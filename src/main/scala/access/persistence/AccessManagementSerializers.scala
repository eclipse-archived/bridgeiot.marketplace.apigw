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

import java.io.NotSerializableException
import akka.serialization.SerializerWithStringManifest

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import microservice._

import access.api.client.ClientEvent
import access.api.user.UserEvent

class UserEventSerializer extends SerializerWithStringManifest {
  val EventManifest = "UserEvent"

  override def identifier = 400
  override def manifest(o: AnyRef) = EventManifest

  override def toBinary(o: AnyRef) = o match {
    case ev: UserEvent => ev.asJson.noSpaces.getBytes(UTF_8)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String) = manifest match {
    case EventManifest => parse(new String(bytes, UTF_8)) match {
      case Right(json) => Decoder[UserEvent].decodeJson(json) match {
        case Right(event) => event
        case Left(err) => throw new NotSerializableException(s"Couldn't decode: $err")
      }
      case Left(err) => throw new NotSerializableException(s"Couldn't parse: $err")
    }
    case _ => throw new NotSerializableException(s"Manifest $manifest unknown")
  }
}

class ClientEventSerializer extends SerializerWithStringManifest {
  val EventManifest = "ClientEvent"

  override def identifier = 401
  override def manifest(o: AnyRef) = EventManifest

  override def toBinary(o: AnyRef) = o match {
    case ev: ClientEvent => ev.asJson.noSpaces.getBytes(UTF_8)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String) = manifest match {
    case EventManifest => parse(new String(bytes, UTF_8)) match {
      case Right(json) => Decoder[ClientEvent].decodeJson(json) match {
        case Right(event) => event
        case Left(err) => throw new NotSerializableException(s"Couldn't decode: $err")
      }
      case Left(err) => throw new NotSerializableException(s"Couldn't parse: $err")
    }
    case _ => throw new NotSerializableException(s"Manifest $manifest unknown")
  }
}

