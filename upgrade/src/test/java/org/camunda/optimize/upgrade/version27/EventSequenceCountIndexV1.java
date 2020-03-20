/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version27;

import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EventSequenceCountIndexV1 extends DefaultIndexMappingCreator {

  public static final int VERSION = 1;

  public static final String ID = EventSequenceCountDto.Fields.id;
  public static final String SOURCE_EVENT = EventSequenceCountDto.Fields.sourceEvent;
  public static final String TARGET_EVENT = EventSequenceCountDto.Fields.targetEvent;
  public static final String COUNT = EventSequenceCountDto.Fields.count;

  public static final String GROUP = EventTypeDto.Fields.group;
  public static final String SOURCE = EventTypeDto.Fields.source;
  public static final String EVENT_NAME = EventTypeDto.Fields.eventName;

  @Override
  public String getIndexName() {
    return "event-sequence-count";
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(ID)
      .field("type", "keyword")
      .endObject()
      .startObject(SOURCE_EVENT)
      .field("type", "object")
      .startObject("properties")
      .startObject(GROUP)
      .field("type", "keyword")
      .endObject()
      .startObject(SOURCE)
      .field("type", "keyword")
      .endObject()
      .startObject(EVENT_NAME)
      .field("type", "keyword")
      .endObject()
      .endObject()
      .endObject()
      .startObject(TARGET_EVENT)
      .field("type", "object")
      .startObject("properties")
      .startObject(GROUP)
      .field("type", "keyword")
      .endObject()
      .startObject(SOURCE)
      .field("type", "keyword")
      .endObject()
      .startObject(EVENT_NAME)
      .field("type", "keyword")
      .endObject()
      .endObject()
      .endObject()
      .startObject(COUNT)
      .field("type", "long")
      .endObject()
      ;
    // @formatter:on
  }

}