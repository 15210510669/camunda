/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessRoleRestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingRestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventSourceEntryRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.EventProcessClient.createEventMappingsDto;
import static org.camunda.optimize.test.optimize.EventProcessClient.createMappedEventDto;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;

public class EventBasedProcessRestServiceIT extends AbstractEventProcessIT {

  private static String simpleDiagramXml;

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  private static Stream<Arguments> getAllEndpointsThatNeedEventAuthorization() {
    return Stream.of(
      Arguments.of(HttpMethod.GET, "/eventBasedProcess", null),
      Arguments.of(HttpMethod.GET, "/eventBasedProcess/someId/delete-conflicts", null),
      Arguments.of(HttpMethod.POST, "/eventBasedProcess", null),
      Arguments.of(
        HttpMethod.PUT, "/eventBasedProcess/someId", EventProcessMappingRequestDto.builder().name("someName").build()
      ),
      Arguments.of(HttpMethod.POST, "/eventBasedProcess/someId/_publish", null),
      Arguments.of(HttpMethod.POST, "/eventBasedProcess/someId/_cancelPublish", null),
      Arguments.of(HttpMethod.DELETE, "/eventBasedProcess/someId", null),
      Arguments.of(HttpMethod.GET, "/eventBasedProcess/someId/role", null),
      Arguments.of(
        HttpMethod.PUT,
        "/eventBasedProcess/someId/role",
        Collections.singleton(new EventProcessRoleRestDto(new UserDto("someId")))
      ),
      Arguments.of(HttpMethod.POST, "/eventBasedProcess/_mappingCleanup", EventMappingCleanupRequestDto.builder()
        .xml("<xml></xml>")
        .build()
      )
    );
  }

  @Test
  public void getIsEventBasedProcessesEnabled() {
    // when
    boolean isEnabled = eventProcessClient.getIsEventBasedProcessEnabled();

    // then
    assertThat(isEnabled).isEqualTo(true);
  }

  @Test
  public void getIsEventBasedProcessesEnabledWithFeatureDeactivatedReturnsFalse() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(false);

    // when
    boolean isEnabled = eventProcessClient.getIsEventBasedProcessEnabled();

