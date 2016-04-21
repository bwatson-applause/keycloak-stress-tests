package com.applause.test.keycloak.dom

import java.util.UUID

import play.api.libs.json.{JsPath, Reads, Json, Writes}

/**
  * Represents a user in the Keycloak system.
  */
object User {
  import play.api.libs.functional.syntax._

  /**
    * Writes the user creation data as it's JSON representation.
    */
  implicit val UserCreationWrites = new Writes[UserCreationData] {
    def writes(user: UserCreationData) = Json.obj(
      "enabled" -> true,
      "emailVerified" -> true,
      "firstName" -> user.firstName,
      "lastName" -> user.lastName,
      "username" -> user.username,
      "email" -> user.email
    )
  }

  /**
    * Reads the user data from it's JSON representation.
    */
  implicit val UserReads: Reads[User] = (
    (JsPath \ "id").read[UUID] and
    (JsPath \ "username").read[String] and
    (JsPath \ "email").read[String]
  )(User.apply _)
}

/**
  * Represents a user in the Keycloak system.
  * @param id the ID of the user in the Keycloak system
  * @param username the username of the user
  * @param email the email address of the user
  */
case class User(
  id: UUID,
  username: String,
  email: String
)

/**
  * Represents the user creation data.
  * @param firstName the first name for the user
  * @param lastName the last name for the user
  * @param username the username for the user
  * @param email the email address for the user
  * @param password the password creation data for the user
  * @param roles the roles for the user
  */
case class UserCreationData(
  firstName: String,
  lastName: String,
  username: String,
  email: String,
  password: UserPasswordCreationData,
  roles: Seq[Role]
)
