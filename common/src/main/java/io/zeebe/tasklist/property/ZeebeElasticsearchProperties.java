/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.property;

public class ZeebeElasticsearchProperties extends ElasticsearchProperties {

  public ZeebeElasticsearchProperties() {
    this.setDateFormat("yyyy-MM-dd");   //hard-coded, as not configurable on Zeebe side
    this.setElsDateFormat("date");      //hard-coded, as not configurable on Zeebe side
  }

  private String prefix = "zeebe-record";

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }
}
