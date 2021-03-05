/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34.indices;

import org.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessPublishStateDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.migrate33To34.dto.EventSourceEntryDtoOld;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class EventProcessPublishStateIndexV3Old extends DefaultIndexMappingCreator {

  public static final int VERSION = 3;

  public static final String ID = EsEventProcessPublishStateDto.Fields.id;
  public static final String PROCESS_MAPPING_ID = EsEventProcessPublishStateDto.Fields.processMappingId;
  public static final String NAME = EsEventProcessPublishStateDto.Fields.name;
  public static final String PUBLISH_DATE_TIME = EsEventProcessPublishStateDto.Fields.publishDateTime;
  public static final String STATE = EsEventProcessPublishStateDto.Fields.state;
  public static final String PUBLISH_PROGRESS = EsEventProcessPublishStateDto.Fields.publishProgress;
  public static final String DELETED = EsEventProcessPublishStateDto.Fields.deleted;
  public static final String XML = EsEventProcessPublishStateDto.Fields.xml;
  public static final String MAPPINGS = EsEventProcessPublishStateDto.Fields.mappings;
  public static final String EVENT_IMPORT_SOURCES = EsEventProcessPublishStateDto.Fields.eventImportSources;

  public static final String FLOWNODE_ID = EsEventMappingDto.Fields.flowNodeId;
  public static final String START = EsEventMappingDto.Fields.start;
  public static final String END = EsEventMappingDto.Fields.end;

  public static final String GROUP = EventTypeDto.Fields.group;
  public static final String SOURCE = EventTypeDto.Fields.source;
  public static final String EVENT_NAME = EventTypeDto.Fields.eventName;
  public static final String EVENT_LABEL = EventTypeDto.Fields.eventLabel;

  public static final String FIRST_EVENT_FOR_IMPORT_SOURCE_TIMESTAMP =
    EventImportSourceDto.Fields.firstEventForSourceAtTimeOfPublishTimestamp;
  public static final String PUBLISH_COMPLETED_TIMESTAMP =
    EventImportSourceDto.Fields.lastEventForSourceAtTimeOfPublishTimestamp;
  public static final String LAST_IMPORT_EXECUTION_TIMESTAMP = EventImportSourceDto.Fields.lastImportExecutionTimestamp;
  public static final String LAST_IMPORTED_EVENT_TIMESTAMP = EventImportSourceDto.Fields.lastImportedEventTimestamp;
  public static final String EVENT_SOURCE = "eventSource";

  public static final String EVENT_SOURCE_ID = EventSourceEntryDtoOld.Fields.id;
  public static final String EVENT_SOURCE_TYPE = EventSourceEntryDtoOld.Fields.type;
  public static final String EVENT_SOURCE_EVENT_SCOPE = EventSourceEntryDtoOld.Fields.eventScope;
  public static final String EVENT_SOURCE_PROC_DEF_KEY = EventSourceEntryDtoOld.Fields.processDefinitionKey;
  public static final String EVENT_SOURCE_VERSIONS = EventSourceEntryDtoOld.Fields.versions;
  public static final String EVENT_SOURCE_TENANTS = EventSourceEntryDtoOld.Fields.tenants;
  public static final String EVENT_SOURCE_TRACED_BY_BUSINESS_KEY = EventSourceEntryDtoOld.Fields.tracedByBusinessKey;
  public static final String EVENT_SOURCE_TRACE_VARIABLE = EventSourceEntryDtoOld.Fields.traceVariable;

  @Override
  public String getIndexName() {
    return EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    final XContentBuilder newXContentBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_MAPPING_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(PUBLISH_DATE_TIME)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(STATE)
      .field("type", "keyword")
      .endObject()
      .startObject(PUBLISH_PROGRESS)
        .field("type", "double")
      .endObject()
      .startObject(DELETED)
        .field("type", "boolean")
      .endObject()
      .startObject(XML)
        .field("type", "text")
        .field("index", true)
        .field("analyzer", "is_present_analyzer")
      .endObject()
      .startObject(MAPPINGS)
        .field("type", "object")
        .startObject("properties");
          addMappingFields(newXContentBuilder)
        .endObject()
      .endObject()
      .startObject(EVENT_IMPORT_SOURCES)
        .field("type", "object")
        .startObject("properties");
          addEventImportSourcesField(newXContentBuilder)
        .endObject()
      .endObject();
    // @formatter:on
    return newXContentBuilder;
  }

  private XContentBuilder addMappingFields(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    XContentBuilder newXContentBuilder = xContentBuilder
      .startObject(FLOWNODE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(START)
        .field("type", "object")
      .startObject("properties");
        addEventMappingFields(newXContentBuilder)
        .endObject()
      .endObject()
      .startObject(END)
        .field("type", "object")
      .startObject("properties");
        addEventMappingFields(newXContentBuilder)
        .endObject()
      .endObject();
    // @formatter:on
    return newXContentBuilder;
  }

  private XContentBuilder addEventMappingFields(final XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      // @formatter:off
      .startObject(GROUP)
        .field("type", "keyword")
      .endObject()
      .startObject(SOURCE)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_LABEL)
      .field("type", "keyword")
      .endObject();
    // @formatter:on
  }

  private XContentBuilder addEventImportSourcesField(final XContentBuilder xContentBuilder) throws IOException {
    XContentBuilder newXContentBuilder =
      // @formatter:off
    xContentBuilder
      .startObject(FIRST_EVENT_FOR_IMPORT_SOURCE_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(PUBLISH_COMPLETED_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(LAST_IMPORT_EXECUTION_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(LAST_IMPORTED_EVENT_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(EVENT_SOURCE)
        .field("type", "object")
        .startObject("properties");
          addEventSourcesField(newXContentBuilder)
        .endObject()
      .endObject();
    // @formatter:on
    return newXContentBuilder;
  }

  private XContentBuilder addEventSourcesField(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(EVENT_SOURCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_EVENT_SCOPE)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_PROC_DEF_KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_VERSIONS)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_TENANTS)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_TRACED_BY_BUSINESS_KEY)
        .field("type", "boolean")
      .endObject()
      .startObject(EVENT_SOURCE_TRACE_VARIABLE)
        .field("type", "keyword")
      .endObject();
    // @formatter:on
  }

}
