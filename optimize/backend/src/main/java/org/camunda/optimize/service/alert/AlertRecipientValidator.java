/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import java.util.List;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;

public interface AlertRecipientValidator {

  void validateAlertRecipientEmailAddresses(List<String> emailAddresses)
      throws OptimizeValidationException;
}
