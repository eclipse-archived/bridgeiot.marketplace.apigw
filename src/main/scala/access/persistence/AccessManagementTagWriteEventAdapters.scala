/**
 * Copyright (c) 2016-2017 Atos IT Solutions and Services GmbH
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package access.persistence

import microservice.persistence.TagWriteEventAdapter

import access.api.client.ClientEvent
import access.api.user.UserEvent
import access.model.{Client, User}

class UserTagWriteEventAdapter extends TagWriteEventAdapter[UserEvent](User.tag)
class ClientTagWriteEventAdapter extends TagWriteEventAdapter[ClientEvent](Client.tag)
