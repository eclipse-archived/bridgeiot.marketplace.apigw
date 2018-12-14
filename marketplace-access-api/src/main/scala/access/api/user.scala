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
import microservice._
import microservice.entity._

import exchange.api.organization.OrganizationId

object user {

  val serviceName = "User"

  case class UserId(value: String) extends AggregateId

  object UserId {
    def from(id: String): UserId = UserId(normalize(id))
    implicit def fromString(aggregateId: String): UserId = UserId(aggregateId)
  }

  case class User(id: UserId, organizationId: Option[OrganizationId] = None) extends Entity

  sealed trait UserCommand extends Command
  sealed trait UserEvent extends Event

  case class CreateUser(id: UserId, meta: Meta = Meta()) extends UserCommand
  case class UserCreated(id: UserId, meta: Meta = Meta()) extends UserEvent

  case class AssignOrganizationToUser(id: UserId, organizationId: OrganizationId, meta: Meta = Meta()) extends UserCommand
  case class OrganizationAssignedToUser(id: UserId, organizationId: OrganizationId, meta: Meta = Meta()) extends UserEvent

  // Errors
  case class NoUserIdInAccessToken() extends Error("", "NoUserIdInAccessToken", meta = Meta())
  case class CannotReassignOrganization(override val id: Id, override val error: String, override val meta: Meta) extends Error(id, "CannotReassignOrganization", error, meta = meta)
}
