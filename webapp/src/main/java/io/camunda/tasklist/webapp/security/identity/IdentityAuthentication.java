/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.Permission;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;

public class IdentityAuthentication extends AbstractAuthenticationToken {

  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityAuthentication.class);

  private Tokens tokens;
  private String id;
  private String name;
  private List<String> permissions;
  private String subject;
  private Date expires;

  public IdentityAuthentication() {
    super(null);
  }

  @Override
  public String getCredentials() {
    return tokens.getAccessToken();
  }

  @Override
  public Object getPrincipal() {
    return subject;
  }

  public Tokens getTokens() {
    return tokens;
  }

  private boolean hasExpired() {
    return expires == null || expires.before(new Date());
  }

  private boolean hasRefreshTokenExpired() {
    final DecodedJWT refreshToken =
        getIdentity().authentication().decodeJWT(tokens.getRefreshToken());
    final Date refreshTokenExpiresAt = refreshToken.getExpiresAt();
    return refreshTokenExpiresAt == null || refreshTokenExpiresAt.before(new Date());
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      LOGGER.info("Access token is expired");
      if (hasRefreshTokenExpired()) {
        setAuthenticated(false);
        LOGGER.info("No refresh token available. Authentication is invalid.");
      } else {
        LOGGER.info("Get a new access token by using refresh token");
        try {
          renewAccessToken();
        } catch (Exception e) {
          LOGGER.error("Renewing access token failed with exception", e);
          setAuthenticated(false);
        }
      }
    }
    return super.isAuthenticated();
  }

  public String getId() {
    return id;
  }

  public List<Permission> getPermissions() {
    return permissions.stream()
        .map(PermissionConverter.getInstance()::convert)
        .collect(Collectors.toList());
  }

  public void authenticate(final Tokens tokens) {
    if (tokens != null) {
      this.tokens = tokens;
    }
    final AccessToken accessToken =
        getIdentity().authentication().verifyToken(this.tokens.getAccessToken());
    final UserDetails userDetails = accessToken.getUserDetails();
    name = retrieveName(userDetails);
    permissions = accessToken.getPermissions();
    if (!getPermissions().contains(Permission.READ)) {
      throw new InsufficientAuthenticationException("No read permissions");
    }
    subject = accessToken.getToken().getSubject();
    expires = accessToken.getToken().getExpiresAt();
    if (!hasExpired()) {
      setAuthenticated(true);
    }
  }

  private String retrieveName(final UserDetails userDetails) {
    // Fallback is UserDetails::name e.g 'Homer Simpson' otherwise UserDetails::id.
    final String name = userDetails.getName().orElse(userDetails.getId());
    // Get username like 'homer' otherwise name e.g. 'Homer Simpson' or id '234-ef-335...'
    return userDetails.getUsername().orElse(name);
  }

  private void renewAccessToken() {
    authenticate(renewTokens(tokens.getRefreshToken()));
  }

  private Tokens renewTokens(final String refreshToken) {
    return IdentityService.requestWithRetry(
        () -> getIdentity().authentication().renewToken(refreshToken));
  }

  private Identity getIdentity() {
    return SpringContextHolder.getBean(Identity.class);
  }

  public IdentityAuthentication setExpires(final Date expires) {
    this.expires = expires;
    return this;
  }

  public IdentityAuthentication setPermissions(final List<String> permissions) {
    this.permissions = permissions;
    return this;
  }
}
