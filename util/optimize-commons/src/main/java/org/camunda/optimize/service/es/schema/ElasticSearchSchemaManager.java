/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.service.es.schema.index.BusinessKeyIndex;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DashboardShareIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.LicenseIndex;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.es.schema.index.OnboardingStateIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ReportShareIndex;
import org.camunda.optimize.service.es.schema.index.SettingsIndex;
import org.camunda.optimize.service.es.schema.index.TenantIndex;
import org.camunda.optimize.service.es.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildDynamicSettings;

@Component
@Slf4j
public class ElasticSearchSchemaManager {
  private static final String INDEX_READ_ONLY_SETTING = "index.blocks.read_only_allow_delete";
  public static final int INDEX_EXIST_BATCH_SIZE = 10;

  private final ElasticsearchMetadataService metadataService;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService indexNameService;

  private final List<IndexMappingCreator> mappings;
  private final ObjectMapper objectMapper;

  @Autowired
  public ElasticSearchSchemaManager(final ElasticsearchMetadataService metadataService,
                                    final ConfigurationService configurationService,
                                    final OptimizeIndexNameService indexNameService,
                                    final ObjectMapper objectMapper) {
    this.metadataService = metadataService;
    this.configurationService = configurationService;
    this.indexNameService = indexNameService;
    this.mappings = new ArrayList<>();
    mappings.addAll(getAllNonDynamicMappings());
    this.objectMapper = objectMapper;
  }

  public ElasticSearchSchemaManager(final ElasticsearchMetadataService metadataService,
                                    final ConfigurationService configurationService,
                                    final OptimizeIndexNameService indexNameService,
                                    final List<IndexMappingCreator> mappings,
                                    final ObjectMapper objectMapper) {
    this.metadataService = metadataService;
    this.configurationService = configurationService;
    this.indexNameService = indexNameService;
    this.mappings = mappings;
    this.objectMapper = objectMapper;
  }

  public void validateExistingSchemaVersion(final OptimizeElasticsearchClient esClient) {
    metadataService.validateSchemaVersionCompatibility(esClient);
  }

  public void initializeSchema(final OptimizeElasticsearchClient esClient) {
    unblockIndices(esClient.getHighLevelClient());
    if (!schemaExists(esClient)) {
      log.info("Initializing Optimize schema...");
      createOptimizeIndices(esClient);
      log.info("Optimize schema initialized successfully.");
    } else {
      updateAllMappingsAndDynamicSettings(esClient);
    }
    metadataService.initMetadataIfMissing(esClient);
  }

  public void addMapping(IndexMappingCreator mapping) {
    mappings.add(mapping);
  }

  public List<IndexMappingCreator> getMappings() {
    return mappings;
  }

  public boolean schemaExists(OptimizeElasticsearchClient esClient) {
    return indicesExist(esClient, getMappings());
  }

  public boolean indexExists(final OptimizeElasticsearchClient esClient,
                             final IndexMappingCreator mapping) {
    return indicesExist(esClient, Collections.singletonList(mapping));
  }

