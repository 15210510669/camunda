/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.event;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@FieldNameConstants
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EventProcessMappingResponseDto {
  @EqualsAndHashCode.Include
  private String id;
  @NotBlank
  private String name;

  private String lastModifier;

  private OffsetDateTime lastModified;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String xml;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Valid
  private Map<String, EventMappingDto> mappings;

  private EventProcessState state;

  private Double publishingProgress;

  private List<EventSourceEntryResponseDto> eventSources = new ArrayList<>();

  public static EventProcessMappingResponseDto from(final EventProcessMappingDto dto,
                                                    Optional<String> lastModifierName,
                                                    List<EventSourceEntryResponseDto> eventSourcesDtos) {
    return EventProcessMappingResponseDto.builder()
      .id(dto.getId())
      .lastModified(dto.getLastModified())
      .lastModifier(lastModifierName.orElse(dto.getLastModifier()))
      .mappings(dto.getMappings())
      .name(dto.getName())
      .state(dto.getState())
      .publishingProgress(dto.getPublishingProgress())
      .xml(dto.getXml())
      .eventSources(eventSourcesDtos)
      .build();
  }
}
