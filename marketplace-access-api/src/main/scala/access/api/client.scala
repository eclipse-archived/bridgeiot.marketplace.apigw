/**
 * Copyright (c) 2016-2017 Atos IT Solutions and Services GmbH
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package access.api

import io.funcqrs.AggregateId
import microservice.entity.{Entity, Id}
import microservice.{Command, Error, Event, Meta}

import exchange.api.organization.OrganizationId

object client {

  val serviceName = "Client"
  def prefix = serviceName + "_"

  val defaultTokenExpiryPeriod = 60 * 60 * 24 * 7 // 7 days

  type AccessToken = String

  case class ClientId(value: String) extends AggregateId {
    def withoutPrefix = value.replace(prefix, "")
  }

  object ClientId {
    def fromId(id: AggregateId) = ClientId(prefix + id.value)
    def from(id: String) = ClientId(prefix + id)
    implicit def fromString(str: String): ClientId = ClientId(str)
  }

  case class Client(id: ClientId, organizationId: OrganizationId, secret: String, accessToken: Option[AccessToken] = None) extends Entity {
    override def toString = s"Client($id,$organizationId)"
  }

  sealed trait ClientCommand extends Command
  sealed trait ClientEvent extends Event

  case class CreateClient(id: ClientId, organizationId: OrganizationId, secret: String, meta: Meta = Meta()) extends ClientCommand
  case class ClientCreated(id: ClientId, organizationId: OrganizationId, secret: String, meta: Meta = Meta()) extends ClientEvent

  case class DeleteClient(id: ClientId, meta: Meta = Meta()) extends ClientCommand
  case class ClientDeleted(id: ClientId, meta: Meta = Meta()) extends ClientEvent

  case class CreateAccessToken(id: ClientId, secret: String, meta: Meta = Meta()) extends ClientCommand
  case class AccessTokenCreated(id: ClientId, accessToken: AccessToken, meta: Meta = Meta()) extends ClientEvent

  // Errors
  case class NoClientIdInAccessToken() extends Error("", "NoClientIdInAccessToken", meta = Meta())
  case class WrongClientSecret(override val id: Id, override val meta: Meta) extends Error(id, "WrongClientSecret", id, meta)
  case class ClientExists(override val id: Id, override val meta: Meta) extends Error(id, "ClientExists", id, meta)
  case class ClientDoesNotExist(override val id: Id, override val meta: Meta) extends Error(id, "ClientDoesNotExist", id, meta)
}
