package com.applause.test.keycloak.client

import java.net.URI

import com.google.api.client.auth.oauth2.Credential.Builder
import com.google.api.client.auth.oauth2._
import com.google.api.client.http._
import com.google.api.client.json.jackson2._

import scala.util.Try

/**
  * Represents an OpenID Connect client.
  */
object OIDCClient {
  /** A fake URL used to created HTTP */
  private val dummyUrl = new GenericUrl("http://some.fake.url/for/obtaining/access/tokens")

  /**
    * An OIDCClient that uses the Google Credential class for handling OAuth2 client credential token request
    * @param credential the Google Credential instance
    */
  private class CredentialOIDCClient(
    val credential: Credential
  ) extends OIDCClient {
    val httpRequestFactory = credential.getTransport.createRequestFactory(credential)

    override def withAccessToken[A](f: (String) => A): Try[A] = {
      // Bit of a hack, but allows us to (a) rely on refresh token logic in the credential instance,
      // while (b) not tying all other operations to the Google HttpClient API to obtain an access
      // token.
      val request = httpRequestFactory.buildGetRequest(dummyUrl)
      credential.intercept(request)
      Try(request.getHeaders.getAuthorization)
        .map { _ =>
          f(credential.getAccessToken)
        }.recover {
          case _ => throw new NoAccessTokenException()
      }
    }
  }

  /**
    * Creata a client using the provided cedentials for the OAuth2 client credentials grant flow.
    * @param baseURI The base URI of the OIDC compatible server
    * @param tokenPath the path of the server's OAuth2 token endpoint
    * @param transport the HTTP transport to use in commincations
    * @param creds the OIDC client credentials
    * @return
    */
  def forClientCredentials(
    baseURI: URI,
    tokenPath: String,
    transport: HttpTransport,
    creds: OIDCClientCredentials
  ): OIDCClient = {
    val jsonFactory = new JacksonFactory()
    val tokenServerUrl = new GenericUrl(baseURI.resolve(tokenPath))
    val authentication = new ClientParametersAuthentication(creds.clientId, creds.clientSecret)

    // Obtain an initial access and refresh token.
    val initialTokenResponse =
        new ClientCredentialsTokenRequest(transport, jsonFactory, tokenServerUrl)
          .setClientAuthentication(authentication)
          .execute()

    // Create the credentials handler.
    val credential =
      new Builder(BearerToken.authorizationHeaderAccessMethod())
        .setTransport(transport)
        .setJsonFactory(jsonFactory)
        .setTokenServerUrl(tokenServerUrl)
        .setClientAuthentication(authentication)
        .build()
        .setFromTokenResponse(initialTokenResponse)

    new CredentialOIDCClient(credential)
  }
}

/**
  * Represents an OpenID Connect client.
  */
trait OIDCClient {
  def withAccessToken[A](f: (String) => A): Try[A]
  def httpRequestFactory: HttpRequestFactory
}

/**
  * Thrown if an access token can not be obtained.
  */
case class NoAccessTokenException() extends Exception()
