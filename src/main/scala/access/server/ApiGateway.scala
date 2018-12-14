/**
 * Copyright (c) 2016-2017 Atos IT Solutions and Services GmbH
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package access.server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Properties.envOrElse
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}

import org.slf4j.LoggerFactory
import microservice.entity.Id
import microservice.persistence.CassandraEventSource
import microservice._
import sangria.marshalling.circe._

import access._
import access.impl.{AccessManager, ClientView, CommandQueues, UserView}
import access.repo.{ClientRepo, UserRepo}

object ApiGateway extends CORSHandler {
  val userRepo = new UserRepo
  val clientRepo = new ClientRepo

  val log = LoggerFactory.getLogger(this.getClass)

  case class Requester(id: Option[Id] = None, organizationId: Option[Id] = None)

  def apply()(implicit system: ActorSystem, mat: ActorMaterializer) = {
    val pendingRequestQueue = RunnableGraph.fromGraph(GraphDSL.create(SourceQueue[PendingRequest]) { implicit b =>
      addQueue =>
        val userEvents = b.add(CassandraEventSource(model.User.tag))
        val clientEvents = b.add(CassandraEventSource(model.Client.tag))
        val errors = b.add(TopicSource(errorTopic, "ApiGateway.PendingRequestHandler" + envSuffix))

        val userView = b.add(UserView(userRepo))
        val clientView = b.add(ClientView(clientRepo))
        val pending = b.add(PendingRequestHandler("ApiGateway" + envSuffix))
        val merge = b.add(Merge[CompletedRequest](2))

        addQueue ~> pending.add
        userEvents ~> userView ~> merge.in(0)
        clientEvents ~> clientView ~> merge.in(1)
        merge.out ~> pending.complete
        errors ~> pending.error
        pending.out ~> Sink.ignore

        ClosedShape
    }).run()

    val accessManager = new AccessManager(userRepo, clientRepo, CommandQueues(), pendingRequestQueue)

    def accessTokenRoute = (get & path("accessToken") & parameters('clientId, 'clientSecret)) { (clientId, clientSecret) =>
      log.info(s"requested accessToken for client $clientId")
      complete(accessManager.createToken(clientId, clientSecret).map(OK -> _).recover {
        case err: Error => Unauthorized -> err.getMessage
        case _ => Unauthorized -> "Unauthorized"
      })
    }

    def clientSecretRoute = (get & path("clientSecret") & parameter('clientId)) { clientId =>
      log.info(s"requested clientSecret for client $clientId")
      authenticateOAuth2Async("BIG IoT", userAuthenticator) { user =>
        context =>
          log.info(s"authenticated as user $user")
          accessManager.client(clientId).fold({ error =>
            log.info(s"client $clientId not found: ${error.getMessage}")
            context.complete(NotFound)
          }, { client =>
            if (user.organizationId.isDefined && (client.organizationId == user.organizationId.get))
              context.complete(client.secret)
            else {
              log.info(s"user $user not authorized to access secret for client $client")
              context.complete(Unauthorized)
            }
          })
      }
    }

    def userAuthenticator(credentials: Credentials) = credentials match {
      case p@Credentials.Provided(token) =>
        accessManager.userForToken(token).map(Some(_))
      case _ =>
        Future.successful(None)
    }

    def userOrClientAuthenticator(credentials: Credentials): Future[Option[Requester]] = credentials match {
      case p@Credentials.Provided(token) =>
        accessManager.userForToken(token).map { user =>
          Some(Requester(Some(user.id.value), user.organizationId.map(_.value)))
        } recoverWith { case _ =>
          accessManager.clientForToken(token) map { client =>
            Some(Requester(Some(client.id.value), Some(client.organizationId.value)))
          } recover { case _ =>
            Some(Requester())
          }
        }

      case _ =>
        Future.successful(Some(Requester()))
    }

    val exchangeAddress = envOrElse("EXCHANGE_HOST", "localhost")
    val exchangePort = envOrElse("EXCHANGE_PORT", "8080").toInt

    def protectedRoute = path("graphql") {
      authenticateOAuth2Async("BIG IoT", userOrClientAuthenticator) {
        requester =>
          context =>
            log.info(s"GraphQL request to $exchangeAddress:$exchangePort by $requester")
            val headers = context.request.headers.filterNot { header => List("requesterId", "organizationId", "Timeout-Access").contains(header.name) } ++ List(
              RawHeader("requesterId", requester.id getOrElse ""),
              RawHeader("organizationId", requester.organizationId getOrElse ""))
            val exchangeRequest = context.request.withHeaders(headers)

            Source.single(exchangeRequest)
              .via(exchangeConn)
              .runWith(Sink.head)
              .flatMap(context.complete(_))
      }
    }

    def schemaRoute = (get & path("schema")) {
      context =>
        log.info(s"forwarding schema request ${context.request}")
        Source.single(context.request)
          .via(exchangeConn)
          .runWith(Sink.head)
          .flatMap(context.complete(_))
    }

    def exchangeConn = {
      Http(system).outgoingConnection(exchangeAddress, exchangePort)
    }

    corsHandler {
      schemaRoute ~ accessTokenRoute ~ clientSecretRoute ~ protectedRoute
    }
  }
}
