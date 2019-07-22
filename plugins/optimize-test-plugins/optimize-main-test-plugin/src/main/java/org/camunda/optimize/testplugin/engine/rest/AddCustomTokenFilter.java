/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.testplugin.engine.rest;

import org.camunda.optimize.plugin.engine.rest.EngineRestFilter;

import javax.ws.rs.client.ClientRequestContext;
import java.io.IOException;

public class AddCustomTokenFilter implements EngineRestFilter {

  @Override
  public void filter(ClientRequestContext requestContext, String engineAlias, String engineName) throws IOException {
    requestContext.getHeaders().add("Custom-Token", "SomeCustomToken");
  }

}