  public boolean indicesExist(final OptimizeElasticsearchClient esClient,
                              final List<IndexMappingCreator> mappings) {
    return StreamSupport.stream(Iterables.partition(mappings, INDEX_EXIST_BATCH_SIZE).spliterator(), true)
      .map(mappingBatch -> mappingBatch.stream().map(IndexMappingCreator::getIndexName).collect(toList()))
      .allMatch(indices -> {
        final GetIndexRequest request = new GetIndexRequest(indices.toArray(new String[]{}));
        try {
          return esClient.exists(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
          final String message = String.format(
            "Could not check if [%s] index(es) already exist.", String.join(",", indices)
          );
          throw new OptimizeRuntimeException(message, e);
        }
      });
  }

  public void createIndexIfMissing(final OptimizeElasticsearchClient esClient,
                                   final IndexMappingCreator indexMapping) {
    try {
      final boolean indexAlreadyExists = indexExists(esClient, indexMapping);
      if (!indexAlreadyExists) {
        createOptimizeIndex(esClient, indexMapping);
      }
    } catch (final Exception e) {
      log.error("Failed ensuring index is present: {}", indexMapping.getIndexName(), e);
      throw e;
    }
  }

  /**
   * NOTE: create one alias and index per type
   * <p>
   * https://www.elastic.co/guide/en/elasticsearch/reference/6.0/indices-aliases.html
   */
  public void createOptimizeIndices(OptimizeElasticsearchClient esClient) {
    for (IndexMappingCreator mapping : mappings) {
      createOptimizeIndex(esClient, mapping);
    }

    RefreshRequest refreshAllIndexesRequest = new RefreshRequest();
    try {
      esClient.getHighLevelClient().indices().refresh(refreshAllIndexesRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not refresh Optimize indices!", e);
    }
  }

  public void createOptimizeIndex(final OptimizeElasticsearchClient esClient,
                                  final IndexMappingCreator mapping) {
    createOptimizeIndex(esClient, mapping, Collections.emptySet());
  }

  public void createOptimizeIndex(final OptimizeElasticsearchClient esClient,
                                  final IndexMappingCreator mapping,
                                  final Set<String> readOnlyAliases) {
    final Set<String> prefixedReadOnlyAliases =
      readOnlyAliases.stream()
        .map(indexNameService::getOptimizeIndexAliasForIndex)
        .collect(toSet());
    final String defaultAliasName = indexNameService.getOptimizeIndexAliasForIndex(mapping.getIndexName());
    final String suffixedIndexName = indexNameService.getOptimizeIndexNameWithVersion(mapping);
    final Settings indexSettings = createIndexSettings(mapping);

    try {
      if (mapping.getCreateFromTemplate()) {
        // Creating template without alias and adding aliases manually to indices created from this template to
        // ensure correct alias handling on rollover
        createOrUpdateTemplateWithAliases(
          esClient, mapping, defaultAliasName, prefixedReadOnlyAliases, indexSettings
        );
        createOptimizeIndexWithWriteAliasFromTemplate(esClient, suffixedIndexName, defaultAliasName);
      } else {
        createOptimizeIndexFromRequest(
          esClient, mapping, suffixedIndexName, defaultAliasName, prefixedReadOnlyAliases, indexSettings
        );
      }
    } catch (ElasticsearchStatusException e) {
      if (e.status() == RestStatus.BAD_REQUEST && e.getMessage().contains("resource_already_exists_exception")) {
        log.debug("index {} already exists, updating mapping and dynamic settings.", suffixedIndexName);
        updateDynamicSettingsAndMappings(esClient, mapping);
      } else {
        throw e;
      }
    } catch (Exception e) {
      String message = String.format("Could not create Index [%s]", suffixedIndexName);
      log.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void createOptimizeIndexFromRequest(final OptimizeElasticsearchClient esClient,
                                              final IndexMappingCreator mapping,
                                              final String indexName,
                                              final String defaultAliasName,
                                              final Set<String> additionalAliases,
                                              final Settings indexSettings) throws
                                                                            IOException,
                                                                            ElasticsearchStatusException {
    log.debug("Creating Optimize Index with name {}, default alias {} and additional aliases {}",
              indexName, defaultAliasName, additionalAliases
    );
    final CreateIndexRequest request = new CreateIndexRequest(indexName);
    final Set<String> aliases = new HashSet<>(additionalAliases);
    aliases.add(defaultAliasName);
    aliases.forEach(
      additionalAliasName -> request.alias(
        new Alias(additionalAliasName).writeIndex(defaultAliasName.equals(additionalAliasName))
      )
    );
    request.settings(indexSettings);
    request.mapping(mapping.getSource());
    esClient.getHighLevelClient().indices().create(request, RequestOptions.DEFAULT);
  }

  private void createOrUpdateTemplateWithAliases(final OptimizeElasticsearchClient esClient,
                                                 final IndexMappingCreator mappingCreator,
                                                 final String defaultAliasName,
                                                 final Set<String> additionalAliases,
                                                 final Settings indexSettings) {
    final String templateName = indexNameService.getOptimizeIndexNameWithVersionWithoutSuffix(mappingCreator);
    log.info("Creating or updating template with name {}", templateName);

    PutIndexTemplateRequest templateRequest = new PutIndexTemplateRequest(templateName)
      .version(mappingCreator.getVersion())
      .mapping(mappingCreator.getSource())
      .settings(indexSettings)
      .patterns(Collections.singletonList(indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(mappingCreator)));

    additionalAliases.stream()
      .filter(aliasName -> !aliasName.equals(defaultAliasName))
      .map(aliasName -> {
        final Alias alias = new Alias(aliasName);
        alias.writeIndex(false);
        return alias;
      })
      .forEach(templateRequest::alias);

    try {
      esClient.getHighLevelClient().indices().putTemplate(templateRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not create or update template %s", templateName);
      log.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void createOptimizeIndexWithWriteAliasFromTemplate(final OptimizeElasticsearchClient esClient,
                                                             final String indexNameWithSuffix,
                                                             final String aliasName) {
    log.info("Creating index {} from template with write alias {}", indexNameWithSuffix, aliasName);
    Alias writeAlias = new Alias(aliasName).writeIndex(true);
    CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexNameWithSuffix).alias(writeAlias);
    try {
      esClient.getHighLevelClient().indices().create(createIndexRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not create index %s from template.", indexNameWithSuffix);
      log.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void deleteOptimizeIndex(final OptimizeElasticsearchClient esClient, final IndexMappingCreator mapping) {
    try {
      esClient.deleteIndex(mapping);
    } catch (ElasticsearchStatusException e) {
      if (e.status() == RestStatus.NOT_FOUND) {
        log.debug("Index {} was not found.", mapping.getIndexName());
      } else {
        throw e;
      }
    }
  }

  private void updateAllMappingsAndDynamicSettings(OptimizeElasticsearchClient esClient) {
    log.info("Updating Optimize schema...");
    for (IndexMappingCreator mapping : mappings) {
      updateDynamicSettingsAndMappings(esClient, mapping);
    }
    log.info("Finished updating Optimize schema.");
  }

  private void unblockIndices(RestHighLevelClient esClient) {
    Map<String, Map> responseBodyAsMap;
    try {
      // we need to perform this request manually since Elasticsearch 6.5 automatically
      // adds "master_timeout" parameter to the get settings request which is not
      // recognized prior to 6.4 and throws an error. As soon as we don't support 6.3 or
      // older those lines can be replaced with the high rest client equivalent.
      Request request = new Request("GET", "/_all/_settings");
      Response response = esClient.getLowLevelClient().performRequest(request);
      String responseBody = EntityUtils.toString(response.getEntity());
      responseBodyAsMap = objectMapper.readValue(responseBody, new TypeReference<Map<String, Map>>() {
      });
    } catch (Exception e) {
      log.error("Could not retrieve index settings!", e);
      throw new OptimizeRuntimeException("Could not retrieve index settings!", e);
    }
    boolean indexBlocked = false;
    for (Map.Entry<String, Map> entry : responseBodyAsMap.entrySet()) {
      Map<String, Map> settingsMap = (Map) entry.getValue().get("settings");
      Map<String, String> indexSettingsMap = settingsMap.get("index");
      if (Boolean.parseBoolean(indexSettingsMap.get(INDEX_READ_ONLY_SETTING))
        && entry.getKey().contains(indexNameService.getIndexPrefix())) {
        indexBlocked = true;
        log.info("Found blocked Optimize Elasticsearch indices");
        break;
      }
    }

    if (indexBlocked) {
      log.info("Unblocking Elasticsearch indices...");
      UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest(indexNameService.getIndexPrefix() + "*");
      updateSettingsRequest.settings(Settings.builder().put(INDEX_READ_ONLY_SETTING, false));
      try {
        esClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Could not unblock Elasticsearch indices!", e);
      }
    }
  }

  private void updateDynamicSettingsAndMappings(OptimizeElasticsearchClient esClient,
                                                IndexMappingCreator indexMapping) {
    updateIndexDynamicSettingsAndMappings(esClient.getHighLevelClient(), indexMapping);
    if (indexMapping.getCreateFromTemplate()) {
      updateTemplateDynamicSettingsAndMappings(esClient, indexMapping);
    }
  }

  private void updateTemplateDynamicSettingsAndMappings(OptimizeElasticsearchClient esClient,
                                                        IndexMappingCreator mappingCreator) {
    final String defaultAliasName = indexNameService.getOptimizeIndexAliasForIndex(mappingCreator.getIndexName());
    final Settings indexSettings = createIndexSettings(mappingCreator);
    createOrUpdateTemplateWithAliases(
      esClient, mappingCreator, defaultAliasName, Sets.newHashSet(), indexSettings
    );
  }

  private void updateIndexDynamicSettingsAndMappings(RestHighLevelClient esClient, IndexMappingCreator indexMapping) {
    final String indexName = indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMapping);
    try {
      final Settings indexSettings = buildDynamicSettings(configurationService);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest();
      updateSettingsRequest.indices(indexName);
      updateSettingsRequest.settings(indexSettings);
      esClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index settings for index [%s].", indexMapping.getIndexName());
      throw new OptimizeRuntimeException(message, e);
    }

    try {
      final PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
      putMappingRequest.source(indexMapping.getSource());
      esClient.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index mappings for index [%s].", indexMapping.getIndexName());
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private Settings createIndexSettings(IndexMappingCreator indexMappingCreator) {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService, indexMappingCreator);
    } catch (IOException e) {
      log.error("Could not create settings!", e);
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  private List<IndexMappingCreator> getAllNonDynamicMappings() {
    return Arrays.asList(
      new AlertIndex(),
      new BusinessKeyIndex(),
      new CollectionIndex(),
      new DashboardIndex(),
      new DashboardShareIndex(),
      new DecisionDefinitionIndex(),
      new DecisionInstanceIndex(),
      new LicenseIndex(),
      new MetadataIndex(),
      new OnboardingStateIndex(),
      new ProcessDefinitionIndex(),
      new ProcessInstanceIndex(),
      new ReportShareIndex(),
      new SettingsIndex(),
      new TenantIndex(),
      new TerminatedUserSessionIndex(),
      new VariableUpdateInstanceIndex(),
      new EventIndex(),
      new EventProcessDefinitionIndex(),
      new EventProcessMappingIndex(),
      new EventProcessPublishStateIndex(),
      new ImportIndexIndex(),
      new TimestampBasedImportIndex(),
      new CombinedReportIndex(),
      new SingleDecisionReportIndex(),
      new SingleProcessReportIndex()
    );
  }

}
