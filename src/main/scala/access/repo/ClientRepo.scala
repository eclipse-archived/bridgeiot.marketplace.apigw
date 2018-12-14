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

import scala.util.{Success, Try}

import microservice.entity.{DeletedEntity, DeletedId, Entity}
import microservice.persistence.{InMemoryRepo, ReadableRepo}

import access.api.client._

trait ReadableClientRepo extends ReadableRepo[Client, ClientId]

class ClientRepo extends InMemoryRepo[Client, ClientId] with ReadableClientRepo {

  def update(event: ClientEvent): Try[Entity] = event match {
    case ClientCreated(id, organizationId, secret, _) =>
      val client = Client(id, organizationId, secret)
      save(client)
      Success(client)
    case ClientDeleted(id, _) =>
      deleteById(id)
      Success(DeletedEntity(DeletedId(id.value)))
    case AccessTokenCreated(id, accessToken, _) =>
      find(id) map { client =>
        val updatedClient = client.copy(accessToken = Some(accessToken))
        save(updatedClient)
        updatedClient
      }
  }
}
