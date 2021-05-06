/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.property;

public class SslProperties {

  private String certificatePath;
  private boolean verifyHostname = true;
  private boolean selfSigned = false;

  public String getCertificatePath() {
    return certificatePath;
  }

  public void setCertificatePath(String certificatePath) {
    this.certificatePath = certificatePath;
  }

  public boolean isVerifyHostname() {
    return verifyHostname;
  }

  public void setVerifyHostname(boolean verifyHostname) {
    this.verifyHostname = verifyHostname;
  }

  public boolean isSelfSigned() {
    return selfSigned;
  }

  public void setSelfSigned(boolean selfSigned) {
    this.selfSigned = selfSigned;
  }
}
