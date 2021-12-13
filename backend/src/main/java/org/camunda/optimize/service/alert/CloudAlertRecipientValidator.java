/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.rest.cloud.CloudUserClient;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.identity.CloudUserIdentityCacheService;
import org.camunda.optimize.service.util.configuration.CamundaCloudCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Conditional(CamundaCloudCondition.class)
@Component
@RequiredArgsConstructor
public class CloudAlertRecipientValidator implements AlertRecipientValidator {

  private final CloudUserIdentityCacheService cloudUserIdentityCacheService;
  private final CloudUserClient cloudUserClient;

  @Override
  public List<String> getValidatedRecipientEmailList(final List<String> emails) {
    final List<String> cachedUserEmails = cloudUserIdentityCacheService.getUsersByEmail(emails)
      .stream().map(UserDto::getEmail).collect(Collectors.toList());
    final Collection<String> uncachedUserEmails = CollectionUtils.subtract(emails, cachedUserEmails);

    // If the user has supplied an ID rather than an email address, we can still fetch it directly
    final Map<String, String> fetchedEmailsByUserId = uncachedUserEmails.stream()
      .map(cloudUserClient::getCloudUserForId)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toMap(CloudUserDto::getUserId, CloudUserDto::getEmail));
    if (uncachedUserEmails.size() > fetchedEmailsByUserId.size()) {
      throw new OptimizeValidationException(
        "Users with the following email addresses are not available for receiving alerts: "
          + CollectionUtils.subtract(uncachedUserEmails, fetchedEmailsByUserId.values()));
    }
    return emails.stream()
      .map(emailAddress -> fetchedEmailsByUserId.getOrDefault(emailAddress, emailAddress))
      .collect(Collectors.toList());
  }

}
