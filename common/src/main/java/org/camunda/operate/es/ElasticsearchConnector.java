/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.camunda.operate.property.ElasticsearchProperties;
import org.camunda.operate.property.OperateProperties;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.util.StringUtils;

import static org.camunda.operate.util.ThreadUtil.sleepFor;

@Component
@Configuration
public class ElasticsearchConnector {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConnector.class);

  @Autowired
  private OperateProperties operateProperties;

  @Bean
  public RestHighLevelClient esClient() {
    //some weird error when ELS sets available processors number for Netty - see https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createEsClient(operateProperties.getElasticsearch());
  }

  @Bean("zeebeEsClient")
  public RestHighLevelClient zeebeEsClient() {
    //some weird error when ELS sets available processors number for Netty - see https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createEsClient(operateProperties.getZeebeElasticsearch());
  }
  
  public static void closeEsClient(RestHighLevelClient esClient) {
    if (esClient != null) {
      try {
        esClient.close();
      } catch (IOException e) {
        logger.error("Could not close esClient",e);
      }
    }
  }

  public RestHighLevelClient createEsClient(ElasticsearchProperties elsConfig) {
    logger.debug("Creating Elasticsearch connection...");
    final RestClientBuilder restClientBuilder = RestClient.builder(getHttpHost(elsConfig))
        .setHttpClientConfigCallback(
            httpClientBuilder -> setupAuthentication(httpClientBuilder, elsConfig));
    if (elsConfig.getConnectTimeout() != null || elsConfig.getSocketTimeout() != null) {
      restClientBuilder
          .setRequestConfigCallback(configCallback -> setTimeouts(configCallback, elsConfig));
    }
    RestHighLevelClient esClient = new RestHighLevelClient(restClientBuilder);
    if (!checkHealth(esClient, true)) {
      logger.warn("Elasticsearch cluster is not accessible");
    } else {
      logger.debug("Elasticsearch connection was successfully created.");
    }
    return esClient;
  }

  private Builder setTimeouts(
      final Builder builder,
      final ElasticsearchProperties elsConfig) {
    if (elsConfig.getSocketTimeout() != null) {
      builder.setSocketTimeout(elsConfig.getSocketTimeout());
    }
    if (elsConfig.getConnectTimeout() != null) {
      builder.setConnectTimeout(elsConfig.getConnectTimeout());
    }
    return builder;
  }

  private HttpHost getHttpHost(ElasticsearchProperties elsConfig) {
    URI uri = elsConfig.getURI();
    return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
  }

  private HttpAsyncClientBuilder setupAuthentication(final HttpAsyncClientBuilder builder,
      ElasticsearchProperties elsConfig) {
    if (StringUtils.isEmpty(elsConfig.getUsername()) || StringUtils
        .isEmpty(elsConfig.getPassword())) {
      logger.warn(
          "Username and/or password for are empty. Basic authentication for elasticsearch is not used.");
      return builder;
    }
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
            elsConfig.getUsername(),
            elsConfig.getPassword()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
  }

  public boolean checkHealth(RestHighLevelClient esClient, boolean reconnect) {
    //TODO temporary solution
    ElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    int attempts = 0, maxAttempts = 50;
    boolean successfullyConnected = false;
    while (attempts == 0 || (reconnect && attempts < maxAttempts && !successfullyConnected)) {
      try {
        final ClusterHealthResponse clusterHealthResponse = esClient.cluster()
            .health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
        //!clusterHealthResponse.getStatus().equals(ClusterHealthStatus.RED)
        //TODO do we need this?
        successfullyConnected = clusterHealthResponse.getClusterName()
            .equals(elsConfig.getClusterName());
      } catch (IOException ex) {
        logger.error(
            "Error occurred while connecting to Elasticsearch: clustername [{}], {}:{}. Will be retried ({}/{}) ...",
            elsConfig.getClusterName(),
            elsConfig.getHost(), elsConfig.getPort(), attempts, maxAttempts, ex);
        sleepFor(3000);
      }
      attempts++;
    }
    return successfullyConnected;
  }

  public boolean checkHealth(boolean reconnect) {
    return checkHealth(esClient(), reconnect);
  }

  public static class CustomOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

    private DateTimeFormatter formatter;

    public CustomOffsetDateTimeSerializer(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      gen.writeString(value.format(this.formatter));
    }
  }

  public static class CustomOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private DateTimeFormatter formatter;

    public CustomOffsetDateTimeDeserializer(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {

      OffsetDateTime parsedDate;
      try {
        parsedDate = OffsetDateTime.parse(parser.getText(), this.formatter);
      } catch(DateTimeParseException exception) {
        //
        parsedDate = ZonedDateTime
          .parse(parser.getText(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault()))
          .toOffsetDateTime();
      }
      return parsedDate;
    }
  }


  public static class CustomInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      return Instant.ofEpochMilli(Long.valueOf(parser.getText()));
    }
  }

//  public static class CustomLocalDateSerializer extends JsonSerializer<LocalDate> {
//
//    private DateTimeFormatter formatter;
//
//    public CustomLocalDateSerializer(DateTimeFormatter formatter) {
//      this.formatter = formatter;
//    }
//
//    @Override
//    public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider provider) throws IOException {
//      gen.writeString(value.format(this.formatter));
//    }
//  }
//
//  public static class CustomLocalDateDeserializer extends JsonDeserializer<LocalDate> {
//
//    private DateTimeFormatter formatter;
//
//    public CustomLocalDateDeserializer(DateTimeFormatter formatter) {
//      this.formatter = formatter;
//    }
//
//    @Override
//    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
//
//      LocalDate parsedDate;
//      try {
//        parsedDate = LocalDate.parse(parser.getText(), this.formatter);
//      } catch(DateTimeParseException exception) {
//        //
//        parsedDate = LocalDate
//          .parse(parser.getText(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
//      }
//      return parsedDate;
//    }
//  }


}
