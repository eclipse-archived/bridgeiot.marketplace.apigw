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

import access.api.client.{ClientEvent, ClientId}
import access.repo.ClientRepo

case class ClientView(clientRepo: ClientRepo)(implicit system: ActorSystem, mat: ActorMaterializer) extends GraphStage[FlowShape[EventEnvelope2, CompletedRequest]] {
  val in = Inlet[EventEnvelope2]("ClientView.in")
  val out = Outlet[CompletedRequest]("ClientView.out")
  val shape = FlowShape.of(in, out)

  val startupTime = DateTime.now.clicks
  var recovering = true

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      setHandler(shape.in, new InHandler {
        override def onPush() = {
          val envelope = grab(shape.in)
          envelope.event match {
            case event: ClientEvent =>
              if (event.meta.time >= startupTime && recovering) {
                recovering = false
                log.info("Finished recovery")
              }
              val result = clientRepo.update(event)
              if (recovering)
                pull(shape.in)
              else result.fold({ err =>
                log.error(err.getMessage)
                pull(shape.in)
              }, client =>
                emit(shape.out, CompletedRequest(event.meta.requestId, Success(client))))
            case _ =>
              log.error(s"Wrong event in envelope: $envelope")
              pull(shape.in)
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

