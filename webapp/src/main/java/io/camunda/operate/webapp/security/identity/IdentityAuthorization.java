/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.authorizations.dto.Authorization;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class IdentityAuthorization implements Serializable {

  private static final long serialVersionUID = 1L;

  private String resourceKey;
  private String resourceType;
  private Set<String> permissions;

  public IdentityAuthorization setResourceKey(String resourceKey) {
    this.resourceKey = resourceKey;
    return this;
  }

  public IdentityAuthorization setResourceType(String resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public IdentityAuthorization setPermissions(Set<String> permissions) {
    // Copy the container so that it remains independent of any changes to the original
    this.permissions = new HashSet<>(permissions);
    return this;
  }

  public String getResourceKey() {
    return resourceKey;
  }

  public String getResourceType() {
    return resourceType;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public static IdentityAuthorization createFrom(Authorization authorization) {
    return new IdentityAuthorization()
        .setResourceKey(authorization.getResourceKey())
        .setResourceType(authorization.getResourceType())
        .setPermissions(authorization.getPermissions());
  }

  public static List<IdentityAuthorization> createFrom(List<Authorization> authorizations) {
    if (authorizations == null) {
      return new ArrayList<>();
    }
    return authorizations.stream()
        .filter(Objects::nonNull)
        .map(IdentityAuthorization::createFrom)
        .collect(Collectors.toList());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IdentityAuthorization that = (IdentityAuthorization) o;
    return Objects.equals(resourceKey, that.resourceKey)
        && Objects.equals(resourceType, that.resourceType)
        && Objects.equals(permissions, that.permissions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resourceKey, resourceType, permissions);
  }

  @Override
  public String toString() {
    return "IdentityAuthorization{"
        + "resourceKey='"
        + resourceKey
        + '\''
        + ", resourceType='"
        + resourceType
        + '\''
        + ", permissions="
        + permissions
        + '}';
  }
}
