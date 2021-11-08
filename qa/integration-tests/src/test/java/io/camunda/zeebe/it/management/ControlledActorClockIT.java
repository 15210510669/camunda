/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.jackson.record.AbstractRecord;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class ControlledActorClockIT {
  private static final String INDEX_PREFIX = "exporter-clock-test";
  private ElasticsearchContainer elasticsearchContainer;
  private ZeebeContainer zeebeContainer;
  private ZeebeClient zeebeClient;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void startContainers() {
    final var network = Network.newNetwork();
    startElasticsearch(network);
    startZeebe(network);
  }

  @AfterEach
  void stopContainers() {
    zeebeContainer.stop();
    elasticsearchContainer.stop();
  }

  @Test
  void testPinningTime() throws IOException, InterruptedException {
    // given - Zeebe actor clock is pinned
    final var pinnedAt = pinZeebeTime();
    final var process = Bpmn.createExecutableProcess().startEvent().endEvent().done();

    // when - producing records
    final var deployResult =
        zeebeClient.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();
    zeebeClient
        .newCreateInstanceCommand()
        .processDefinitionKey(
            deployResult.getProcesses().stream()
                .findFirst()
                .orElseThrow()
                .getProcessDefinitionKey())
        .send()
        .join();

    // then - records are exported with a timestamp matching the pinned time
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var records = searchExportedRecords();
              assertThat(records).isNotNull();
              assertThat(records.size())
                  .isEqualTo(10); // deploy + instance produces exactly 10 records
              assertThat(records)
                  .allMatch((record) -> record.getTimestamp() == pinnedAt.toEpochMilli());
            });
  }

  @Test
  void testOffsetTime() throws IOException, InterruptedException {
    // given - Zeebe actor clock is offset
    final var beforeRecords = Instant.now();
    final var offsetZeebeTime = offsetZeebeTime();
    final var process = Bpmn.createExecutableProcess().startEvent().endEvent().done();

    // when - producing records
    final var deployResult =
        zeebeClient.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();
    zeebeClient
        .newCreateInstanceCommand()
        .processDefinitionKey(
            deployResult.getProcesses().stream()
                .findFirst()
                .orElseThrow()
                .getProcessDefinitionKey())
        .send()
        .join();

    // then - records are exported with a timestamp matching the offset time
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var records = searchExportedRecords();
              assertThat(records).isNotNull();
              assertThat(records.size())
                  .isEqualTo(10); // deploy + instance produces exactly 10 records
              assertThat(records)
                  .allSatisfy(
                      (record) -> {
                        final var timestamp = Instant.ofEpochMilli(record.getTimestamp());
                        final var afterRecords = Instant.now();
                        assertThat(timestamp).isBefore(afterRecords.plus(offsetZeebeTime));
                        assertThat(timestamp).isAfter(beforeRecords.plus(offsetZeebeTime));
                      });
            });
  }

  private List<AbstractRecord<?>> searchExportedRecords() throws IOException, InterruptedException {
    final var uri =
        URI.create(
            String.format(
                "http://%s/%s*/_search",
                elasticsearchContainer.getHttpHostAddress(), INDEX_PREFIX));
    final var request = HttpRequest.newBuilder(uri).method("POST", BodyPublishers.noBody()).build();
    final var response = httpClient.send(request, BodyHandlers.ofInputStream());
    final var result = mapper.readValue(response.body(), EsSearchResponseDto.class);
    if (result.documentsWrapper == null) {
      return null;
    }
    return result.documentsWrapper.documents.stream()
        .map(esDocumentDto -> esDocumentDto.record)
        .collect(Collectors.toList());
  }

  private Instant pinZeebeTime() throws IOException, InterruptedException {
    final var pinAt = Instant.now().minus(Duration.ofDays(1));
    final var body =
        BodyPublishers.ofString(String.format("{\"epochMilli\": %s}", pinAt.toEpochMilli()));
    zeebeRequest("/actuator/clock/pin", body);
    return pinAt;
  }

  private Duration offsetZeebeTime() throws IOException, InterruptedException {
    final var offsetBy = Duration.ofHours(3);
    final var body =
        BodyPublishers.ofString(String.format("{\"offsetMilli\": %s}", offsetBy.toMillis()));
    zeebeRequest("/actuator/clock/add", body);
    return offsetBy;
  }

  private void zeebeRequest(final String endpoint, final BodyPublisher body)
      throws IOException, InterruptedException {
    final var fullEndpoint =
        URI.create(
            String.format("http://%s/%s", zeebeContainer.getExternalAddress(9600), endpoint));
    final var httpRequest =
        HttpRequest.newBuilder(fullEndpoint)
            .method("POST", body)
            .header("Content-Type", "application/json")
            .build();
    final var httpResponse = httpClient.send(httpRequest, BodyHandlers.ofString());
    if (httpResponse.statusCode() != 200) {
      throw new IllegalStateException("Pinning time failed: " + httpResponse.body());
    }
  }

  void startElasticsearch(final Network network) {
    final var version = RestClient.class.getPackage().getImplementationVersion();
    elasticsearchContainer =
        new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                    .withTag(version))
            .withNetwork(network)
            .withNetworkAliases("elasticsearch")
            .withCreateContainerCmdModifier(
                cmd ->
                    Objects.requireNonNull(cmd.getHostConfig())
                        .withMemory((long) (1024 * 1024 * 1024)));
    elasticsearchContainer.start();
  }

  private void startZeebe(final Network network) {
    zeebeContainer =
        new ZeebeContainer(DockerImageName.parse("camunda/zeebe:current-test"))
            .withNetwork(network)
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
                "io.camunda.zeebe.exporter.ElasticsearchExporter")
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "http://elasticsearch:9200")
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY", "1")
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", INDEX_PREFIX)
            .withEnv("ZEEBE_CLOCK_CONTROLLED", "true");
    zeebeContainer.start();
    zeebeClient =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
            .build();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class EsDocumentDto {
    @JsonProperty(value = "_source", required = true)
    AbstractRecord<?> record;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class EsSearchResponseDto {
    @JsonProperty(value = "hits", required = true)
    DocumentsWrapper documentsWrapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class DocumentsWrapper {
      @JsonProperty(value = "hits", required = true)
      final List<EsDocumentDto> documents = Collections.emptyList();
    }
  }
}
