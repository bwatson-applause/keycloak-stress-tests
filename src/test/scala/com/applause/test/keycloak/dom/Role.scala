package com.applause.test.keycloak.dom

import java.util.UUID

import play.api.libs.json.{Reads, JsPath, Json, Writes}

/**
  * Represents a role in the Keycloak system.
  */
object Role {
  import play.api.libs.functional.syntax._

  /**
    * Writes the role creation data to it's JSON representation.
    */
  implicit val RoleCreationWrites = new Writes[RoleCreationData] {
    def writes(role: RoleCreationData) = Json.obj(
      "name" -> role.name
    )
  }

  /**
    * Writes the role to it's JSON representation.
    */
  implicit val RoleWrites = new Writes[Role] {
    def writes(role: Role) = Json.obj(
      "id" -> role.id,
      "name" -> role.name,
      "scopeParamRequired" -> role.scopeParamRequired,
      "composite" -> role.composite
    )
  }

  /**
    * Reads a role from it's JSON representation.
    */
  implicit val RoleReads: Reads[Role] = (
    (JsPath \ "id").read[UUID] and
    (JsPath \ "name").read[String] and
    (JsPath \ "scopeParamRequired").read[Boolean] and
    (JsPath \ "composite").read[Boolean]
  )(Role.apply _)
}

/**
  * Represents a role in the Keycloak system.
  * @param id the Keycloak ID of the role
  * @param name the name of the role
  * @param scopeParamRequired whether or not the role requires a scope param to be granted
  * @param composite whether or not the role is composite
  */
case class Role(
  id: UUID,
  name: String,
  scopeParamRequired: Boolean,
  composite: Boolean
)

/**
  * Represents the data needed to create a role.
  * @param name the role name
  */
case class RoleCreationData(
  name: String
)
