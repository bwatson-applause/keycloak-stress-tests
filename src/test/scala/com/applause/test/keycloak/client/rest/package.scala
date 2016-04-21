package com.applause.test.keycloak.client

import java.net.URI
import java.nio.charset.Charset
import java.util.UUID

import com.applause.test.keycloak.client.rest.RestApiPaths._
import com.applause.test.keycloak.dom._
import com.google.api.client.http._
import org.apache.commons.io.IOUtils
import play.api.libs.json.{JsResult, Json, JsValue}

import scala.util.Try

import scalaz._

/**
  * This package is mostly used as a namespace for a variety of Keycloak REST functions.
  */
package object rest {
  import Realm._
  import ClientId._
  import User._
  import Role._
  import UserPassword._

  private val utf8Charset = Charset.forName("UTF-8")
  private val acceptAllHeaderValue = "*/*"
  private val jsonContentTypeHeaderValue = "application/json;charset=utf-8"

  /**
    * Type of functions used to transform HttpRequests (i.e., add headers, set body content)
    */
  type HttpRequestTransformer = (HttpRequest) => HttpRequest

  /**
    * Resolve a path to a base URI, and convert the path to a Google generic URL.
    *
    * @param path the path to resolve
    * @param baseURI the base URI against which to resolve the path
    * @return the resulting Google GenericUrl
    */
  private def resolvePath(path: String)(implicit baseURI: URI) = new GenericUrl(baseURI.resolve(path))

  /**
    * Convert the JsValue into a Google HTTP request content payload
    *
    * @param data the JsValue to convert
    * @return the resulting Google HTTP request content payload
    */
  private def toHttpContent(data: JsValue) = new ByteArrayContent(
    jsonContentTypeHeaderValue,
    Json.prettyPrint(data).getBytes(utf8Charset)
  )

  /**
    * Identity transformer, user as the default transformer for later functions.
    */
  val httpRequestIdentity: HttpRequestTransformer = { req => req }

  /**
    * Sets common HTTP headers on the request.
    */
  val commonHttpRequestTransformer: HttpRequestTransformer = { req =>
    // Mutability FTL
    req.getHeaders.setAccept(acceptAllHeaderValue)
    req
  }

  /**
    * Creates an HTTP GET request.
    *
    * @param path the of the get request
    * @param f the HTTP request transformer
    * @param factory the implicit request factory
    * @param baseURI the base URI against which to resolve the provided path
    * @return the resulting HTTP request
    */
  def get(
    path: String
  )(
    f: HttpRequestTransformer = httpRequestIdentity
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): HttpRequest =
    (commonHttpRequestTransformer andThen f) (
      factory.buildGetRequest(resolvePath(path))
    )

  /**
    * Creates an HTTP DELETE request.
    *
    * @param path the of the get request
    * @param f the HTTP request transformer
    * @param factory the implicit request factory
    * @param baseURI the base URI against which to resolve the provided path
    * @return the resulting HTTP request
    */
  def delete(
    path: String
  )(
    f: HttpRequestTransformer = httpRequestIdentity
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): HttpRequest =
    (commonHttpRequestTransformer andThen f) (
      factory.buildDeleteRequest(resolvePath(path))
    )

  /**
    * Creates an HTTP POST request.
    *
    * @param path the of the get request
    * @param data the POST data JSON value
    * @param f the HTTP request transformer
    * @param factory the implicit request factory
    * @param baseURI the base URI against which to resolve the provided path
    * @return the resulting HTTP request
    */
  def post(
    path: String,
    data: JsValue
  )(
    f: HttpRequestTransformer = httpRequestIdentity
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): HttpRequest =
    (commonHttpRequestTransformer andThen f) (
      factory.buildPostRequest(resolvePath(path), toHttpContent(data))
    )

  /**
    * Creates an HTTP PUT request.
    *
    * @param path the of the get request
    * @param data the PUT data JSON value
    * @param f the HTTP request transformer
    * @param factory the implicit request factory
    * @param baseURI the base URI against which to resolve the provided path
    * @return the resulting HTTP request
    */
  def put(
    path: String,
    data: JsValue
  )(
    f: HttpRequestTransformer = httpRequestIdentity
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): HttpRequest =
    (commonHttpRequestTransformer andThen f) (
      factory.buildPutRequest(resolvePath(path), toHttpContent(data))
    )

  /** Parent of all HTTP errors. */
  sealed trait ResponseError

  /** Request failure. */
  case class RequestFailure(message: String) extends ResponseError

  /** General client failure. */
  object ClientError extends ResponseError

  /** General server failure. */
  object ServerError extends ResponseError

  /** Failure indicating the action (e.g., create) could not be taken as an entity already exists. */
  object EntityExists extends ResponseError

  /** Failure indicating the action (e.g., get) could not be taken as an entity does not exist. */
  object EntityNotFound extends ResponseError

  /** Failure indicating the response could not be parsed into an object. */
  object ResponseParseError extends ResponseError

  /** Type of HTTP response processors. */
  private type ResponseProcessor[A] = (Option[HttpResponse], Int) => \/[ResponseError, A]

  /** Type of partial HTTP response processors. */
  private type PartialResponseProcessor[A] = PartialFunction[(Option[HttpResponse], Int), \/[ResponseError, A]]

  /**
    * HTTP request execution driver.
    *
    * @param req the request to use in the HTTP request execution
    * @param pf the partial response processor
    * @tparam A the type of the response object
    * @return an Either, indicating that either an error occurred or the request succeeded
    */
  private def executeRequest[A](req: HttpRequest)(pf: PartialResponseProcessor[A]): \/[ResponseError, A] =
    Try(req.execute())
      .map(r => ( Some(r), r.getStatusCode ))
      .recover {
        // Ugh Google... don't throw on a 404, etc... Let me decide how to proceed.
        // I mean, really? I can't access the headers, etc... of a 404 response?
        // You're THAT much smarter than me that you know I don't need that info?
        // That is SOOOOOOO Google of you :|
        case hre: HttpResponseException if hre.getStatusCode > 0 => ( None, hre.getStatusCode )
      } map { case tuple @ (rOpt, statusCode) =>
        val result = (pf orElse responseCatchAll[A])(tuple)
        Try(rOpt.foreach(_.disconnect()))
        result
      } recover {
        case t: Throwable => -\/(RequestFailure(t.getMessage))
      } get

  /**
    * Catch-all for response processing. Converts the response into a server or client error.
    *
    * @tparam A the type of the expected response, needed for proper partial chaining
    * @return the proper error type
    */
  private def responseCatchAll[A]: PartialResponseProcessor[A] = {
    case (rOpt, s) if s / 100 == 4 => -\/(ClientError)
    case _ => -\/(ServerError)
  }

  /**
    * Process a general request.
    *
    * @param req the general request
    * @return either the response error or Unit in the case of a 2XX response
    */
  def processRequest(req: HttpRequest): \/[ResponseError, Unit] =
    executeRequest[Unit](req) {
      case (rOpt, s) if s / 100 == 2 => \/-()
    }

  /**
    * Process a create (POST or PUT) request
    *
    * @param req the POST or PUT request
    * @return either the response error or Unit in the case of a 201 response
    */
  def processCreateRequest(req: HttpRequest): \/[ResponseError, Unit] =
    executeRequest[Unit](req) {
      case (rOpt, s) if s == 201 => \/-()
      case (rOpt, s) if s == 409 => -\/(EntityExists)
    }

  /**
    * Process a DELETE request
    *
    * @param req the DELETE request
    * @return either the response error, Some(Unit) in the case of a 204 response, or None in the case of a 404
    */
  def processDeleteRequest(req: HttpRequest): \/[ResponseError, Option[Unit]] = {
    executeRequest[Option[Unit]](req) {
      case (rOpt, s) if s == 204 => \/-(Some(()))
      case (rOpt, s) if s == 404 => \/-(None)
    }
  }

  /**
    * Attempts to parse the JSON payload of an HTTP response
    *
    * @param resp the JSON HTTP response
    * @return the attempt of parsing the payload into a JsValue
    */
  private def fromJsonHttpResponse(resp: HttpResponse): Try[JsValue] = {
    Try(Json.parse(IOUtils.toString(resp.getContent, resp.getContentCharset)))
  }

  /**
    * Process a GET request
    *
    * @param req the GET request
    * @return either the response error, Some(JsValue) in the case of a 200 response, or None in the case of a 404
    */
  def processFetchRequest(req: HttpRequest): \/[ResponseError, Option[JsValue]] = {
    executeRequest[Option[JsValue]](req) {
      case (rOpt, s) if s == 200 => rOpt.map(r => {
        fromJsonHttpResponse(r).map(js => \/-(Some(js))).getOrElse(-\/(ResponseParseError))
      }) getOrElse -\/(RequestFailure("Successful fetch request had no response content"))
      case (rOpt, s) if s == 404 => \/-(None)
    }
  }

  /**
    * Convert a JsResult into an either of the expected result.
    *
    * @param f the logic responsible for creating the JsResult
    * @tparam A the type of the JsResult
    * @return an either representing either the error or the parse result.
    */
  def asEither[A](f: => JsResult[A]): \/[ResponseError, A] =
    f.map(a => \/-(a)) recover { case _ => -\/(ResponseParseError) } get

  /**
    * Convert an optional JsResult into an either of an optional.
    *
    * @param f the logic responsible for creating the optional JsResult
    * @tparam A the type of the JsResult
    * @return an either representing either the error or the optional parse result.
    */
  def asOptEither[A](f: => Option[JsResult[A]]): \/[ResponseError, Option[A]] =
    f.map(_.map { a => \/-(Some(a)) } recover { case _ => -\/(ResponseParseError) } get) getOrElse \/-(None)

  /**
    * Fetch a realm from Keycloak.
    *
    * @param realmName the realm name
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the optional realm
    */
  def fetchRealm(
    realmName: String
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Option[Realm]] = {
    val path = realmPath(realmName)

    for {
      realmJsOpt <- processFetchRequest(get(path)())
      realmJsResultOpt <- \/-(realmJsOpt.map(_.validate[Realm]))
      realmOpt <- asOptEither(realmJsResultOpt)
    } yield realmOpt
  }

  /**
    * Create a realm in Keycloak.
    *
    * @param data the realm creation data
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the realm
    */
  def createRealm(
    data: RealmCreationData
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Realm] = {
    val jsonData = Json.toJson(data)

    for {
      // Create the target realm.
      createResp <- processCreateRequest(post(realmsPath, jsonData)())

      // Get the data for the newly created realm.
      realmOpt <- fetchRealm(data.name)
      realm <- realmOpt.map(\/-(_)).getOrElse(-\/(EntityNotFound))
    } yield realm
  }

  /**
    * Fetch a client by client ID (NOT it's keycloak ID) from Keycloak.
    *
    * @param realm the realm of the client
    * @param clientId the client ID
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the optional client
    */
  def fetchClientByClientId(
    realm: Realm,
    clientId: String
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Option[Client]] = {
    val path = clientsPath(realm.name)

    for {
      clientJsOpt <- processFetchRequest(get(path)())
      clientJsResultOpt <- \/-(clientJsOpt.map(_.validate[Seq[ClientId]]))
      clientSeqOpt <- asOptEither(clientJsResultOpt)
      clientSeq <- clientSeqOpt.map(\/-(_)).getOrElse(-\/(EntityNotFound))
      clientOpt <- \/-(clientSeq.collectFirst({ case client @ ClientId(_, id) if clientId == id => client }))
      clientSecretOpt <- clientOpt.map(c => fetchClientSecret(realm, c.id)).getOrElse(\/-(None))
      clientWithSecretOpt <- \/-(( clientOpt, clientSecretOpt ) match {
        case ( Some(c), Some(s)) => Some(Client(c, s))
        case _ => None
      })
    } yield clientWithSecretOpt
  }

  /**
    * Fetch a client secret from Keycloak.
    *
    * @param realm the realm of the client
    * @param clientUUID the client's Keycloak UUID
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the optional client secret
    */
  def fetchClientSecret(
    realm: Realm,
    clientUUID: UUID
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Option[ClientSecret]] = {
    val path = clientSecretPath(realm.name, clientUUID)

    for {
      clientSecretJsOpt <- processFetchRequest(get(path)())
      clientSecretJsResultOpt <- \/-(clientSecretJsOpt.map(_.validate[ClientSecret]))
      clientSecretOpt <- asOptEither(clientSecretJsResultOpt)
    } yield clientSecretOpt
  }

  /**
    * Create a client in Keycloak.
    *
    * @param realm the realm of the client.
    * @param data the client creation data
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the client
    */
  def createClient(
    realm: Realm,
    data: ClientCreationData
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Client] = {
    val path = clientsPath(realm.name)
    val jsonData = Json.toJson(data)

    for {
      // Create the client.
      createResp <- processCreateRequest(post(path, jsonData)())

      // Get the data for the newly created client.
      clientOpt <- fetchClientByClientId(realm, data.clientId)
      client <- clientOpt.map(\/-(_)).getOrElse(-\/(EntityNotFound))
    } yield client
  }

  /**
    * Fetch a role from Keycloak.
    *
    * @param realm the realm of the role
    * @param roleName the name of the role
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the optional role
    */
  def fetchRole(
    realm: Realm,
    roleName: String
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Option[Role]] = {
    val path = roleByNamePath(realm.name, roleName)

    for {
      roleJsOpt <- processFetchRequest(get(path)())
      roleJsResultOpt <- \/-(roleJsOpt.map(_.validate[Role]))
      roleOpt <- asOptEither(roleJsResultOpt)
    } yield roleOpt
  }

  /**
    * Create a role in Keycloak.
    *
    * @param realm the realm of the role
    * @param data the role creation data
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the role
    */
  def createRole(
    realm: Realm,
    data: RoleCreationData
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Role] = {
    val path = rolesPath(realm.name)
    val jsonData = Json.toJson(data)

    for {
      // Create the role.
      createResp <- processCreateRequest(post(path, jsonData)())

      // Get the data for the newly created role.
      roleOpt <- fetchRole(realm, data.name)
      role <- roleOpt.map(\/-(_)).getOrElse(-\/(EntityNotFound))
    } yield role
  }

  /**
    * Assign the provided roles to the given user in Keycloak.
    *
    * @param realm the realm of the user
    * @param user the user to whom to assign roles
    * @param roles the roles to assign to the user
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or simply Unit for success
    */
  def assignRolesToUser(
    realm: Realm,
    user: User,
    roles: Seq[Role]
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Unit] = {
    val path = assignRealmRolesToUserPath(realm.name, user.id)
    val jsonData = Json.toJson(roles)

    processRequest(post(path, jsonData)())
  }

  /**
    * Fetch a user by username from Keycloak.
    *
    * @param realm the realm of the user
    * @param username the username of the user
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the optional user
    */
  def fetchUserByUsername(
    realm: Realm,
    username: String
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Option[User]] = {
    val path = s"${usersPath(realm.name)}?username=$username"

    for {
      userJsOpt <- processFetchRequest(get(path)())
      userJsResultOpt <- \/-(userJsOpt.map(_.validate[Seq[User]]))
      userSeqOpt <- asOptEither(userJsResultOpt)
      userSeq <- userSeqOpt.map(\/-(_)).getOrElse(-\/(EntityNotFound))
      userOpt <- \/-(userSeq.collectFirst({ case user @ User(_, u, _) if username == u => user }))
    } yield userOpt
  }

  /**
    * Assign a password to the given user in Keycloak.
    *
    * @param realm the realm of the user
    * @param user the user to whom to assign the password
    * @param password the password to assign to the user
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or simply Unit for success
    */
  def assignPasswordToUser(
    realm: Realm,
    user: User,
    password: UserPasswordCreationData
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, Unit] = {
    val path = resetUserPasswordPath(realm.name, user.id)
    val jsonData = Json.toJson(password)

    processRequest(put(path, jsonData)())
  }

  /**
    * Create a user in Keycloak.
    *
    * @param realm the realm of the user
    * @param data the user creation data
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the user
    */
  def createUser(
    realm: Realm,
    data: UserCreationData
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, User] = {
    val path = usersPath(realm.name)
    val jsonData = Json.toJson(data)

    for {
    // Create the user.
      createResp <- processCreateRequest(post(path, jsonData)())

      // Get the data for the newly created user.
      userOpt <- fetchUserByUsername(realm, data.username)
      user <- userOpt.map(\/-(_)).getOrElse(-\/(EntityNotFound))
    } yield user
  }

  /**
    * Create a user, and assign it a password, and assign it roles.
    *
    * @param realm the realm of the user
    * @param data the user creation data
    * @param factory the implicit HTTP request factory
    * @param baseURI the implicit base URI for the Keycloak instance
    * @return an either representing the response error or the user
    */
  def initializeUser(
    realm: Realm,
    data: UserCreationData
  )(implicit
    factory: HttpRequestFactory,
    baseURI: URI
  ): \/[ResponseError, User] = {
    for {
      user <- createUser(realm, data)
      passwordAssignmentResult <- assignPasswordToUser(realm, user, data.password)
      roleAssignmentResult <- assignRolesToUser(realm, user, data.roles)
    } yield user
  }
}
