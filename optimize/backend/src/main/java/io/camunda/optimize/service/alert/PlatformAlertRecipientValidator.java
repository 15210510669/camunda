/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.alert;

import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(CamundaPlatformCondition.class)
@Component
public class PlatformAlertRecipientValidator implements AlertRecipientValidator {

  @Override
  public void validateAlertRecipientEmailAddresses(final List<String> emailAddresses) {
    if (emailAddresses.isEmpty()) {
      throw new OptimizeValidationException(
          "The field [emails] is not allowed to be empty. At least one recipient must be set.");
    }
  }
}
