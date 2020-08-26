/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.property;

public class LdapProperties {

  // LDAP properties
  // Also used as url for ActiveDirectory
  private String url;
  // Also used as rootDn for ActiveDirectory
  private String baseDn;
  private String userDnPatterns;
  private String userSearchBase;
  private String managerDn;
  private String managerPassword;
  // Also used for ActiveDirectory search filter
  private String userSearchFilter;

  // Properties for specific LDAP service provided by Active Directory Server
  private String domain;

  public String getBaseDn() {
    return baseDn;
  }

  public void setBaseDn(String baseDn) {
    this.baseDn = baseDn;
  }

  public String getUserSearchBase() {
    return userSearchBase == null ? "" : userSearchBase;
  }

  public void setUserSearchBase(String userSearchBase) {
    this.userSearchBase = userSearchBase;
  }

  public String getManagerDn() {
    return managerDn;
  }

  public void setManagerDn(String managerDn) {
    this.managerDn = managerDn;
  }

  public String getManagerPassword() {
    return managerPassword;
  }

  public void setManagerPassword(String managerPassword) {
    this.managerPassword = managerPassword;
  }

  public String getUserSearchFilter() { return userSearchFilter; }

  public void setUserSearchFilter(String userSearchFilter) {
    this.userSearchFilter = userSearchFilter;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUserDnPatterns() {
    return userDnPatterns==null?"":userDnPatterns;
  }

  public void setUserDnPatterns(String userDnPatterns) {
    this.userDnPatterns = userDnPatterns;
  }

  public String getDomain() { return domain; }

  public void setDomain(String domain) { this.domain = domain; }

}
