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

import access.api.client
import access.api.client.{ClientCommand, ClientId, CreateClient, DeleteClient}
import access.model.Client
import access.server.Server.{materializer, system}
import exchange.api.consumer.{ConsumerCreated, ConsumerDeleted, ConsumerEvent}
import exchange.api.provider.{ProviderCreated, ProviderDeleted, ProviderEvent}
import exchange.api.{consumer, provider}

case object ClientService extends AkkaServiceBackend(client.serviceName, Client.behavior) {
  override def eventAdapter = Some(EventAdapterStage(client.serviceName, List(
    eventTopic[ProviderEvent](provider.serviceName),
    eventTopic[ConsumerEvent](consumer.serviceName)
  ), cmdTopic[ClientCommand](client.serviceName), {

    case ProviderCreated(id, organizationId, _, secret, meta) =>
      CreateClient(ClientId.fromId(id), organizationId, secret, meta)
    case ProviderDeleted(id, organizationId, meta) =>
      DeleteClient(ClientId.fromId(id), meta)

    case ConsumerCreated(id, organizationId, _, secret, meta) =>
      CreateClient(ClientId.fromId(id), organizationId, secret, meta)
    case ConsumerDeleted(id, organizationId, meta) =>
      DeleteClient(ClientId.fromId(id), meta)
  }))
}

