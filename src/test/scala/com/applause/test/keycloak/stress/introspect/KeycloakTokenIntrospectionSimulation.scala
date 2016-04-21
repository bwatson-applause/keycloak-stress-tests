package com.applause.test.keycloak.stress.introspect

import java.util.concurrent.atomic.AtomicInteger

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * The simulation used to stress test token validation via token introspection.
  */
class KeycloakTokenIntrospectionSimulation extends Simulation {
  import com.applause.test.keycloak.client.rest.RestApiPaths._
  import com.applause.test.keycloak.util._

  // Create the config and ensure the stress test data exists.
  val configOpt = Config.configFromEnv(getEnvVar)
  val dataSeedResultOpt = configOpt.flatMap(c => StressTestData.ensureRealm(c).toOption)

  // No point in running the test if the data is bad or missing.
  before {
    assert(configOpt.isDefined, "Unable to create stress test configuration")
    assert(dataSeedResultOpt.isDefined, "Unable to initialize the stress test realm.")
  }

  // If the config and seeding are valid, set up the test.
  configOpt.foreach { config =>
    dataSeedResultOpt.foreach { case (realm, client) =>

      // Thread-safe counter used for seeding test user data.
      val userIdCounter = new AtomicInteger(0)

      // The base HTTP request data.
      val BaseHttpConfig = http
        .baseURL(config.baseUrl)
        .acceptHeader("*/*")
        .acceptLanguageHeader("en-US,en;q=0.8")
        .acceptEncodingHeader("gzip, deflate")
        .userAgentHeader("Gatling Stress Test Tool")

      // Create the needed URL paths.
      val tokenRequestPath = tokenPath(config.realmName)
      val tokenIntrospectionRequestPath = tokenIntrospectionPath(config.realmName)

      /**
        * Scenario for initializing sessions.
        */
      object KeycloakSessionInitScenario {
        val action =
          exec({ session =>
            // Create the user credentials.
            val userCreds = StressTestData.testUserCredentials(userIdCounter.getAndIncrement())
            session
              .set("kcUsername", userCreds.username)
              .set("kcPassword", userCreds.password)
          }).exec(
            // Obtain an access token.
            http("keycloak_token_request")
              .post(tokenRequestPath)
              .formParam("grant_type", "password")
              .formParam("client_id", client.clientId)
              .formParam("client_secret", client.clientSecret)
              .formParam("username", "${kcUsername}")
              .formParam("password", "${kcPassword}")
              .check(status.is(200))
              .check(jsonPath("$.access_token").saveAs("kcAccessToken"))
          )
      }

      /**
        * Scenario for performing access token introspection.
        */
      object KeycloakTokenIntrospectionScenario {
        val action =
          repeat(100) {
            exec(
              pause(1 second)
            ).exec(
              http("keycloak_token_introspection")
                .post(tokenIntrospectionRequestPath)
                .basicAuth(client.clientId, client.clientSecret)
                .formParam("token", "${kcAccessToken}")
                .check(status.is(200))
                .check(jsonPath("$.active").ofType[Boolean].is(true))
            )
          }
      }

      // Set up the combined scenario.
      val stressTest = scenario("keycloak_token_introspection_scenario")
        .exec(KeycloakSessionInitScenario.action)
        .exec(KeycloakTokenIntrospectionScenario.action)

      // Set up the stress test.
      setUp(
        stressTest.inject(
          rampUsers(config.userCount) over(30 seconds)
        )
      ).protocols(BaseHttpConfig)
    }
  }
}
