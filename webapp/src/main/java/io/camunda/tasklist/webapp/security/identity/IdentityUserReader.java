/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.oauth.IdentityTenantAwareJwtAuthenticationToken;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityUserReader implements UserReader {

  @Autowired private Identity identity;

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    if (authentication instanceof IdentityAuthentication identityAuthentication) {
      return Optional.of(
          new UserDTO()
              // For testing assignee migration locally use 'identityAuthentication.getId()'
              .setUserId(/*identityAuthentication.getId()*/ identityAuthentication.getName())
              .setDisplayName(identityAuthentication.getUserDisplayName())
              .setPermissions(identityAuthentication.getPermissions())
              .setTenants(identityAuthentication.getTenants())
              .setGroups(identityAuthentication.getGroups()));
    } else if (authentication
        instanceof IdentityTenantAwareJwtAuthenticationToken identityTenantAwareToken) {
      final AccessToken accessToken =
          identity
              .authentication()
              .verifyToken(((Jwt) identityTenantAwareToken.getPrincipal()).getTokenValue());
      final List<Permission> permissions =
          accessToken.getPermissions().stream()
              .map(PermissionConverter.getInstance()::convert)
              .collect(Collectors.toList());

      final String userDisplayName =
          accessToken.getUserDetails().getName().orElse(identityTenantAwareToken.getName());

      return Optional.of(
          new UserDTO()
              .setUserId(identityTenantAwareToken.getName())
              .setDisplayName(userDisplayName)
              .setApiUser(true)
              .setPermissions(permissions)
              .setTenants(identityTenantAwareToken.getTenants()));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
    return map(usernames, name -> new UserDTO().setUserId(name).setDisplayName(name));
  }
}
