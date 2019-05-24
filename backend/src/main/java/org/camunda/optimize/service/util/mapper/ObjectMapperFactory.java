/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class ObjectMapperFactory {
  private final DateTimeFormatter optimizeDateTimeFormatter;
  private final DateTimeFormatter engineDateTimeFormatter;

  public ObjectMapperFactory(final DateTimeFormatter optimizeDateTimeFormatter,
                             final ConfigurationService configurationService) {
    this.optimizeDateTimeFormatter = optimizeDateTimeFormatter;
    this.engineDateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  @Primary
  @Qualifier("optimizeMapper")
  @Bean
  public ObjectMapper createOptimizeMapper() {
    return buildObjectMapper(optimizeDateTimeFormatter);
  }

  @Qualifier("engineMapper")
  @Bean
  public ObjectMapper createEngineMapper() {
    return buildObjectMapper(engineDateTimeFormatter);
  }

  private ObjectMapper buildObjectMapper(DateTimeFormatter deserializationDateTimeFormatter) {
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(this.optimizeDateTimeFormatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(deserializationDateTimeFormatter));

    ObjectMapper mapper = Jackson2ObjectMapperBuilder
      .json()
      .modules(new Jdk8Module(), javaTimeModule)
      .featuresToDisable(
        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
        DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY
      )
      .featuresToEnable(
        JsonParser.Feature.ALLOW_COMMENTS,
        SerializationFeature.INDENT_OUTPUT,
        DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
        MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS
      )
      .build();

    SimpleModule module = new SimpleModule();
    module.addDeserializer(ReportDefinitionDto.class, new CustomReportDefinitionDeserializer(mapper));
    module.addDeserializer(CollectionEntity.class, new CustomCollectionEntityDeserializer(mapper));
    mapper.registerModule(module);

    return mapper;
  }
}
