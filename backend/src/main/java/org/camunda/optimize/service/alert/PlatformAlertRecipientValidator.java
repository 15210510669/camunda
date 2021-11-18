/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.util.configuration.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Conditional(CamundaPlatformCondition.class)
@Component
public class PlatformAlertRecipientValidator implements AlertRecipientValidator {

  @Override
  public List<String> getValidatedRecipientEmailList(final List<String> emailAddresses) {
    if (emailAddresses.isEmpty()) {
      throw new OptimizeValidationException(
        "The field [emails] is not allowed to both be empty. At least one recipient must be set.");
    }
    return emailAddresses;
  }

}
