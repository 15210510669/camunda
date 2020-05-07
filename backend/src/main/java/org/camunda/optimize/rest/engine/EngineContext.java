/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.engine;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.dto.engine.EngineGroupDto;
import org.camunda.optimize.dto.engine.EngineListUserDto;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.GROUP_BY_ID_ENDPOINT_TEMPLATE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.GROUP_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MEMBER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MEMBER_OF_GROUP;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_USER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_ASC;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.USER_BY_ID_ENDPOINT_TEMPLATE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.USER_COUNT_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.USER_ENDPOINT;

@Slf4j
public class EngineContext {
  private static final Set<String> OPTIMIZE_APPLICATION_AUTH_RESOURCE_IDS = ImmutableSet.of(
    ALL_RESOURCES_RESOURCE_ID, OPTIMIZE_APPLICATION_RESOURCE_ID
  );
  private String engineAlias;
  private Client engineClient;
  private ConfigurationService configurationService;

  public EngineContext(String engineAlias, Client engineClient, ConfigurationService configurationService) {
    this.engineAlias = engineAlias;
    this.engineClient = engineClient;
    this.configurationService = configurationService;
  }

  public Client getEngineClient() {
    return engineClient;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public Optional<String> getDefaultTenantId() {
    return configurationService.getEngineDefaultTenantIdOfCustomEngine(engineAlias);
  }

  public AuthorizedIdentitiesResult getApplicationAuthorizedIdentities() {
    final AuthorizedIdentitiesResult authorizedIdentitiesResult = new AuthorizedIdentitiesResult();
    final List<AuthorizationDto> optimizeGrantAndRevokeAuthorizations = getAllApplicationAuthorizations().stream()
      .filter(authorizationDto -> OPTIMIZE_APPLICATION_AUTH_RESOURCE_IDS.contains(authorizationDto.getResourceId()))
      .peek(authorizationDto -> {
        if (AUTHORIZATION_TYPE_GLOBAL == authorizationDto.getType()) {
          authorizedIdentitiesResult.setGlobalOptimizeGrant(true);
        }
      })
      .filter(authorizationDto -> AUTHORIZATION_TYPE_GLOBAL != authorizationDto.getType())
      .collect(toList());

    optimizeGrantAndRevokeAuthorizations.stream()
      .sorted(
        // as users authorization win over group authorizations, we order by having group first
        Comparator.comparing(AuthorizationDto::getGroupId, Comparator.nullsLast(Comparator.naturalOrder()))
          // as revokes win over grants we have them last (AUTHORIZATION_TYPE_GRANT=1 < AUTHORIZATION_TYPE_REVOKE=2)
          .thenComparing(AuthorizationDto::getType)
      ).forEach(authorizationDto -> {
      switch (authorizationDto.getType()) {
        case AUTHORIZATION_TYPE_GRANT:
          if (authorizationDto.getGroupId() != null) {
            authorizedIdentitiesResult.getGrantedGroupIds().add(authorizationDto.getGroupId());
          } else {
            authorizedIdentitiesResult.getGrantedUserIds().add(authorizationDto.getUserId());
          }
          break;
        case AUTHORIZATION_TYPE_REVOKE:
          if (authorizationDto.getGroupId() != null) {
            authorizedIdentitiesResult.getRevokedGroupIds().add(authorizationDto.getGroupId());
          } else {
            authorizedIdentitiesResult.getRevokedUserIds().add(authorizationDto.getUserId());
          }
          break;
        default:
          throw new OptimizeRuntimeException("Unexpected authorization type:" + authorizationDto.getType());
      }
    });
    return authorizedIdentitiesResult;
  }

  public Optional<UserDto> getUserById(final String userId) {
    EngineListUserDto engineUserDto = null;
    try {
      Response response = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(USER_BY_ID_ENDPOINT_TEMPLATE)
        .resolveTemplate("id", userId)
        .request(MediaType.APPLICATION_JSON)
        .get();
      if (response.getStatus() == Response.Status.OK.getStatusCode()) {
        engineUserDto = response.readEntity(EngineListUserDto.class);
      }
      response.close();
    } catch (Exception e) {
      String message = String.format(
        "Could not fetch user with id [%s] from engine with alias [%s]",
        userId,
        engineAlias
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
    return Optional.ofNullable(engineUserDto)
      .map(this::mapEngineUser);
  }

  private UserDto mapEngineUser(EngineListUserDto engineUser) {
    if (this.configurationService.getIdentitySyncConfiguration().isIncludeUserMetaData()) {
      return new UserDto(
        engineUser.getId(),
        engineUser.getFirstName(),
        engineUser.getLastName(),
        engineUser.getEmail()
      );
    } else {
      return new UserDto(engineUser.getId());
    }
  }

  public List<UserDto> getUsersById(final List<String> userIds) {
    return userIds.stream()
      // consider adding get multiple users by id to optimize api in engine, see OPT-2788
      .map(this::getUserById)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  public List<UserDto> fetchPageOfUsers(final int pageStartIndex, final int pageLimit) {
    return fetchPageOfUsers(pageStartIndex, pageLimit, null);
  }

  public List<UserDto> fetchPageOfUsers(final int pageStartIndex, final int pageLimit, final String groupId) {
    Response response = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .queryParam(MAX_RESULTS_TO_RETURN, pageLimit)
      .queryParam("sortBy", "userId")
      .queryParam("sortOrder", "asc")
      .queryParam("memberOfGroup", groupId)
      .queryParam(INDEX_OF_FIRST_RESULT, pageStartIndex)
      .path(USER_ENDPOINT)
      .request(MediaType.APPLICATION_JSON)
      .get();
    if (response.getStatus() == Response.Status.OK.getStatusCode()) {
      // @formatter:off
      return response.readEntity(new GenericType<List<EngineListUserDto>>() {})
      // @formatter:on
        .stream()
        .map(this::mapEngineUser)
        .collect(toList());

    } else {
      final String message = String.format(
        "Failed querying users from engine with alias [%s], response status: [%s].", response.getStatus(), engineAlias
      );
      response.close();
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }

  public List<GroupDto> getGroupsById(final List<String> groupIds) {
    return groupIds.stream()
      // consider adding get multiple groups by id to optimize api in engine, see OPT-2788
      .map(this::getGroupById)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  public Optional<GroupDto> getGroupById(final String groupId) {
    EngineGroupDto groupDto = null;
    try {
      Response response = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(GROUP_BY_ID_ENDPOINT_TEMPLATE)
        .resolveTemplate("id", groupId)
        .request(MediaType.APPLICATION_JSON)
        .get();
      if (response.getStatus() == Response.Status.OK.getStatusCode()) {
        groupDto = response.readEntity(EngineGroupDto.class);
      }
      response.close();
    } catch (Exception e) {
      String message = String.format(
        "Could not fetch group with id [%s] from engine with alias [%s]",
        groupId,
        engineAlias
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
    return Optional.ofNullable(groupDto)
      .map(group -> new GroupDto(
        group.getId(),
        group.getName(),
        getUserCountForUserGroup(group.getId()).orElse(null)
      ));
  }

  private Optional<Long> getUserCountForUserGroup(String userGroupId) {
    try {
      Response response = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .queryParam(MEMBER_OF_GROUP, userGroupId)
        .path(USER_COUNT_ENDPOINT)
        .request(MediaType.APPLICATION_JSON)
        .get();
      if (response.getStatus() == Response.Status.OK.getStatusCode()) {
        return Optional.of(response.readEntity(CountDto.class).getCount());
      }
      response.close();
    } catch (Exception e) {
      String message = String.format(
        "Could not get user count for user group [%s] from engine with alias [%s]",
        userGroupId,
        engineAlias
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
    return Optional.empty();
  }

  public List<GroupDto> fetchPageOfGroups(final int pageStartIndex, final int pageLimit) {
    Response response = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .queryParam(MAX_RESULTS_TO_RETURN, pageLimit)
      .queryParam(SORT_BY, "id")
      .queryParam(SORT_ORDER, SORT_ORDER_ASC)
      .queryParam(INDEX_OF_FIRST_RESULT, pageStartIndex)
      .path(GROUP_ENDPOINT)
      .request(MediaType.APPLICATION_JSON)
      .get();
    if (response.getStatus() == Response.Status.OK.getStatusCode()) {
      // @formatter:off
      return response.readEntity(new GenericType<List<EngineGroupDto>>() {}).stream()
        .map(engineGroupDto -> new GroupDto(engineGroupDto.getId(),
                                            engineGroupDto.getName(),
                                            getUserCountForUserGroup(engineGroupDto.getId()).orElse(null)))
        .collect(toList());
      // @formatter:on
    } else {
      final String message = String.format(
        "Failed querying groups from engine with alias [%s], response status: [%s].", response.getStatus(), engineAlias
      );
      response.close();
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }

  public List<GroupDto> getAllGroupsOfUser(String userId) {
    try {
      Response response = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .queryParam(MEMBER, userId)
        .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportGroupMaxPageSize())
        .path(GROUP_ENDPOINT)
        .request(MediaType.APPLICATION_JSON)
        .get();
      if (response.getStatus() == Response.Status.OK.getStatusCode()) {
        // @formatter:off
        return response.readEntity(new GenericType<List<EngineGroupDto>>() {})
          .stream()
          .map(engineGroupDto -> new GroupDto(engineGroupDto.getId(), engineGroupDto.getName()))
          .collect(toList());
        // @formatter:on
      }
    } catch (Exception e) {
      String message = String.format(
        "Could not fetch groups for user [%s] from engine with alias [%s]",
        userId,
        engineAlias
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
    return new ArrayList<>();
  }

  public List<AuthorizationDto> getAllApplicationAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_APPLICATION);
    } catch (Exception e) {
      String message = String.format(
        "Could not fetch application authorizations from the Engine with alias [%s] to check the access " +
          "permissions.",
        engineAlias
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message);
    }
  }

  public List<AuthorizationDto> getAllProcessDefinitionAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_PROCESS_DEFINITION);
    } catch (Exception e) {
      String message = String.format(
        "Could not fetch process definition authorizations from the Engine with alias [%s] to check the access " +
          "permissions.",
        engineAlias
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public List<AuthorizationDto> getAllDecisionDefinitionAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_DECISION_DEFINITION);
    } catch (Exception e) {
      String message = String.format(
        "Could not fetch decision definition authorizations from the Engine with alias [%s] to check the access " +
          "permissions.",
        engineAlias
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public List<AuthorizationDto> getAllTenantAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_TENANT);
    } catch (Exception e) {
      String message = String.format(
        "Could not fetch tenant authorizations from the Engine with alias [%s] to check the access " +
          "permissions.",
        engineAlias
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public List<AuthorizationDto> getAllGroupAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_GROUP);
    } catch (Exception e) {
      log.error(
        "Could not fetch group authorizations from the engine to check the access permissions.",
        e
      );
    }
    return new ArrayList<>();
  }

  public List<AuthorizationDto> getAllUserAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_USER);
    } catch (Exception e) {
      log.error(
        "Could not fetch user authorizations from the engine to check the access permissions.",
        e
      );
    }
    return new ArrayList<>();
  }

  private List<AuthorizationDto> getAuthorizationsForType(final int resourceType) {
    int pageSize = configurationService.getEngineImportAuthorizationMaxPageSize();
    List<AuthorizationDto> totalAuthorizations = new ArrayList<>();
    List<AuthorizationDto> pageOfAuthorizations;
    do {
      pageOfAuthorizations = new ArrayList<>();
      final Response response = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(AUTHORIZATION_ENDPOINT)
        .queryParam(RESOURCE_TYPE, resourceType)
        .queryParam(INDEX_OF_FIRST_RESULT, totalAuthorizations.size())
        .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
        .request(MediaType.APPLICATION_JSON)
        .get();
      if (response.getStatus() == Response.Status.OK.getStatusCode()) {
        // @formatter:off
        pageOfAuthorizations = response.readEntity(new GenericType<List<AuthorizationDto>>() {});
        totalAuthorizations.addAll(pageOfAuthorizations);
      // @formatter:on
      } else {
        if (log.isDebugEnabled()) {
          String message = String.format(
            "Could not fetch authorizations from engine with alias [%s]! Error from engine: %s",
            engineAlias,
            response.readEntity(String.class)
          );
          log.debug(message);
          throw new OptimizeRuntimeException(message);
        }
      }
      response.close();
    } while (pageOfAuthorizations.size() >= pageSize);
    return totalAuthorizations;
  }

}
