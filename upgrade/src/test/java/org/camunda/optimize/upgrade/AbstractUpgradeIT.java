/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.util.UpgradeUtil;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;

public abstract class AbstractUpgradeIT {

  protected static final MetadataIndex METADATA_INDEX = new MetadataIndex();

  protected ObjectMapper objectMapper;
  protected OptimizeElasticsearchClient prefixAwareClient;
  protected OptimizeIndexNameService indexNameService;
  protected UpgradeExecutionDependencies upgradeDependencies;
  protected ElasticsearchMetadataService metadataService;
  protected ConfigurationService configurationService;

  @AfterEach
  public void after() throws Exception {
    cleanAllDataFromElasticsearch();
    deleteEnvConfig();
  }

  @BeforeEach
  protected void setUp() throws Exception {
    configurationService = createDefaultConfiguration();
    if (upgradeDependencies == null) {
      upgradeDependencies = UpgradeUtil.createUpgradeDependencies();
      objectMapper = upgradeDependencies.getObjectMapper();
      prefixAwareClient = upgradeDependencies.getEsClient();
      indexNameService = upgradeDependencies.getIndexNameService();
      metadataService = upgradeDependencies.getMetadataService();
    }

    cleanAllDataFromElasticsearch();
    createEmptyEnvConfig();
  }

  protected void initSchema(List<IndexMappingCreator> mappingCreators) {
    final ElasticSearchSchemaManager elasticSearchSchemaManager = new ElasticSearchSchemaManager(
      metadataService, createDefaultConfiguration(), indexNameService, mappingCreators, objectMapper
    );
    elasticSearchSchemaManager.initializeSchema(prefixAwareClient);
  }

  protected void setMetadataIndexVersion(String version) {
    metadataService.upsertMetadata(prefixAwareClient, version);
  }

  protected void createOptimizeIndexWithTypeAndVersion(DefaultIndexMappingCreator indexMapping,
                                                       int version) throws IOException {
    final String aliasName = indexNameService.getOptimizeIndexAliasForIndex(indexMapping.getIndexName());
    final String indexName = getVersionedIndexName(indexMapping.getIndexName(), version);
    final Settings indexSettings = createIndexSettings(indexMapping);

    CreateIndexRequest request = new CreateIndexRequest(indexName);
    request.alias(new Alias(aliasName));
    request.settings(indexSettings);
    request.mapping(indexMapping.getSource());
    indexMapping.setDynamic("false");
    prefixAwareClient.getHighLevelClient().indices().create(request, RequestOptions.DEFAULT);
  }

  protected void executeBulk(final String bulkPayload) throws IOException {
    final Request request = new Request(HttpPost.METHOD_NAME, "/_bulk");
    final HttpEntity entity = new NStringEntity(
      UpgradeUtil.readClasspathFileAsString(bulkPayload),
      ContentType.APPLICATION_JSON
    );
    request.setEntity(entity);
    prefixAwareClient.getLowLevelClient().performRequest(request);
    prefixAwareClient.getHighLevelClient().indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
  }

  private String getVersionedIndexName(final String indexName, final int version) {
    return OptimizeIndexNameService.getOptimizeIndexNameForAliasAndVersion(
      indexNameService.getOptimizeIndexAliasForIndex(indexName), String.valueOf(version)
    );
  }

  private Settings createIndexSettings(IndexMappingCreator indexMappingCreator) {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService, indexMappingCreator);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  protected void cleanAllDataFromElasticsearch() {
    prefixAwareClient.deleteIndexByRawIndexName("_all");
  }

  @SneakyThrows
  protected <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> valueType) {
    final SearchHit[] searchHits = getAllDocumentsOfIndex(indexName);
    return Arrays
      .stream(searchHits)
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), valueType
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(toList());
  }

  protected SearchHit[] getAllDocumentsOfIndex(final String... indexNames) throws IOException {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(indexNames).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return searchResponse.getHits().getHits();
  }

}
