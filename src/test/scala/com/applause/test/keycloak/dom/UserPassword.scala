package com.applause.test.keycloak.dom

import play.api.libs.json.{Json, Writes}

/**
  * Represents a user password in the Keycloak system. These can not be read, only written.
  */
object UserPassword {
  /**
    * Writes a password to it's JSON representation.
    */
  implicit val UserPasswordCreationWrites = new Writes[UserPasswordCreationData] {
    def writes(pw: UserPasswordCreationData) = Json.obj(
      "type" -> "password",
      "temporary" -> false,
      "value" -> pw.password
    )
  }
}

/**
  * Represents the data needed to create a user password.
  * @param password the user password
  */
case class UserPasswordCreationData(
  password: String
)
