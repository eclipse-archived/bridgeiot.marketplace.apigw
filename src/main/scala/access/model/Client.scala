/**
 * Copyright (c) 2016-2017 Atos IT Solutions and Services GmbH
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package access.model

import java.time.Instant

import io.funcqrs.Tags
import io.funcqrs.behavior.handlers.just.OneEvent
import io.funcqrs.behavior.{Behavior, Types}
import pdi.jwt.{JwtCirce, JwtClaim}
import microservice._
import microservice.security._

import access.api.client._
import exchange.api.organization.OrganizationId

object Client extends Types[Client] {
  type Id = ClientId
  type Command = ClientCommand
  type Event = ClientEvent

  val tag = Tags.aggregateTag("Client")

  def create = {
    actions
      .rejectCommand {
        case cmd: CreateClient if cmd.hasNoOrganization =>
          NotAuthorized(cmd)
        case CreateAccessToken(id, _, meta) =>
          ClientDoesNotExist(id.withoutPrefix, meta)
      }
      .commandHandler {
        OneEvent {
          case CreateClient(id, organizationId, secret, meta) =>
            ClientCreated(id, organizationId, secret, meta)
        }
      }
      .eventHandler {
        case ClientCreated(id, organizationId, secret, _) =>
          ActiveClient(id, organizationId, secret)
      }
  }

  def behavior(id: ClientId) =
    Behavior
      .first {
        create
      }
      .andThen {
        case client: Client => client.acceptCommands
      }
}

sealed trait Client extends Aggregate[Client, Client.Command, Client.Event]

case class ActiveClient(id: ClientId, organizationId: OrganizationId, secret: String) extends Client {
  def acceptCommands =
    Client.actions
      .rejectCommand {
        case CreateClient(_, _, _, meta) =>
          ClientExists(id.withoutPrefix, meta)
      }
      .rejectCommand {
        case CreateAccessToken(_, givenSecret, meta) if givenSecret != secret =>
          WrongClientSecret(id.withoutPrefix, meta)
      }
      .commandHandler {
        OneEvent {
          case DeleteClient(_, meta) =>
            ClientDeleted(id, meta)
          case CreateAccessToken(_, _, meta) =>
            val now = Instant.now
            val claim = JwtClaim()
              .about(id.withoutPrefix)
              .expiresIn(defaultTokenExpiryPeriod)
              .issuedNow
            val token = JwtCirce.encode(claim, decodeBase64(secret), algorithm)
            AccessTokenCreated(id, token, meta)
        }
      }
      .eventHandler {
        case ClientDeleted(_, _) =>
          DeletedClient()
        case AccessTokenCreated(_, _, _) =>
          this
      }
}

case class DeletedClient() extends Client {
  def acceptCommands = Client.create
}
