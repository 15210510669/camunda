/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.property;

public class ZeebeOpenSearchProperties extends OpenSearchProperties {

  public static final String ZEEBE_INDEX_PREFIX_DEFAULT = "zeebe-record";
  private String prefix = ZEEBE_INDEX_PREFIX_DEFAULT;

  public ZeebeOpenSearchProperties() {
    this.setDateFormat("yyyy-MM-dd"); // hard-coded, as not configurable on Zeebe side
    this.setElsDateFormat("date"); // hard-coded, as not configurable on Zeebe side
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }
}
