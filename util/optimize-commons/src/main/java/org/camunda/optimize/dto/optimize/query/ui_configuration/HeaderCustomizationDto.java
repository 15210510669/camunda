/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.ui_configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.util.configuration.ui.TextColorType;

@NoArgsConstructor
@Data
@EqualsAndHashCode
@AllArgsConstructor
public class HeaderCustomizationDto {

  private TextColorType textColor;
  private String backgroundColor;
  private String logo;


}
