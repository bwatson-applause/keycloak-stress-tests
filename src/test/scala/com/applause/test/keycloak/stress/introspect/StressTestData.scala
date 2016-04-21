package com.applause.test.keycloak.stress.introspect

import java.net.URI
import java.util.UUID
import java.util.concurrent.{Executors, ThreadFactory}

import com.applause.test.keycloak.client.{OIDCClient, OIDCClientCredentials}
import com.applause.test.keycloak.dom._
import com.applause.test.keycloak.util._
import com.google.api.client.http.javanet.NetHttpTransport

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scalaz.{\/, \/-}

/**
  * Contains functions for creating the Keycloak data needed for stress tests.
  */
object StressTestData {
  import com.applause.test.keycloak.client.rest._
  import RestApiPaths._

  /** The name of the client used for stress tests */
  private val stressTestClientName = "stress-test-client"

  /**
    * Create a fixed size thread pool.
    *
    * @param nThreads the number of threads in the pool
    * @return the thread pool
    */
  private def createExecutionContext(nThreads: Int): ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(nThreads, new ThreadFactory {
      def newThread(r: Runnable): Thread  = {
        val t = new Thread(r)
        t.setDaemon(true)
        t
      }
    }))

  /**
    * Convert a hex character into a role name.
    *
    * @param c the hex character
    * @return the role name
    */
  private def toTestRoleName(c: Char): String = s"role_0${"%s".format(c).toUpperCase}"

  /**
    * Convert a number into a role name.
    *
    * @param i the number
    * @return the role name
    */
  private def toTestRoleName(i: Int): String = toTestRoleName("%x".format(i).charAt(0))

  /**
    * Run the provided function with the padded integer and UUID generated for the given seed
    *
    * @param n the seed
    * @param f the function to run
    * @tparam A the return type fo the function
    * @return the result of the function
    */
  private def withUserSeedData[A](n: Int)(f: (String, String) => (A)): A = {
    val padded = "%06d".format(n)
    val uuid = UUID.nameUUIDFromBytes(padded.getBytes).toString
    f(padded, uuid)
  }

  /**
    * Create an email address for the padded integer
    *
    * @param padded the padded integer
    * @return the generated email address
    */
  def emailForPadded(padded: String) = s"user.$padded@example.com"

  /**
    * Create test user creation data for the given seed and roles.
    *
    * @param n the user generation seed
    * @param roles the role map
    * @return the test user creation data
    */
  def testUserCreationData(n: Int, roles: Map[String, Role]): UserCreationData = withUserSeedData(n) { (padded, uuid) =>
    val uuidPrefix = uuid.split('-')(0)
    val email = emailForPadded(padded)
    val userRoles = uuidPrefix.toUpperCase.toSet.toSeq.sorted.map(toTestRoleName).flatMap(roles.get)

    UserCreationData(s"user-$padded".toUpperCase, uuid, email, email, UserPasswordCreationData(uuid), userRoles)
  }

  /**
    * Log in credentials for a user.
    *
    * @param username the username
    * @param password the password
    */
  case class UserCredentials(
    username: String,
    password: String
  )

  /**
    * Create test user credentials for the given seed.
    *
    * @param n the seed
    * @return the generated user cerdentials
    */
  def testUserCredentials(n: Int): UserCredentials = withUserSeedData(n) { (padded, uuid) =>
    UserCredentials(emailForPadded(padded), uuid)
  }

  /**
    * Helper function for creating an entity if it can not be fetched.
    *
    * @param f the fetch function
    * @param c the creation function
    * @tparam A the entity type
    * @return an either representing the entity of the reason it could not be fetched or created.
    */
  def fetchOrCreate[A](f: => \/[ResponseError, Option[A]])(c: => \/[ResponseError, A]): \/[ResponseError, A] =
    f.flatMap {
      case Some(a) => \/-(a)
      case None => c
    }

  /**
    * This is the main data creation method. It ensures a proper realm exists for the provided configuration.
    *
    * @param config the realm creation configuration
    * @return the resulting realm and OAuth2 client data
    */
  def ensureRealm(config: Config) = {
    implicit val executionContext = createExecutionContext(config.threads)

    implicit val transport = new NetHttpTransport()
    implicit val baseURI = new URI(config.baseUrl)

    val adminClientCreds = OIDCClientCredentials(config.clientId, config.clientSecret)
    val adminClient = OIDCClient.forClientCredentials(baseURI, tokenPath(masterRealmName), transport, adminClientCreds)

    // This client factory will automatically handle OAuth2 token (re)fetches.
    implicit val factory = adminClient.httpRequestFactory

    val realmCreationData = RealmCreationData(config.realmName, config.realmName)
    val clientCreationData = ClientCreationData(stressTestClientName)

    for {
      realm <- fetchOrCreate(fetchRealm(realmCreationData.name))(createRealm(realmCreationData))

      client <- fetchOrCreate(fetchClientByClientId(realm, clientCreationData.clientId))(createClient(realm, clientCreationData))

      roleMap <- (0 until 16).map { n =>
        val roleName = toTestRoleName(n)
        fetchOrCreate(fetchRole(realm, roleName))(createRole(realm, RoleCreationData(roleName)))
      } .foldRight[\/[ResponseError, Map[String, Role]]](\/-(Map.empty)) { (roleE, mE) =>
        roleE.flatMap(role => mE.map(_ + (role.name -> role)))
      }

      userResult <-
        if (config.skipUserCreation) \/-()
        else {
          Stream.range(config.startUserIndex, config.startUserIndex + config.userCount).grouped(config.threads).map { grpStream =>
            Await.result(Future.sequence(grpStream.toList.map { n => Future {
              val userData = testUserCreationData(n, roleMap)
              if (n % 100 == 0) println(s"==== Processing user $n ...")
              fetchOrCreate(fetchUserByUsername(realm, userData.username))(initializeUser(realm, userData))
            }
            }), 10 seconds).foldRight[\/[ResponseError, Unit]](\/-()) { (acc, u) => acc.flatMap(_ => u) }
          }.foldRight[\/[ResponseError, Unit]](\/-()) { (acc, u) => acc.flatMap(_ => u) }
        } map { result =>
          println(s"==== Processed ${config.userCount} user(s)")
          result
        }
    } yield ( realm, client )
  }

  /**
    * Main used to create stress test data. Config data is pulled from environment variables.
    */
  def main(args: Array[String]) {
    System.exit(Config.configFromEnv(getEnvVar)
      .map(StressTestData.ensureRealm)
      .map(_.map(_ => 0).getOrElse(1))
      .getOrElse(2)
    )
  }
}
