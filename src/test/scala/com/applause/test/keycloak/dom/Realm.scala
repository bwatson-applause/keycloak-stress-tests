package com.applause.test.keycloak.dom

import play.api.libs.json.{JsPath, Json, Reads, Writes}

import scala.concurrent.duration._

/**
  * Represents a realm in the Keycloak system.
  */
object Realm {
  import play.api.libs.functional.syntax._

  /** The realm session length */
  val sessionLength = (2 days).toSeconds

  /**
    * Writes the realm creation data to it's JSON representation.
    * All session lengths are set to 'sessionLength'.
    */
  implicit val RealmCreationWrites = new Writes[RealmCreationData] {
    def writes(realm: RealmCreationData) = Json.obj(
      "enabled" -> true,
      "id" -> realm.id,
      "realm" -> realm.name,
      "accessTokenLifespan" -> sessionLength,
      "accessTokenLifespanForImplicitFlow" -> sessionLength,
      "ssoSessionIdleTimeout" -> sessionLength,
      "ssoSessionMaxLifespan" -> sessionLength,
      "offlineSessionIdleTimeout" -> sessionLength
    )
  }

  /**
    * Reads a JSON representation of a realm.
    */
  implicit val RealmReads: Reads[Realm] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "realm").read[String]
  )(Realm.apply _)
}

/**
  * Represents a realm in the Keycloak system.
  * @param id the ID of the realm in the Keycloak system
  * @param name the name of the realm
  */
case class Realm(
  id: String,
  name: String
)

/**
  * The realm creation data.
  * @param id the ID of the realm
  * @param name the name of the realm
  */
case class RealmCreationData(
  id: String,
  name: String
)
