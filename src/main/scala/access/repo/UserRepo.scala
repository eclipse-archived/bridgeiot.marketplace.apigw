/**
 * Copyright (c) 2016-2017 Atos IT Solutions and Services GmbH
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package access.repo

import microservice.Event
import microservice.persistence.{InMemoryRepo, ReadableRepo}

import access.api.user._

trait ReadableUserRepo extends ReadableRepo[User, UserId]

class UserRepo extends InMemoryRepo[User, UserId] with ReadableUserRepo {

  def update(event: UserEvent) = event match {
    case UserCreated(id, _) =>
      save(User(id))
    case OrganizationAssignedToUser(userId, organizationId, _) =>
      find(userId) foreach { user =>
        save(user.copy(organizationId = Some(organizationId)))
      }
  }
}
