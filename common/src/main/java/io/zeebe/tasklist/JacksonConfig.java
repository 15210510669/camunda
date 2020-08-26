/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.zeebe.tasklist.es.ElasticsearchConnector;
import io.zeebe.tasklist.property.TasklistProperties;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

  @Autowired private TasklistProperties tasklistProperties;

  @Bean
  public ObjectMapper objectMapper() {
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(
        OffsetDateTime.class,
        new ElasticsearchConnector.CustomOffsetDateTimeSerializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(
        OffsetDateTime.class,
        new ElasticsearchConnector.CustomOffsetDateTimeDeserializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(
        Instant.class, new ElasticsearchConnector.CustomInstantDeserializer());

    //    javaTimeModule.addSerializer(LocalDate.class, new
    // ElasticsearchConnector.CustomLocalDateSerializer(localDateFormatter()));
    //    javaTimeModule.addDeserializer(LocalDate.class, new
    // ElasticsearchConnector.CustomLocalDateDeserializer(localDateFormatter()));

    return Jackson2ObjectMapperBuilder.json()
        .modules(javaTimeModule, new Jdk8Module())
        .featuresToDisable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT)
        .build();
  }

  @Bean
  public DateTimeFormatter dateTimeFormatter() {
    return DateTimeFormatter.ofPattern(tasklistProperties.getElasticsearch().getDateFormat());
  }

  @Bean
  public DateTimeFormatter localDateFormatter() {
    return DateTimeFormatter.ofPattern(tasklistProperties.getZeebeElasticsearch().getDateFormat());
  }
}
