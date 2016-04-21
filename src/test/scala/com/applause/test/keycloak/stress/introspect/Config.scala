package com.applause.test.keycloak.stress.introspect

/**
  * Contains stress test data generation configuration.
  */
object Config {
  val baseUrlEnvVar = "KST_BASE_URL"
  val stressTestRealmNameEnvVar = "KST_REALM_NAME"
  val clientIdEnvVar = "KST_CLIENT_ID"
  val clientSecretEnvVar = "KST_CLIENT_SECRET"
  val skipUserCreationEnvVar = "KST_SKIP_USER_CREATION"
  val userCountEnvVar = "KST_USER_COUNT"
  val startUserIndexEnvVar = "KST_START_USER_INDEX"
  val threadCountEnvVar = "KST_THREAD_COUNT"

  val defaultSkipUserCreation = "0"
  val defaultUserCount = 1000
  val defaultStartUserIndex = 0
  val defaultThreadCount = 4

  def configFromEnv(env: (String) => Option[String]): Option[Config] = {
    def getEnvOrLog(name: String): Option[String] =
      env(name).orElse {
        println(s"==== Env var not found: $name")
        None
      }

    (for {
      baseUrl <- getEnvOrLog(baseUrlEnvVar)

      stressTestRealmName <- getEnvOrLog(stressTestRealmNameEnvVar)

      clientId <- getEnvOrLog(clientIdEnvVar)

      clientSecret <- getEnvOrLog(clientSecretEnvVar)

      skipUserCreation <- getEnvOrLog(skipUserCreationEnvVar).orElse {
        println(s"==== Using default value for $skipUserCreationEnvVar: $defaultSkipUserCreation")
        Some(defaultSkipUserCreation.toString)
      } map(_.toInt != 0)

      userCount <- getEnvOrLog(userCountEnvVar).orElse {
        println(s"==== Using default value for $userCountEnvVar: $defaultUserCount")
        Some(defaultUserCount.toString)
      } map(_.toInt)

      startUserIndex <- getEnvOrLog(startUserIndexEnvVar).orElse {
        println(s"==== Using default value for $startUserIndexEnvVar: $defaultStartUserIndex")
        Some(defaultStartUserIndex.toString)
      } map(_.toInt)

      threadCount <- getEnvOrLog(threadCountEnvVar).orElse {
        println(s"==== Using default value for $threadCountEnvVar: $defaultThreadCount")
        Some(threadCountEnvVar.toString)
      } map(_.toInt)
    } yield Config(baseUrl, stressTestRealmName, clientId, clientSecret, skipUserCreation, userCount, startUserIndex, threadCount)).orElse {
      println(s"==== Unable to create configuration from env")
      None
    }
  }

}

/**
  * Contains stress test data generation configuration.
  * @param baseUrl the Keycloak server base URL
  * @param realmName the stress test realm name
  * @param clientId the ID of the client to use in data generation
  * @param clientSecret the secret of the client to use in data generation
  * @param skipUserCreation if true, skip user generation, otherwise create the users
  * @param userCount the amount of users to create/use in the test
  * @param startUserIndex the starting index of the users to create/use
  * @param threads the number of threads to use in data generation
  */
case class Config(
  baseUrl: String,
  realmName: String,
  clientId: String,
  clientSecret: String,
  skipUserCreation: Boolean,
  userCount: Int,
  startUserIndex: Int,
  threads: Int
)
