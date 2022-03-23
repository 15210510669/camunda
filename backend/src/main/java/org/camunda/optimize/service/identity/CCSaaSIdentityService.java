/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.rest.cloud.CCSaaSUserClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSIdentityService extends AbstractCachedIdentityService {

  private final CCSaaSUserClient userClient;

  public CCSaaSIdentityService(final ConfigurationService configurationService,
                               final UserIdentityCache syncedIdentityCache,
                               final CCSaaSUserClient userClient) {
    super(configurationService, syncedIdentityCache);
    this.userClient = userClient;
  }

  @Override
  public Optional<UserDto> getUserById(final String userId) {
    return syncedIdentityCache.getUserIdentityById(userId)
      .or(() -> {
        try {
          return userClient.getCloudUserForId(userId)
            .map(cloudUser -> new UserDto(cloudUser.getUserId(), cloudUser.getName(), cloudUser.getEmail()));
        } catch (Exception exception) {
          log.warn("Failed fetching Cloud user with id {}: ", userId, exception);
          return Optional.empty();
        }
      });
  }

  @Override
  public Optional<GroupDto> getGroupById(final String groupId) {
    return Optional.empty();
  }

  @Override
  public List<GroupDto> getAllGroupsOfUser(final String userId) {
    return Collections.emptyList();
  }

  @Override
  public boolean isUserAuthorizedToAccessIdentity(final String userId, final IdentityDto identity) {
    return true;
  }

}
