/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@FieldNameConstants()
public class EventDto implements OptimizeDto, EventProcessEventDto {
  @NotBlank
  @EqualsAndHashCode.Include
  @ToString.Include
  private String id;
  @NotBlank
  @ToString.Include
  private String eventName;
  @NotNull
  @Min(0)
  @ToString.Include
  private Long timestamp;
  @ToString.Include
  private Long ingestionTimestamp;
  @NotBlank
  @ToString.Include
  private String traceId;
  @ToString.Include
  private String group;
  @ToString.Include
  private String source;
  private Object data;

}
