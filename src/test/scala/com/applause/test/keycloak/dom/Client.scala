package com.applause.test.keycloak.dom

import java.util.UUID

import play.api.libs.json.{JsPath, Reads, Json, Writes}

/**
  * Represents the combination of a client ID and secret.
  */
object Client {
  def apply(c: ClientId, s: ClientSecret): Client = Client(c.id, c.clientId, s.secret)
}

/**
  * Represents the combination of a client ID and secret.
  * @param id the Keycloak ID of the client
  * @param clientId the OAuth2 client ID
  * @param clientSecret the OAuth2 client secret
  */
case class Client(
  id: UUID,
  clientId: String,
  clientSecret: String
)

/**
  * Represents an OAuth2 client ID in Keycloak.
  */
object ClientId {

  import play.api.libs.functional.syntax._

  /**
    * Allows for unpacking from traits in pattern matching
    *
    * @param c the client trait implementation
    * @return the unpacked values
    */
  def unapply(c: ClientId): Option[(UUID, String)] = Some(( c.id, c.clientId ))

  /**
    * Writes the client creation data as it's Keycloak JSON representation.
    * The JSON represents a confidential (i.e., secret required) OIDC client
    * that only supports the resource owner grant type and the client
    * credentials grant type.
    */
  implicit val ClientCreationWrites = new Writes[ClientCreationData] {
    def writes(client: ClientCreationData) = Json.obj(
      "enabled" -> true,
      "attributes" -> Json.obj(),
      "redirectUris" -> Json.arr(),
      "clientId" -> client.clientId,
      "protocol" -> "openid-connect",
      "publicClient" -> false,
      "bearerOnly" -> false,
      "standardFlowEnabled" -> false,
      "implicitFlowEnabled" -> false,
      "directAccessGrantsEnabled" -> true,
      "serviceAccountsEnabled" -> true
    )
  }

  /**
    * Reads the JSON representation of a client.
    */
  implicit val ClientIdReads: Reads[ClientId] = (
    (JsPath \ "id").read[UUID] and
    (JsPath \ "clientId").read[String]
  )(ClientIdImpl.apply _)
}

/**
  * Represents a Keycloak client.
  */
trait ClientId {
  /** The Keycloak id for the client */
  val id: UUID

  /** The OAuth2 ID of the client */
  val clientId: String
}

/**
  * Represents an OAuth2 client ID in Keycloak.
  * @param id the Keycloak id for the client
  * @param clientId the OAuth2 ID of the client
  */
case class ClientIdImpl(
  id: UUID,
  clientId: String
) extends ClientId

/**
  * Represents the data needed to create a client.
  * @param clientId the OAuth2 ID of the client
  */
case class ClientCreationData(
  clientId: String
)

/**
  * Represents a client secret.
  */
object ClientSecret {

  /**
    * Reads the JSON representation of a client secret.
    */
  implicit val ClientSecretReads: Reads[ClientSecret] =
    (JsPath \ "value").read[String].map{ s => ClientSecret(s) }
}

/**
  * Represents a client secret.
  * @param secret the secret string
  */
case class ClientSecret(
  secret: String
)
