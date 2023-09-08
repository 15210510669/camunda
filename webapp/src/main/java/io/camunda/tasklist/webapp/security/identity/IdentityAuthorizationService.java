/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.sso.TokenAuthentication;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class IdentityAuthorizationService {

  @Autowired private TasklistProperties tasklistProperties;

  public List<String> getProcessDefinitionsFromAuthorization() {
    if (tasklistProperties.getIdentity().isResourcePermissionsEnabled()
        && tasklistProperties.getIdentity().getBaseUrl() != null) {
      final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof IdentityAuthentication) {
        final IdentityAuthentication identityAuthentication =
            (IdentityAuthentication) authentication;
        return identityAuthentication.getAuthorizations().getProcessesAllowedToStart();
      } else if (authentication instanceof JwtAuthenticationToken) {
        final JwtAuthenticationToken jwtAuthenticationToken =
            (JwtAuthenticationToken) authentication;
        final Identity identity = SpringContextHolder.getBean(Identity.class);
        return new IdentityAuthorization(
                identity
                    .authorizations()
                    .forToken(jwtAuthenticationToken.getToken().getTokenValue()))
            .getProcessesAllowedToStart();
      } else if (authentication instanceof TokenAuthentication) {
        final Identity identity = SpringContextHolder.getBean(Identity.class);
        return new IdentityAuthorization(
                identity
                    .authorizations()
                    .forToken(
                        ((TokenAuthentication) authentication).getAccessToken(),
                        ((TokenAuthentication) authentication).getOrganization()))
            .getProcessesAllowedToStart();
      }
    }
    final List<String> result = new ArrayList<String>();
    result.add("*");
    return result;
  }
}
