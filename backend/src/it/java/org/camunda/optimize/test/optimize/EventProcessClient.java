/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessRoleDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessRoleRestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.service.util.IdGenerator;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;

@AllArgsConstructor
public class EventProcessClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public boolean getIsEventBasedProcessEnabled() {
    return getRequestExecutor()
      .buildGetIsEventProcessEnabledRequest()
      .execute(Boolean.class, Response.Status.OK.getStatusCode());
  }

  public OptimizeRequestExecutor createCreateEventProcessMappingRequest(final EventProcessMappingDto eventProcessMappingDto) {
    return getRequestExecutor().buildCreateEventProcessMappingRequest(toEventProcessMappingCreateRequestDto(eventProcessMappingDto));
  }

  public String createEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    return createCreateEventProcessMappingRequest(eventProcessMappingDto).execute(
      IdDto.class,
      Response.Status.OK.getStatusCode()
    ).getId();
  }

  public OptimizeRequestExecutor createGetEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildGetEventProcessMappingRequest(eventProcessMappingId);
  }

  public EventProcessMappingResponseDto getEventProcessMapping(final String eventProcessMappingId) {
    return createGetEventProcessMappingRequest(eventProcessMappingId).execute(
      EventProcessMappingResponseDto.class,
      Response.Status.OK.getStatusCode()
    );
  }

  private OptimizeRequestExecutor createGetAllEventProcessMappingsRequest() {
    return getRequestExecutor().buildGetAllEventProcessMappingsRequests();
  }

  private OptimizeRequestExecutor createGetAllEventProcessMappingsRequest(final String userId, final String pw) {
    return getRequestExecutor().withUserAuthentication(userId, pw).buildGetAllEventProcessMappingsRequests();
  }

  public List<EventProcessMappingDto> getAllEventProcessMappings() {
    return createGetAllEventProcessMappingsRequest()
      .executeAndReturnList(EventProcessMappingDto.class, Response.Status.OK.getStatusCode());
  }

  public List<EventProcessMappingDto> getAllEventProcessMappings(final String userId, final String pw) {
    return createGetAllEventProcessMappingsRequest(userId, pw)
      .executeAndReturnList(EventProcessMappingDto.class, Response.Status.OK.getStatusCode());
  }

  public OptimizeRequestExecutor createUpdateEventProcessMappingRequest(final String eventProcessMappingId,
                                                                        final EventProcessMappingDto eventProcessMappingDto) {
    return getRequestExecutor()
      .buildUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMappingDto);
  }

  public void updateEventProcessMapping(final String eventProcessMappingId, final EventProcessMappingDto updateDto) {
    createUpdateEventProcessMappingRequest(
      eventProcessMappingId,
      updateDto
    ).execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public OptimizeRequestExecutor createPublishEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildPublishEventProcessMappingRequest(eventProcessMappingId);
  }

  public void publishEventProcessMapping(final String eventProcessMappingId) {
    createPublishEventProcessMappingRequest(eventProcessMappingId).execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public OptimizeRequestExecutor createCancelPublishEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor()
      .buildCancelPublishEventProcessMappingRequest(eventProcessMappingId);
  }

  public void cancelPublishEventProcessMapping(final String eventProcessMappingId) {
    createCancelPublishEventProcessMappingRequest(eventProcessMappingId).execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public OptimizeRequestExecutor createDeleteEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildDeleteEventProcessMappingRequest(eventProcessMappingId);
  }

  public ConflictResponseDto getDeleteConflictsForEventProcessMapping(String eventProcessMappingId) {
    return createGetDeleteConflictsForEventProcessMappingRequest(eventProcessMappingId)
      .execute(ConflictResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public OptimizeRequestExecutor createGetDeleteConflictsForEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildGetDeleteConflictsForEventProcessMappingRequest(eventProcessMappingId);
  }

  public void deleteEventProcessMapping(final String eventProcessMappingId) {
    createDeleteEventProcessMappingRequest(eventProcessMappingId).execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public EventProcessMappingDto buildEventProcessMappingDto(final String xml) {
    return buildEventProcessMappingDto(null, xml);
  }

  public EventProcessMappingDto buildEventProcessMappingDto(final String name, final String xml) {
    return buildEventProcessMappingDtoWithMappingsAndExternalEventSource(null, name, xml);
  }

  public List<EventProcessRoleRestDto> getEventProcessMappingRoles(final String eventProcessMappingId) {
    return createGetEventProcessMappingRolesRequest(eventProcessMappingId)
      .execute(new TypeReference<List<EventProcessRoleRestDto>>() {
      });
  }

  public OptimizeRequestExecutor createGetEventProcessMappingRolesRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildGetEventProcessMappingRolesRequest(eventProcessMappingId);
  }

  public void updateEventProcessMappingRoles(final String eventProcessMappingId,
                                             final List<EventProcessRoleDto<IdentityDto>> roleRestDtos) {
    createUpdateEventProcessMappingRolesRequest(eventProcessMappingId, roleRestDtos)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public OptimizeRequestExecutor createUpdateEventProcessMappingRolesRequest(final String eventProcessMappingId,
                                                                             final List<EventProcessRoleDto<IdentityDto>> roleRestDtos) {
    return getRequestExecutor().buildUpdateEventProcessRolesRequest(eventProcessMappingId, roleRestDtos);
  }

  @SneakyThrows
  public EventProcessMappingDto buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
    final Map<String, EventMappingDto> flowNodeEventMappingsDto,
    final String name,
    final String xml) {
    List<EventSourceEntryDto> externalEventSource = new ArrayList<>();
    externalEventSource.add(createExternalEventSourceEntry());
    return buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
      flowNodeEventMappingsDto,
      name,
      xml,
      externalEventSource
    );
  }

  @SneakyThrows
  public EventProcessMappingDto buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
    final Map<String, EventMappingDto> flowNodeEventMappingsDto, final String name, final String xml,
    final List<EventSourceEntryDto> eventSources) {
    return EventProcessMappingDto.builder()
      .name(Optional.ofNullable(name).orElse(RandomStringUtils.randomAlphanumeric(10)))
      .mappings(flowNodeEventMappingsDto)
      .eventSources(eventSources)
      .xml(xml)
      .build();
  }

  public Map<String, EventMappingDto> cleanupEventProcessMappings(final EventMappingCleanupRequestDto cleanupRequestDto) {
    return createCleanupEventProcessMappingsRequest(cleanupRequestDto)
      // @formatter:off
      .execute(new TypeReference<Map<String, EventMappingDto>>() {});
    // @formatter:on
  }

  public OptimizeRequestExecutor createCleanupEventProcessMappingsRequest(final EventMappingCleanupRequestDto cleanupRequestDto) {
    return getRequestExecutor().buildCleanupEventProcessMappingRequest(cleanupRequestDto);
  }

  public static EventSourceEntryDto createExternalEventSourceEntry() {
    return EventSourceEntryDto.builder()
      .type(EventSourceType.EXTERNAL)
      .eventScope(Collections.singletonList(EventScopeType.ALL))
      .build();
  }

  public static EventSourceEntryDto createSimpleCamundaEventSourceEntry(final String processDefinitionKey) {
    return createSimpleCamundaEventSourceEntryWithTenant(processDefinitionKey, null);
  }

  public static EventSourceEntryDto createSimpleCamundaEventSourceEntryWithTenant(final String processDefinitionKey,
                                                                                  final String tenantId) {
    return createSimpleCamundaEventSourceEntry(processDefinitionKey, ALL_VERSIONS, tenantId);
  }

  public static EventSourceEntryDto createSimpleCamundaEventSourceEntry(final String processDefinitionKey,
                                                                        final String version) {
    return createSimpleCamundaEventSourceEntry(processDefinitionKey, version, null);
  }

  public static EventSourceEntryDto createSimpleCamundaEventSourceEntry(final String processDefinitionKey,
                                                                        final String version,
                                                                        final String tenantId) {
    return EventSourceEntryDto.builder()
      .processDefinitionKey(processDefinitionKey)
      .versions(ImmutableList.of(version))
      .tenants(Lists.newArrayList(tenantId))
      .tracedByBusinessKey(true)
      .type(EventSourceType.CAMUNDA)
      .eventScope(Collections.singletonList(EventScopeType.ALL))
      .build();
  }

  public static EventMappingDto createEventMappingsDto(EventTypeDto startEventDto, EventTypeDto endEventDto) {
    return EventMappingDto.builder()
      .start(startEventDto)
      .end(endEventDto)
      .build();
  }

  public static EventTypeDto createMappedEventDto() {
    return EventTypeDto.builder()
      .group(IdGenerator.getNextId())
      .source(IdGenerator.getNextId())
      .eventName(IdGenerator.getNextId())
      .build();
  }

  private EventProcessMappingCreateRequestDto toEventProcessMappingCreateRequestDto(final EventProcessMappingDto eventProcessMappingDto) {
    return EventProcessMappingCreateRequestDto.eventProcessMappingCreateBuilder()
      .name(eventProcessMappingDto.getName())
      .eventSources(eventProcessMappingDto.getEventSources())
      .mappings(eventProcessMappingDto.getMappings())
      .xml(eventProcessMappingDto.getXml())
      .build();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
