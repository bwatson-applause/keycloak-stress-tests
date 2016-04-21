package com.applause.test.keycloak.client.rest

import java.util.UUID

/**
  * Contains logic for generating various Keycloak RESTful paths.
  */
object RestApiPaths {
  val masterRealmName = "master"

  val adminPath = "admin"
  val realmsPath = s"$adminPath/realms"

  def tokenPath(realm: String) = s"realms/$realm/protocol/openid-connect/token"
  def tokenIntrospectionPath(realm: String) = s"${tokenPath(realm)}/introspect"

  def realmPath(realm: String) = s"$realmsPath/$realm"

  def clientsPath(realm: String) = s"${realmPath(realm)}/clients"
  def clientByIdPath(realm: String, clientId: UUID) = s"${clientsPath(realm)}/$clientId"
  def clientSecretPath(realm: String, clientId: UUID) = s"${clientByIdPath(realm, clientId)}/client-secret"

  def rolesPath(realm: String) = s"${realmPath(realm)}/roles"
  def roleByNamePath(realm: String, roleName: String) = s"${realmPath(realm)}/roles/$roleName"
  def roleByIdPath(realm: String, roleId: UUID) = s"${realmPath(realm)}/roles-by-id/$roleId"

  def usersPath(realm: String) = s"${realmPath(realm)}/users"
  def userPath(realm: String, userId: UUID) = s"${realmPath(realm)}/users/$userId"
  def resetUserPasswordPath(realm: String, userId: UUID) = s"${userPath(realm, userId)}/reset-password"
  def assignRealmRolesToUserPath(realm: String, userId: UUID) = s"${userPath(realm, userId)}/role-mappings/realm"
}
