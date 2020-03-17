/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util.rest;

import java.net.URI;

import org.apache.http.client.HttpClient;
import org.apache.http.protocol.HttpContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Factory class which sets up the HttpComponents context to be the same on
 * every request with the RestTemplate.
 *
 */
public class StatefulHttpComponentsClientHttpRequestFactory extends HttpComponentsClientHttpRequestFactory {

  private final HttpContext httpContext;

  public StatefulHttpComponentsClientHttpRequestFactory(HttpClient httpClient, HttpContext httpContext) {
    super(httpClient);
    this.httpContext = httpContext;
  }

  @Override
  protected HttpContext createHttpContext(HttpMethod httpMethod, URI uri) {
    return this.httpContext;
  }
}