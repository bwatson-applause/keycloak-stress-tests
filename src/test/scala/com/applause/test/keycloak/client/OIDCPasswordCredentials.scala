package com.applause.test.keycloak.client

/**
  * Represents the credentials for an OpenID Connect resource owner.
  */
case class OIDCPasswordCredentials(
  username: String,
  password: String,
  clientId: String,
  clientSecret: Option[String] = None
)
