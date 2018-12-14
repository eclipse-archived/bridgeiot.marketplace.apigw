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

import scala.util.Success
import akka.actor.ActorSystem
import akka.http.scaladsl.model.DateTime
import akka.persistence.query.EventEnvelope2
import akka.stream._
import akka.stream.stage._

import microservice._

import access.api.user.{UserEvent, UserId}
import access.repo.UserRepo

case class UserView(userRepo: UserRepo)(implicit system: ActorSystem, mat: ActorMaterializer) extends GraphStage[FlowShape[EventEnvelope2, CompletedRequest]] {
  val in = Inlet[EventEnvelope2]("UserView.in")
  val out = Outlet[CompletedRequest]("UserView.out")
  val shape = FlowShape.of(in, out)

  val startupTime = DateTime.now.clicks
  var recovering = true

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      setHandler(shape.in, new InHandler {
        override def onPush() = {
          val envelope = grab(shape.in)
          envelope.event match {
            case event: UserEvent =>
              if (event.meta.time >= startupTime && recovering) {
                recovering = false
                log.info("Finished recovery")
              }
              userRepo.update(event)
              if (recovering)
                pull(shape.in)
              else userRepo.find(event.id.asInstanceOf[UserId]).fold({ err =>
                log.error(err.getMessage)
                pull(shape.in)
              }, user =>
                emit(shape.out, CompletedRequest(event.meta.requestId, Success(user))))
            case _ =>
              log.error(s"Wrong event in envelope: $envelope")
          }
        }
      })
      setHandler(shape.out, new OutHandler {
        override def onPull() = {
          pull(shape.in)
        }
      })
    }
}

