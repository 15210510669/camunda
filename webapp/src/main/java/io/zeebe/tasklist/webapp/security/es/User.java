/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.es;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

public class User extends org.springframework.security.core.userdetails.User {

  private String firstname;
  private String lastname;
  private boolean canLogout = true;

  public User(
      String username, String password, Collection<? extends GrantedAuthority> authorities) {
    super(username, password, authorities);
  }

  public String getFirstname() {
    return firstname;
  }

  public User setFirstname(String firstname) {
    this.firstname = firstname;
    return this;
  }

  public String getLastname() {
    return lastname;
  }

  public User setLastname(String lastname) {
    this.lastname = lastname;
    return this;
  }

  public boolean isCanLogout() {
    return canLogout;
  }

  public User setCanLogout(boolean canLogout) {
    this.canLogout = canLogout;
    return this;
  }
}
