/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.engine;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.it.rule.IntegrationTestProperties;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@AllArgsConstructor
@Slf4j
public class EnginePluginClient {
  private static final String ENGINE_IT_PLUGIN_ENDPOINT = IntegrationTestProperties.getEngineItPluginEndpoint();
  private static final String DEPLOY_PATH = "/deploy";
  private static final String PURGE_PATH = "/purge";

  private final CloseableHttpClient httpClient;

  @SneakyThrows
  public void deployEngine(final String engineName) {
    log.info("Deploying engine with name {}", engineName);
    final HttpPost purgeRequest = new HttpPost(
      new URIBuilder(ENGINE_IT_PLUGIN_ENDPOINT + DEPLOY_PATH)
        .addParameter("name", engineName)
        .build()
    );
    try (CloseableHttpResponse response = httpClient.execute(purgeRequest)) {
      final int statusCode = response.getStatusLine().getStatusCode();
      switch (statusCode) {
        case HttpServletResponse.SC_OK:
          log.info("Finished deploying engine {}.", engineName);
          break;
        case HttpServletResponse.SC_CONFLICT:
          log.info("Engine with name {} was already deployed.", engineName);
          break;
        default:
          log.error("Error deploying engine {}, got status code {}.", engineName, statusCode);
          throw new RuntimeException("Something really bad happened during engine deployment, please check the logs.");
      }
    } catch (IOException e) {
      final String message = String.format("Error deploying engine %s.", engineName);
      log.error(message, engineName, e);
      throw new OptimizeIntegrationTestException(message, e);
    }
  }

  @SneakyThrows
  public void cleanEngine(final String engineName) {
    log.info("Start cleaning engine");
    final HttpPost purgeRequest = new HttpPost(
      new URIBuilder(ENGINE_IT_PLUGIN_ENDPOINT + PURGE_PATH)
        .addParameter("name", engineName)
        .build()
    );
    try (CloseableHttpResponse response = httpClient.execute(purgeRequest)) {
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during purge, please check the logs.");
      }
      log.info("Finished cleaning engine");
    } catch (IOException e) {
      final String message = "Error cleaning engine with purge request";
      log.error(message, e);
      throw new OptimizeIntegrationTestException(message, e);
    }
  }

}
