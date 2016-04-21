package com.applause.test.keycloak

package object util {

  def getEnvVar(name: String) = Option(System.getenv(name))
}