    // then
    assertThat(isEnabled).isEqualTo(false);
  }

  @Test
  public void getIsEventBasedProcessesEnabledWithUserNotGrantedEventBasedProcessAccessReturnsFalse() {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getAuthorizedUserIds()
      .clear();

    // when
    boolean isEnabled = eventProcessClient.getIsEventBasedProcessEnabled();

    // then
    assertThat(isEnabled).isEqualTo(false);
  }

  @ParameterizedTest()
  @MethodSource("getAllEndpointsThatNeedEventAuthorization")
  public void callingEventBasedProcessApiWithFeatureDeactivatedReturnsForbidden(final String method,
                                                                                final String path,
                                                                                final Object payload) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(false);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGenericRequest(method, path, payload)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("getAllEndpointsThatNeedEventAuthorization")
  public void callingEventBasedProcessApiWithUserNotGrantedEventBasedProcessAccessReturnsForbidden(final String method,
                                                                                                   final String path,
                                                                                                   final Object payload) {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getAuthorizedUserIds()
      .clear();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGenericRequest(method, path, payload)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void createEventProcessMapping() {
    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(
        eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml)
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void createEventProcessMappingWithEventMappingCombinations() {
    // given event mappings with IDs existing in XML
    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(USER_TASK_ID_ONE, createEventMappingsDto(createMappedEventDto(), createMappedEventDto()));
    eventMappings.put(USER_TASK_ID_TWO, createEventMappingsDto(createMappedEventDto(), null));
    eventMappings.put(USER_TASK_ID_THREE, createEventMappingsDto(null, createMappedEventDto()));
    EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
        eventMappings, "process name", simpleDiagramXml
      );

    // when
    Response response = eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void createEventProcessMappingWithEventMappingIdNotExistInXml() {
    // given event mappings with ID does not exist in XML
    EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
        Collections.singletonMap("invalid_Id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
        "process name", simpleDiagramXml
      );

    // when
    Response response = eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createEventProcessMappingWithEventMappingsAndXmlNotPresent() {
    // given event mappings but no XML provided
    EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
        Collections.singletonMap(
          "some_task_id",
          createEventMappingsDto(createMappedEventDto(), createMappedEventDto())
        ),
        "process name",
        null
      );

    // when
    Response response = eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createEventProcessMappingWithNullStartAndEndEventMappings() {
    // given event mapping entry but neither start nor end is mapped
    EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
        Collections.singletonMap("some_task_id", createEventMappingsDto(null, null)),
        "process name", simpleDiagramXml
      );

    // when
    Response response = eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createEventProcessMappingWithInvalidEventMappings() {
    // given event mappings but mapped events have fields missing
    EventTypeDto invalidEventTypeDto = EventTypeDto.builder()
      .group(IdGenerator.getNextId())
      .source(IdGenerator.getNextId())
      .eventName(null)
      .build();
    invalidEventTypeDto.setGroup(null);
    EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
        Collections.singletonMap(USER_TASK_ID_ONE, createEventMappingsDto(invalidEventTypeDto, createMappedEventDto())),
        "process name", simpleDiagramXml
      );

    // when
    Response response = eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then a bad request exception is thrown
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventProcessMappingWithId() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    OffsetDateTime now = OffsetDateTime.parse("2019-11-25T10:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);
    String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    EventProcessMappingRestDto actual = eventProcessClient.getEventProcessMapping(expectedId);

    // then
    assertThat(actual.getId()).isEqualTo(expectedId);
    assertThat(actual).isEqualToIgnoringGivenFields(
      eventProcessMappingDto,
      EventProcessMappingDto.Fields.id,
      EventProcessMappingDto.Fields.lastModified,
      EventProcessMappingDto.Fields.lastModifier,
      EventProcessMappingDto.Fields.state,
      EventProcessMappingDto.Fields.roles,
      EventProcessMappingDto.Fields.eventSources
    )
      .extracting(EventProcessMappingDto.Fields.eventSources).asList()
      .hasSize(1)
      .containsExactly(convertToEventSourceRestEntryDto(eventProcessMappingDto.getEventSources().get(0)));
    assertThat(actual.getLastModified()).isEqualTo(now);
    assertThat(actual.getLastModifier()).isEqualTo("demo");
    assertThat(actual.getState()).isEqualTo(EventProcessState.MAPPED);
  }

  @Test
  public void getEventProcessMappingWithId_unmappedState() {
    // given
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient
      .buildEventProcessMappingDtoWithMappingsAndExternalEventSource(null, "process name", simpleDiagramXml);
    String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    EventProcessMappingRestDto actual = eventProcessClient.getEventProcessMapping(expectedId);

    // then the report is returned in state unmapped
    assertThat(actual.getState()).isEqualTo(EventProcessState.UNMAPPED);
  }

  @Test
  public void getEventProcessMappingWithIdNotExists() {
    // when
    Response response = eventProcessClient
      .createGetEventProcessMappingRequest(IdGenerator.getNextId()).execute();

    // then the report is returned with expect
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void getAllEventProcessMappings() {
    // given
    final Map<String, EventMappingDto> firstProcessMappings = Collections.singletonMap(
      USER_TASK_ID_THREE,
      createEventMappingsDto(createMappedEventDto(), createMappedEventDto())
    );
    EventProcessMappingDto firstExpectedDto = eventProcessClient
      .buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
        firstProcessMappings, "process name", simpleDiagramXml
      );
    OffsetDateTime now = OffsetDateTime.parse("2019-11-25T10:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);
    String firstExpectedId = eventProcessClient.createEventProcessMapping(firstExpectedDto);
    EventProcessMappingDto secondExpectedDto = eventProcessClient
      .buildEventProcessMappingDto(simpleDiagramXml);
    String secondExpectedId = eventProcessClient.createEventProcessMapping(secondExpectedDto);

    // when
    List<EventProcessMappingDto> response = eventProcessClient.getAllEventProcessMappings();

    // then the response contains expected processes with xml omitted
    assertThat(response).extracting(
      EventProcessMappingDto.Fields.id, EventProcessMappingDto.Fields.name,
      EventProcessMappingDto.Fields.xml, EventProcessMappingDto.Fields.lastModified,
      EventProcessMappingDto.Fields.lastModifier, EventProcessMappingDto.Fields.mappings,
      EventProcessMappingDto.Fields.state
    )
      .containsExactlyInAnyOrder(
        tuple(
          firstExpectedId,
          firstExpectedDto.getName(),
          null,
          now,
          "demo",
          firstProcessMappings,
          EventProcessState.MAPPED
        ),
        tuple(secondExpectedId, secondExpectedDto.getName(), null, now, "demo", null, EventProcessState.UNMAPPED)
      );
  }

  @Test
  public void updateEventProcessMappingWithMappingsAdded() {
    // given
    OffsetDateTime createdTime = OffsetDateTime.parse("2019-11-24T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(createdTime);
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml)
    );

    // when
    EventProcessMappingDto updateDto = eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      Collections.singletonMap(
        USER_TASK_ID_THREE, createEventMappingsDto(createMappedEventDto(), createMappedEventDto())
      ),
      "new process name",
      simpleDiagramXml
    );
    OffsetDateTime updatedTime = OffsetDateTime.parse("2019-11-25T10:00:00+01:00");
    LocalDateUtil.setCurrentTime(updatedTime);
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(storedEventProcessMappingId, updateDto).execute();

    // then the update response code is correct
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // then the fields have been updated
    EventSourceEntryDto eventSourceEntry = updateDto.getEventSources().get(0);
    EventProcessMappingRestDto storedDto = eventProcessClient.getEventProcessMapping(storedEventProcessMappingId);
    assertThat(storedDto)
      .isEqualToIgnoringGivenFields(
        updateDto,
        EventProcessMappingDto.Fields.id,
        EventProcessMappingDto.Fields.lastModified,
        EventProcessMappingDto.Fields.lastModifier,
        EventProcessMappingDto.Fields.state,
        EventProcessMappingDto.Fields.roles,
        EventProcessMappingDto.Fields.eventSources
      )
      .extracting("id").isEqualTo(storedEventProcessMappingId);
    assertThat(storedDto.getLastModified()).isEqualTo(updatedTime);
    assertThat(storedDto.getLastModifier()).isEqualTo("demo");
    assertThat(storedDto.getEventSources())
      .hasSize(1)
      .extracting(
        EventSourceEntryRestDto::getId,
        EventSourceEntryRestDto::getType,
        EventSourceEntryRestDto::getEventScope
      )
      .containsExactly(Tuple.tuple(
        eventSourceEntry.getId(),
        eventSourceEntry.getType(),
        eventSourceEntry.getEventScope()
      ));
  }

  @Test
  public void updateEventProcessMappingWithIdNotExists() {
    // when
    Response response = eventProcessClient.createUpdateEventProcessMappingRequest(
      "doesNotExist",
      eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml)
    ).execute();

    // then the report is returned with expect
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void updateEventProcessMappingWithEventMappingIdNotExistInXml() {
    // given
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml)
    );

    // when update event mappings with ID does not exist in XML
    EventProcessMappingDto updateDto = eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      Collections.singletonMap("invalid_Id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name", simpleDiagramXml
    );
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(storedEventProcessMappingId, updateDto).execute();

    // then the update response code is correct
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateEventProcessMappingWithInvalidEventMappings() {
    // given existing event based process
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml)
    );
    EventTypeDto invalidEventTypeDto = EventTypeDto.builder()
      .group(IdGenerator.getNextId())
      .source(IdGenerator.getNextId())
      .eventName(null)
      .build();

    // when update event mappings with a mapped event with missing fields
    EventProcessMappingDto updateDto = eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      Collections.singletonMap(USER_TASK_ID_THREE, createEventMappingsDto(invalidEventTypeDto, createMappedEventDto())),
      "process name",
      simpleDiagramXml
    );
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(storedEventProcessMappingId, updateDto)
      .execute();

    // then a bad request exception is thrown
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateEventProcessMappingWithEventMappingAndNoXmlPresent() {
    // given
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.buildEventProcessMappingDto(null)
    );

    // when update event mappings and no XML present
    EventProcessMappingDto updateDto = eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      Collections.singletonMap("some_task_id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      null
    );
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(storedEventProcessMappingId, updateDto)
      .execute();

    // then the update response code is correct
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void publishMappedEventProcessMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    final EventProcessMappingRestDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
      eventProcessId
    );

    // then
    assertThat(storedEventProcessMapping.getState()).isEqualTo(EventProcessState.PUBLISH_PENDING);
    assertThat(storedEventProcessMapping.getPublishingProgress()).isEqualTo(0.0D);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).get()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(storedEventProcessMapping.getId())
          .name(storedEventProcessMapping.getName())
          .publishDateTime(LocalDateUtil.getCurrentDateTime())
          .state(EventProcessState.PUBLISH_PENDING)
          .publishProgress(0.0D)
          .xml(storedEventProcessMapping.getXml())
          .mappings(eventProcessMappingDto.getMappings())
          .deleted(false).build(),
        EventProcessPublishStateDto.Fields.id, EventProcessPublishStateDto.Fields.eventImportSources
      )
      .extracting(EventProcessPublishStateDto::getEventImportSources).asList()
      .hasSize(1)
      .containsExactly(EventImportSourceDto.builder()
                         .firstEventForSourceAtTimeOfPublishTimestamp(OffsetDateTime.ofInstant(
                           Instant.ofEpochMilli(0L),
                           ZoneId.systemDefault()
                         ))
                         .lastEventForSourceAtTimeOfPublishTimestamp(OffsetDateTime.ofInstant(
                           Instant.ofEpochMilli(0L),
                           ZoneId.systemDefault()
                         ))
                         .lastImportedEventTimestamp(OffsetDateTime.ofInstant(
                           Instant.ofEpochMilli(0L),
                           ZoneId.systemDefault()
                         ))
                         .eventSource(convertToEventSourceEntryDto(storedEventProcessMapping.getEventSources().get(0)))
                         .build());
  }

  @Test
  public void publishUnpublishedChangesEventProcessMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    final EventProcessMappingDto updateDto = eventProcessClient
      .buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
        eventProcessMappingDto.getMappings(), "new process name", simpleDiagramXml
      );
    eventProcessClient.updateEventProcessMapping(eventProcessId, updateDto);

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusSeconds(1));
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    final EventProcessMappingRestDto republishedEventProcessMapping = eventProcessClient.getEventProcessMapping(
      eventProcessId
    );

    // then
    assertThat(republishedEventProcessMapping.getState()).isEqualTo(EventProcessState.PUBLISH_PENDING);
    assertThat(republishedEventProcessMapping.getPublishingProgress()).isEqualTo(0.0D);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.xml, updateDto.getXml())
      .hasFieldOrPropertyWithValue(
        EventProcessPublishStateDto.Fields.publishDateTime,
        LocalDateUtil.getCurrentDateTime()
      );
  }

  @NonNull
  private OffsetDateTime getPublishedDateForEventProcessMappingOrFail(final String eventProcessId) {
    return getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Failed reading first publish date"))
      .getPublishDateTime();
  }

  @Test
  public void publishUnmappedEventProcessMapping_fails() {
    // given
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient
      .buildEventProcessMappingDtoWithMappingsAndExternalEventSource(null, "unmapped", simpleDiagramXml);
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createPublishEventProcessMappingRequest(eventProcessId)
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    final EventProcessMappingRestDto actual = eventProcessClient.getEventProcessMapping(eventProcessId);

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidEventProcessState");

    assertThat(actual.getState()).isEqualTo(EventProcessState.UNMAPPED);
    assertThat(actual.getPublishingProgress()).isEqualTo(null);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).isEmpty();
  }

  @Test
  public void publishPublishPendingEventProcessMapping_fails() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessId);
    final OffsetDateTime firstPublishDate = getPublishedDateForEventProcessMappingOrFail(eventProcessId);

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createPublishEventProcessMappingRequest(eventProcessId)
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    final EventProcessMappingRestDto actual = eventProcessClient.getEventProcessMapping(eventProcessId);

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidEventProcessState");

    assertThat(actual.getState()).isEqualTo(EventProcessState.PUBLISH_PENDING);
    assertThat(actual.getPublishingProgress()).isEqualTo(0.0D);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishDateTime, firstPublishDate);
  }

  @Test
  public void publishedEventProcessMapping_failsIfNotExists() {
    // given

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createPublishEventProcessMappingRequest("notExistingId")
      .execute(ErrorResponseDto.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("notFoundError");
  }

  @Test
  public void cancelPublishPendingEventProcessMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessId);

    // when
    eventProcessClient.cancelPublishEventProcessMapping(eventProcessId);

    final EventProcessMappingRestDto actual = eventProcessClient.getEventProcessMapping(eventProcessId);

    // then
    assertThat(actual.getState()).isEqualTo(EventProcessState.MAPPED);
    assertThat(actual.getPublishingProgress()).isEqualTo(null);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).isEmpty();
  }

  @Test
  public void cancelPublishUnmappedEventProcessMapping_fails() {
    // given
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient
      .buildEventProcessMappingDtoWithMappingsAndExternalEventSource(null, "unmapped", simpleDiagramXml);
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createPublishEventProcessMappingRequest(eventProcessId)
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidEventProcessState");
  }

  @Test
  public void cancelPublishMappedEventProcessMapping_fails() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createCancelPublishEventProcessMappingRequest(eventProcessId)
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidEventProcessState");
  }

  @Test
  public void cancelPublishedEventProcessMapping_failsIfNotExists() {
    // given

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createCancelPublishEventProcessMappingRequest("notExistingId")
      .execute(ErrorResponseDto.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("notFoundError");
  }

  @Test
  public void getDeleteConflictsForEventProcessMappingWithoutAuthorization() {
    // when
    Response response = eventProcessClient
      .createGetDeleteConflictsForEventProcessMappingRequest("doesNotMatter")
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getDeleteConflictsForEventProcessMappingReturnsOnlyConflictedItems() {
    // given a published event process with various dependent resources created using its definition
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey);

    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(PROCESS, eventProcessDefinitionKey)
    );

    String conflictedReportId = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        eventProcessDefinitionKey,
        Collections.emptyList()
      ));
    String nonConflictedReportId = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        DEFAULT_DEFINITION_KEY,
        Collections.emptyList()
      ));
    String conflictedCombinedReportId =
      reportClient.createCombinedReport(collectionId, Arrays.asList(conflictedReportId, nonConflictedReportId));

    String conflictedAlertId = alertClient.createAlertForReport(conflictedReportId);
    alertClient.createAlertForReport(nonConflictedReportId);

    String conflictedDashboardId = dashboardClient.createDashboard(
      collectionId, Arrays.asList(conflictedReportId, nonConflictedReportId)
    );

    // when
    ConflictResponseDto conflictResponseDto =
      eventProcessClient.getDeleteConflictsForEventProcessMapping(eventProcessDefinitionKey);

    // then
    assertThat(conflictResponseDto.getConflictedItems())
      .extracting(ConflictedItemDto.Fields.id, ConflictedItemDto.Fields.type)
      .containsExactlyInAnyOrder(
        new Tuple(conflictedReportId, ConflictedItemType.REPORT),
        new Tuple(conflictedCombinedReportId, ConflictedItemType.COMBINED_REPORT),
        new Tuple(conflictedDashboardId, ConflictedItemType.DASHBOARD),
        new Tuple(conflictedAlertId, ConflictedItemType.ALERT)
      );
  }

  @Test
  public void deleteEventProcessMapping() {
    // given
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml)
    );

    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest(storedEventProcessMappingId).execute();

    // then the delete response code is correct
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // then the process no longer exists
    Response getResponse = eventProcessClient
      .createGetEventProcessMappingRequest(storedEventProcessMappingId).execute();
    assertThat(getResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void deletePublishedEventProcessMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessId);

    // when
    eventProcessClient.deleteEventProcessMapping(eventProcessId);

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).isEmpty();
  }

  @Test
  public void deletePublishedEventProcessMappingDependentResourcesGetCleared() {
    // given a published event process with various dependent resources created using its definition
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey);

    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(PROCESS, eventProcessDefinitionKey)
    );

    String reportWithEventProcessDefKey = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        eventProcessDefinitionKey,
        Collections.emptyList()
      ));
    String reportIdWithDefaultDefKey = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        DEFAULT_DEFINITION_KEY,
        Collections.emptyList()
      ));
    String reportIdWithNoDefKey = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(collectionId, null, Collections.emptyList()));
    reportClient.createCombinedReport(
      collectionId,
      Arrays.asList(reportWithEventProcessDefKey, reportIdWithDefaultDefKey)
    );

    alertClient.createAlertForReport(reportWithEventProcessDefKey);
    String alertIdToRemain = alertClient.createAlertForReport(reportIdWithDefaultDefKey);

    String dashboardId = dashboardClient.createDashboard(
      collectionId, Arrays.asList(reportWithEventProcessDefKey, reportIdWithDefaultDefKey, reportIdWithNoDefKey)
    );

    // when the event process is deleted
    eventProcessClient.deleteEventProcessMapping(eventProcessDefinitionKey);

    // then the event process is deleted and the associated resources are cleaned up
    eventProcessClient.createGetEventProcessMappingRequest(eventProcessDefinitionKey)
      .execute(Response.Status.NOT_FOUND.getStatusCode());
    assertThat(collectionClient.getReportsForCollection(collectionId))
      .extracting("definitionDto.id")
      .containsExactlyInAnyOrder(reportIdWithDefaultDefKey, reportIdWithNoDefKey);
    assertThat(alertClient.getAlertsForCollectionAsDefaultUser(collectionId))
      .extracting("id")
      .containsExactly(alertIdToRemain);
    assertThat(getAllCollectionDefinitions())
      .hasSize(1)
      .extracting("data.scope")
      .contains(Collections.singletonList(new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY)));
    assertThat(dashboardClient.getDashboard(dashboardId).getReports())
      .extracting("id")
      .containsExactlyInAnyOrder(reportIdWithDefaultDefKey, reportIdWithNoDefKey);
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKey)).isEmpty();
  }

  @Test
  public void deleteEventProcessMappingNotExists() {
    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest("doesNotMatter")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  protected EventSourceEntryRestDto convertToEventSourceRestEntryDto(EventSourceEntryDto eventSourceEntry) {
    return EventSourceEntryRestDto.builder()
      .id(eventSourceEntry.getId())
      .type(eventSourceEntry.getType())
      .eventScope(eventSourceEntry.getEventScope())
      .processDefinitionName(null)
      .processDefinitionKey(eventSourceEntry.getProcessDefinitionKey())
      .versions(eventSourceEntry.getVersions())
      .tenants(eventSourceEntry.getTenants())
      .tracedByBusinessKey(eventSourceEntry.getTracedByBusinessKey())
      .traceVariable(eventSourceEntry.getTraceVariable())
      .build();
  }

  private EventProcessMappingDto createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource() {
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      Collections.singletonMap(
        USER_TASK_ID_THREE,
        createEventMappingsDto(
          createMappedEventDto(),
          createMappedEventDto()
        )
      ),
      "process name",
      simpleDiagramXml
    );
  }

  @SneakyThrows
  public static String createProcessDefinitionXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent("startEvent_ID")
      .userTask(USER_TASK_ID_ONE)
      .userTask(USER_TASK_ID_TWO)
      .userTask(USER_TASK_ID_THREE)
      .endEvent("endEvent_ID")
      .done();
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModel);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }

  @SneakyThrows
  private List<CollectionDefinitionDto> getAllCollectionDefinitions() {
    final SearchResponse searchResponse = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(
      COLLECTION_INDEX_NAME);
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return elasticSearchIntegrationTestExtension.getObjectMapper()
            .readValue(doc.getSourceAsString(), CollectionDefinitionDto.class);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.toList());
  }

}
