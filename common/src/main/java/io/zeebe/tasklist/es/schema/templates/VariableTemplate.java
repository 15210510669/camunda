/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.es.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class VariableTemplate extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "variable";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String SCOPE_KEY = "scopeKey";
  public static final String NAME = "name";
  public static final String VALUE = "value";

  @Override
  protected String getIndexNameFormat() {
    return INDEX_NAME;
  }
}
