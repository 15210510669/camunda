/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSUserClient extends AbstractCCSaaSClient {

  public CCSaaSUserClient(
      final ConfigurationService configurationService, final ObjectMapper objectMapper) {
    super(objectMapper, configurationService);
  }

  public Optional<CloudUserDto> getCloudUserById(final String userId, final String accessToken) {
    try {
      log.info("Fetching Cloud user by id.");
      final HttpGet request =
          new HttpGet(
              String.format(
                  GET_USER_BY_ID_TEMPLATE,
                  getCloudUsersConfiguration().getAccountsUrl(),
                  getCloudAuthConfiguration().getOrganizationId(),
                  URLEncoder.encode(userId, StandardCharsets.UTF_8)));
      try (final CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
          return Optional.empty();
        } else if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new OptimizeRuntimeException(
              String.format(
                  "Unexpected response when fetching cloud user by id: %s",
                  response.getStatusLine().getStatusCode()));
        }
        return Optional.ofNullable(
            objectMapper.readValue(response.getEntity().getContent(), CloudUserDto.class));
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem fetching the cloud user by id.", e);
    }
  }

  public List<CloudUserDto> fetchAllCloudUsers(final String accessToken) {
    try {
      log.info("Fetching Cloud users.");
      final HttpGet request =
          new HttpGet(
              String.format(
                  GET_USERS_TEMPLATE,
                  getCloudUsersConfiguration().getAccountsUrl(),
                  getCloudAuthConfiguration().getOrganizationId()));
      try (final CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new OptimizeRuntimeException(
              String.format(
                  "Unexpected response when fetching cloud users: %s",
                  response.getStatusLine().getStatusCode()));
        }
        return objectMapper.readValue(
            response.getEntity().getContent(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, CloudUserDto.class));
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem fetching Cloud users.", e);
    }
  }
}
