/**
 * Copyright (c) 2016-2017 Atos IT Solutions and Services GmbH
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package access.impl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.SourceQueue

import io.circe.generic.auto._
import org.slf4j.LoggerFactory
import pdi.jwt._
import microservice._
import microservice.entity.{Entity, Id, normalize}
import microservice.security._

import exchange.api.organization.OrganizationId
import access._
import access.api.client._
import access.api.user._
import access.repo.{ReadableClientRepo, ReadableUserRepo}

case class CommandQueues(implicit system: ActorSystem, mat: ActorMaterializer) {
  val user = MessageQueue(cmdTopic[UserCommand](api.user.serviceName), "CommandQueues.user")
  val client = MessageQueue(cmdTopic[ClientCommand](api.client.serviceName), "CommandQueues.client")
}

class AccessManager(userRepo: ReadableUserRepo, clientRepo: ReadableClientRepo, commandQueues: CommandQueues, pending: SourceQueue[PendingRequest]) {
  val logger = LoggerFactory.getLogger(this.getClass)

  private val marketplaceSecret = decodeBase64(sys.env.getOrElse("MARKETPLACE_SECRET", ""))

  private def promise[E](requestId: Id) = {
    val promise = Promise[E]
    pending.offer(PendingRequest(requestId, promise.asInstanceOf[Promise[Entity]]))
    promise.future
  }

  def userForToken(userToken: AccessToken) = for {
    claim <- Future.fromTry(JwtCirce.decode(userToken, marketplaceSecret))
    userIdStr <- claim.subject.map(Future.successful).getOrElse(Future.failed(NoUserIdInAccessToken()))
    userId = UserId.from(userIdStr)
    user <- Future.fromTry(userRepo.find(userId)).recoverWith { case _ =>
      val meta = Meta()
      commandQueues.user.offer(CreateUser(userId, meta))
      promise[User](meta.requestId)
    }
  } yield user

  def clientForToken(clientToken: AccessToken) = Future.fromTry(for {
    claim <- JwtCirce.decode(clientToken, JwtOptions(signature = false))
    clientIdStr <- claim.subject.map(Success(_)).getOrElse(Failure(NoClientIdInAccessToken()))
    clientId = ClientId.from(clientIdStr)
    client <- clientRepo.find(clientId) if JwtCirce.isValid(clientToken, decodeBase64(client.secret))
  } yield client)

  def createToken(clientId: String, clientSecret: String) = {
    val meta = Meta()
    commandQueues.client.offer(CreateAccessToken(ClientId.from(clientId), clientSecret, meta))
    promise[Client](meta.requestId).map(_.accessToken.getOrElse(""))
  }

  def client(clientId: Id) =
    clientRepo.find(ClientId.from(clientId))
}
