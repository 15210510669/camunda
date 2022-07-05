/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

public class Auth0Properties {
  public static final String DEFAULT_ORGANIZATIONS_KEY = "https://camunda.com/organizations";
  /**
   * Defines the domain which the user always sees<br/> auth0.com call it <b>Custom Domain</b>
   */
  private String domain;

  /**
   * Defines the domain which provides information about the user<br/> auth0.com call it
   * <b>Domain</b>
   */
  private String backendDomain;

  /**
   * This is the client id of auth0 application (see Settings page on auth0 dashboard) It's like an
   * user name for the application
   */
  private String clientId;

  /**
   * This is the client secret of auth0 application (see Settings page on auth0 dashboard) It's like
   * a password for the application
   */
  private String clientSecret;

  /**
   * The claim we want to check It's like a permission name
   */
  private String claimName;

  /**
   * Key for claim to retrieve the user name
   */
  private String nameKey = "name";

  private String m2mClientId;

  private String m2mClientSecret;

  private String m2mAudience;
  private String organizationsKey = DEFAULT_ORGANIZATIONS_KEY;

  public void setOrganizationsKey(final String organizationsKey) {
    this.organizationsKey = organizationsKey;
  }

  public String getOrganizationsKey() {
    return organizationsKey;
  }

  public String getDomain() {
    return domain;
  }

  public Auth0Properties setDomain(final String domain) {
    this.domain = domain;
    return this;
  }

  public String getBackendDomain() {
    return backendDomain;
  }

  public Auth0Properties setBackendDomain(final String backendDomain) {
    this.backendDomain = backendDomain;
    return this;
  }

  public String getClientId() {
    return clientId;
  }

  public Auth0Properties setClientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public Auth0Properties setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
    return this;
  }

  public String getClaimName() {
    return claimName;
  }

  public Auth0Properties setClaimName(final String claimName) {
    this.claimName = claimName;
    return this;
  }

  public String getNameKey() {
    return nameKey;
  }

  public Auth0Properties setNameKey(final String nameKey) {
    this.nameKey = nameKey;
    return this;
  }

  public String getM2mClientId() {
    return m2mClientId;
  }

  public Auth0Properties setM2mClientId(final String m2mClientId) {
    this.m2mClientId = m2mClientId;
    return this;
  }

  public String getM2mClientSecret() {
    return m2mClientSecret;
  }

  public Auth0Properties setM2mClientSecret(final String m2mClientSecret) {
    this.m2mClientSecret = m2mClientSecret;
    return this;
  }

  public String getM2mAudience() {
    return m2mAudience;
  }

  public Auth0Properties setM2mAudience(final String m2mAudience) {
    this.m2mAudience = m2mAudience;
    return this;
  }
}
