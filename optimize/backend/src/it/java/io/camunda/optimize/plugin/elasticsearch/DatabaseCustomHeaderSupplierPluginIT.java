/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.plugin.elasticsearch;

import static jakarta.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import io.camunda.optimize.AbstractPlatformIT;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClientConfiguration;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.NottableString;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationTimes;

public class DatabaseCustomHeaderSupplierPluginIT extends AbstractPlatformIT {

  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void fixedCustomHeadersAddedToElasticsearchRequest() {
    // given
    String basePackage = "io.camunda.optimize.testplugin.elasticsearch.authorization.fixed";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    final ClientAndServer dbMockServer = useAndGetDbMockServer();

    // when
    statusClient.getStatus();

    // then
    dbMockServer.verify(request().withHeader(new Header("Authorization", "Bearer fixedToken")));
  }

  @Test
  public void fixedCustomHeadersAddedToElasticsearchRequestDuringClientSetup() {
    // given
    String basePackage = "io.camunda.optimize.testplugin.elasticsearch.authorization.fixed";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    final ClientAndServer dbMockServer = useAndGetDbMockServer();
    // clear all mock recordings that happen during setup
    dbMockServer.clear(request());

    // when
    embeddedOptimizeExtension
        .getBean(OptimizeElasticsearchClientConfiguration.class)
        .createOptimizeElasticsearchClient(new BackoffCalculator(1, 1));
    // clear the version validation request the client does on first use, which bypasses our plugins
    // see RestHighLevelClient#versionValidationFuture
    dbMockServer.clear(request("/").withMethod(GET));

    // then
    dbMockServer.verify(
        request().withHeader(new Header("Authorization", "Bearer fixedToken")),
        VerificationTimes.atLeast(1));
    // ensure there was no request without the header
    dbMockServer.verify(
        request().withHeader(NottableString.not("Authorization")), VerificationTimes.exactly(0));
  }

  @Test
  public void dynamicCustomHeadersAddedToElasticsearchRequest() {
    // given
    String basePackage = "io.camunda.optimize.testplugin.elasticsearch.authorization.dynamic";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    embeddedOptimizeExtension.reloadConfiguration();
    final ClientAndServer dbMockServer = useAndGetDbMockServer();

    // when
    statusClient.getStatus();
    statusClient.getStatus();
    statusClient.getStatus();

    // then
    final RequestDefinition[] allRequests = dbMockServer.retrieveRecordedRequests(null);
    assertThat(allRequests).hasSizeGreaterThan(1);
    IntStream.range(0, allRequests.length)
        .forEach(
            integerSuffix ->
                dbMockServer.verify(
                    request()
                        .withHeader(
                            new Header("Authorization", "Bearer dynamicToken_" + integerSuffix)),
                    VerificationTimes.once()));
  }

  @Test
  public void multipleCustomHeadersAddedToElasticsearchRequest() {
    // given
    String[] basePackages = {
      "io.camunda.optimize.testplugin.elasticsearch.authorization.fixed",
      "io.camunda.optimize.testplugin.elasticsearch.custom"
    };
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackages);
    embeddedOptimizeExtension.reloadConfiguration();
    final ClientAndServer dbMockServer = useAndGetDbMockServer();

    // when
    statusClient.getStatus();

    // then
    dbMockServer.verify(
        request()
            .withHeaders(
                new Header("Authorization", "Bearer fixedToken"),
                new Header("CustomHeader", "customValue")),
        VerificationTimes.atLeast(1));
  }

  private void addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(
      String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    // We don't need to reload the configuration as the MockServer initialization already does this
    // immediately after
    configurationService.setElasticsearchCustomHeaderPluginBasePackages(basePackagesList);
  }
}
