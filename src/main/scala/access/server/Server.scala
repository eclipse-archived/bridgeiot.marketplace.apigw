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

import scala.util.Properties.envOrElse
import akka.actor.ActorSystem
import akka.event.slf4j.Logger
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}

import io.circe.generic.auto._
import microservice._
import org.slf4j.LoggerFactory

import access.service._

object Server extends App {
  val log = LoggerFactory.getLogger(this.getClass)

  val decider: Supervision.Decider = { e =>
    log.error(s"Unhandled exception in stream: $e")
    log.error("!!! Restarting stream now !!!")
    Supervision.Restart
  }

  implicit val system = ActorSystem("Marketplace")
  val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer = ActorMaterializer(materializerSettings)

  CommandProcessorStage(UserService)
  CommandProcessorStage(ClientService)

  val route = ApiGateway()

  val port = sys.props.get("http.port").fold(envOrElse("MP_LISTEN_PORT", "8080").toInt)(_.toInt)
  val addr = envOrElse("MP_LISTEN_ADDRESS", "0.0.0.0")

  Http().bindAndHandle(route, addr, port)
  Logger("Server").info(s"ApiGateway online at http://$addr:$port")
}
