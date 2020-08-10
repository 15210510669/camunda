/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.alert;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class AlertDefinitionDto extends AlertCreationDto {

  /**
   * Needed to inherit field name constants from {@link AlertCreationDto}
   */
  public static class Fields extends AlertCreationDto.Fields {}

  protected String id;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected boolean triggered;
}
