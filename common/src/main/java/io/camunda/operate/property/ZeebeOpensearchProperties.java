/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

public class ZeebeOpensearchProperties extends OpensearchProperties {

  public static final String ZEEBE_INDEX_PREFIX_DEFAULT = "zeebe-record";

  public ZeebeOpensearchProperties() {
    this.setDateFormat("yyyy-MM-dd");   //hard-coded, as not configurable on Zeebe side
    this.setOsDateFormat("date");      //hard-coded, as not configurable on Zeebe side
  }

  private String prefix = ZEEBE_INDEX_PREFIX_DEFAULT;

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }
}
