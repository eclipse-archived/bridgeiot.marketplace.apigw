/**
 * Copyright (c) 2016-2017 Atos IT Solutions and Services GmbH
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package access.service

import io.circe.generic.auto._
import microservice._

import access.api.user
import access.api.user.{AssignOrganizationToUser, UserCommand}
import access.model.User
import access.server.Server.{materializer, system}
import exchange.api.organization
import exchange.api.organization.{OrganizationCreated, OrganizationEvent}

case object UserService extends AkkaServiceBackend(user.serviceName, User.behavior) {
  override def eventAdapter = Some(EventAdapterStage(user.serviceName, List(
    eventTopic[OrganizationEvent](organization.serviceName)
  ), cmdTopic[UserCommand](user.serviceName), {

    case OrganizationCreated(id, _, meta) =>
      meta.requesterId.map(AssignOrganizationToUser(_, id, meta)).toList
  }))
}

