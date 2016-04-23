package com.applause.test.keycloak.stress.introspect

import java.net.URI

/**
  * Contains stress test data generation configuration.
  */
object Config {
  val baseUrlEnvVar = "KST_BASE_URL"
  val realmNameEnvVar = "KST_REALM_NAME"

  val adminUserEnvVar = "KST_ADMIN_USER"
  val adminPasswordEnvVar = "KST_ADMIN_PASSWORD"

  val userCountEnvVar = "KST_USER_COUNT"
  val startUserIndexEnvVar = "KST_START_USER_INDEX"

  val skipUserCreationEnvVar = "KST_SKIP_USER_CREATION"
  val dataSeedThreadCountEnvVar = "KST_DATA_SEED_THREAD_COUNT"

  val defaultUserCount = 100
  val defaultStartUserIndex = 0
  val defaultSkipUserCreation = 0
  val defaultDataSeedThreadCount = 4

  def configFromEnv(env: (String) => Option[String]): Option[Config] = {

    def getEnv(name: String, defaultValue: Option[String] = None): Option[String] =
      env(name).orElse {
        println(s"==== Env var not found: $name")
        defaultValue.map { dv =>
          println(s"==== Using default value for $name: $dv")
          dv
        }
      }

    (for {
      baseUrl <- getEnv(baseUrlEnvVar).map(new URI(_))
      realmName <- getEnv(realmNameEnvVar)
      adminUser <- getEnv(adminUserEnvVar)
      adminPassword <- getEnv(adminPasswordEnvVar)
      userCount <- getEnv(userCountEnvVar, Some(defaultUserCount.toString)).map(_.toInt)
      startUserIndex <- getEnv(startUserIndexEnvVar, Some(defaultStartUserIndex.toString)).map(_.toInt)
      skipUserCreation <- getEnv(skipUserCreationEnvVar, Some(defaultSkipUserCreation.toString)).map(_.toInt).map(_ != 0)
      dataSeedThreadCount <- getEnv(dataSeedThreadCountEnvVar, Some(defaultDataSeedThreadCount.toString)).map(_.toInt)
    } yield Config(baseUrl, realmName, adminUser, adminPassword, userCount, startUserIndex, skipUserCreation, dataSeedThreadCount)).orElse {
      println(s"==== Unable to create configuration from env")
      None
    }
  }

}

/**
  * Contains stress test data generation configuration.
  * @param baseURI the Keycloak server base URI
  * @param realmName the name of the stress test realm
  * @param adminUser the ID of the client to use in data generation
  * @param adminPassword the secret of the client to use in data generation
  * @param skipUserCreation if true, skip user generation, otherwise create the users
  * @param userCount the amount of users to create/use in the test
  * @param startUserIndex the starting index of the users to create/use
  * @param dataSeedThreads the number of threads to use in data generation
  */
case class Config(
  baseURI: URI,
  realmName: String,
  adminUser: String,
  adminPassword: String,
  userCount: Int,
  startUserIndex: Int,
  skipUserCreation: Boolean,
  dataSeedThreads: Int
)
