package com.applause.test.keycloak.client

/**
  * Represents the credentials for an OpenID Connect client.
  */
case class OIDCClientCredentials(
  clientId: String,
  clientSecret: String
)
