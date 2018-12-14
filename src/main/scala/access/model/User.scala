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

import io.funcqrs.Tags
import io.funcqrs.behavior.handlers.just.OneEvent
import io.funcqrs.behavior.{Behavior, Types}
import microservice._

import access.api.user._
import exchange.api.organization.OrganizationId

object User extends Types[User] {
  type Id = UserId
  type Command = UserCommand
  type Event = UserEvent

  val tag = Tags.aggregateTag("User")

  def create = {
    actions
      .commandHandler {
        OneEvent {
          case CreateUser(id, meta) =>
            UserCreated(id, meta)
        }
      }
      .eventHandler {
        case UserCreated(id, _) =>
          RestrictedUser(id)
      }
  }

  def behavior(id: UserId): Behavior[User, Command, Event] =
    Behavior
      .first {
        create
      }
      .andThen {
        case user: RestrictedUser => user.acceptCommands
        case user: FullUser => user.acceptCommands
      }
}

sealed trait User {
  def id: UserId
}

case class RestrictedUser(id: UserId) extends User {
  def acceptCommands =
    User.actions
      .commandHandler {
        OneEvent {
          case AssignOrganizationToUser(_, organizationId, meta) =>
            OrganizationAssignedToUser(id, organizationId, meta)
        }
      }
      .eventHandler {
        case OrganizationAssignedToUser(_, organizationId, _) =>
          FullUser(id, organizationId)
      }
}

case class FullUser(id: UserId, organizationId: OrganizationId) extends User {
  def acceptCommands =
    User.actions
      .rejectCommand {
        case AssignOrganizationToUser(_, newOrganizationId, meta) =>
          CannotReassignOrganization(id, s"Cannot reassign Organization $newOrganizationId. Already assigned to $organizationId", meta)
      }
}

