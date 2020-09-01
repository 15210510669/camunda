/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@RequiredArgsConstructor
@Component
@Slf4j
public class ElasticsearchMetadataService {
  private static final String ERROR_MESSAGE_ES_REQUEST = "Could not write Optimize metadata (version and " +
    "installationID) to Elasticsearch.";

  private static final String CURRENT_OPTIMIZE_VERSION = Version.VERSION;

  private final ObjectMapper objectMapper;

  public void initMetadataIfMissing(final OptimizeElasticsearchClient esClient) {
    final String newInstallationId = UUID.randomUUID().toString();
    upsertMetadataWithScript(
      esClient,
      CURRENT_OPTIMIZE_VERSION,
      newInstallationId,
      createInitInstallationIdScriptIfMissing(newInstallationId)
    );
  }

  public void validateSchemaVersionCompatibility(final OptimizeElasticsearchClient esClient) {
    readMetadata(esClient).ifPresent((metadataDto) -> {
      if (!CURRENT_OPTIMIZE_VERSION.equals(metadataDto.getSchemaVersion())) {
        final String errorMessage = String.format(
          "The Elasticsearch Optimize schema version [%s] doesn't match the current Optimize version [%s]."
            + " Please make sure to run the Upgrade first.",
          metadataDto.getSchemaVersion(),
          CURRENT_OPTIMIZE_VERSION
        );
        throw new OptimizeRuntimeException(errorMessage);
      }
    });
  }

  public Optional<MetadataDto> readMetadata(final OptimizeElasticsearchClient esClient) {
    Optional<MetadataDto> result = Optional.empty();

    final SearchRequest searchRequest = new SearchRequest(METADATA_INDEX_NAME);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery());
    searchRequest.source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      long totalHits = searchResponse.getHits().getTotalHits().value;
      if (totalHits == 1) {
        try {
          MetadataDto parsed = objectMapper.readValue(
            searchResponse.getHits().getAt(0).getSourceAsString(),
            MetadataDto.class
          );
          result = Optional.ofNullable(parsed);
        } catch (IOException e) {
          log.error("Can't parse metadata", e);
        }
      } else if (totalHits > 1) {
        throw new OptimizeRuntimeException("Metadata search returned [" + totalHits + "] hits");
      }
    } catch (IOException | ElasticsearchException e) {
      log.info(
        "Was not able to retrieve metadata index, schema might not have been initialized yet if this is the first " +
          "startup!"
      );
    }

    return result;
  }

  public void upsertMetadata(final OptimizeElasticsearchClient esClient, final String schemaVersion) {
    final String newInstallationId = UUID.randomUUID().toString();
    upsertMetadataWithScript(
      esClient,
      schemaVersion,
      newInstallationId,
      createUpdateMetadataScript(newInstallationId, schemaVersion)
    );
  }

  private void upsertMetadataWithScript(final OptimizeElasticsearchClient esClient,
                                        final String schemaVersion,
                                        final String newInstallationId,
                                        final Script updateScript) {
    final MetadataDto newMetadataIfAbsent = new MetadataDto(schemaVersion, newInstallationId);

    try {
      final UpdateRequest request = new UpdateRequest()
        .index(METADATA_INDEX_NAME)
        .id(MetadataIndex.ID)
        .script(updateScript)
        .upsert(objectMapper.writeValueAsString(newMetadataIfAbsent), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      final UpdateResponse response = esClient.update(request, RequestOptions.DEFAULT);
      if (!response.getResult().equals(DocWriteResponse.Result.CREATED)
        && !response.getResult().equals(DocWriteResponse.Result.UPDATED)) {
        log.error(ERROR_MESSAGE_ES_REQUEST);
        throw new OptimizeRuntimeException(ERROR_MESSAGE_ES_REQUEST);
      }
    } catch (IOException e) {
      log.error(ERROR_MESSAGE_ES_REQUEST, e);
      throw new OptimizeRuntimeException(ERROR_MESSAGE_ES_REQUEST, e);
    }
  }

  private Script createUpdateMetadataScript(final String newInstallationId,
                                            final String newSchemaVersion) {
    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put("newInstallationId", newInstallationId)
      .put("newSchemaVersion", newSchemaVersion)
      .build();

    //@formatter:off
    final String scriptString =
      "ctx._source." + MetadataDto.Fields.schemaVersion.name() + " = params.newSchemaVersion;\n" +
      "if (ctx._source." + MetadataDto.Fields.installationId.name() + " == null) {\n" +
        "ctx._source." + MetadataDto.Fields.installationId.name() + " = params.newInstallationId;\n" +
      "}\n";
    //@formatter:on

    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      scriptString,
      params
    );
  }

  private Script createInitInstallationIdScriptIfMissing(final String newInstallationId) {
    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put("newInstallationId", newInstallationId)
      .build();

    //@formatter:off
    final String scriptString =
      "if (ctx._source." + MetadataDto.Fields.installationId.name() + " == null) {\n" +
        "ctx._source." + MetadataDto.Fields.installationId.name() + " = params.newInstallationId;\n" +
      "}\n";
    //@formatter:on

    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      scriptString,
      params
    );
  }

}
