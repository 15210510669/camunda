/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.reimport.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.BusinessKeyIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.TenantIndex;
import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.util.jetty.LoggingConfigurationReader;
import org.elasticsearch.client.RestHighLevelClient;

import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

/**
 * Deletes all engine data and the import indexes from Elasticsearch such that Optimize reimports all data from the
 * engine, but keeps all reports, dashboards and alerts that were defined before.
 * <p>
 * External events are kept. Event process data is cleared as well to allow for manual republishing.
 */
@Slf4j
public class ReimportPreparation {
  private static final ConfigurationService CONFIGURATION_SERVICE =
    ConfigurationServiceBuilder.createDefaultConfiguration();
  private static final List<IndexMappingCreator> STATIC_INDICES_TO_DELETE = ImmutableList.of(
    new ImportIndexIndex(),
    new TimestampBasedImportIndex(),
    new ProcessDefinitionIndex(),
    new EventProcessDefinitionIndex(),
    new ProcessInstanceIndex(),
    new DecisionDefinitionIndex(),
    new DecisionInstanceIndex(),
    new TenantIndex(),
    new BusinessKeyIndex(),
    new VariableUpdateInstanceIndex(),
    new EventProcessPublishStateIndex()
  );
  private static final List<IndexMappingCreator> DYNAMIC_EVENT_INDICES_TO_DELETE = ImmutableList.of(
    new EventProcessInstanceIndex("*"),
    new CamundaActivityEventIndex("*")
  );
  private static final List<IndexMappingCreator> DYNAMIC_EVENT_TRACE_INDICES_TO_DELETE = ImmutableList.of(
    new EventSequenceCountIndex("*"),
    new EventTraceStateIndex("*")
  );

  public static void main(String[] args) {
    log.info("Start to prepare Elasticsearch such that Optimize reimports engine data!");

    log.info("Reading configuration...");
    LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();
    log.info("Successfully read configuration.");

    log.info("Creating connection to Elasticsearch...");
    try (final RestHighLevelClient restHighLevelClient =
           ElasticsearchHighLevelRestClientBuilder.build(CONFIGURATION_SERVICE)) {
      log.info("Successfully created connection to Elasticsearch.");

      final OptimizeElasticsearchClient prefixAwareClient = new OptimizeElasticsearchClient(
        restHighLevelClient,
        new OptimizeIndexNameService(CONFIGURATION_SERVICE)
      );

      deleteImportAndEngineDataIndices(prefixAwareClient);
      recreateStaticIndices(prefixAwareClient);

      log.info(
        "Optimize was successfully prepared such it can reimport the engine data. Feel free to start Optimize again!"
      );
    } catch (Exception e) {
      log.error("Failed preparing Optimize for reimport.", e);
    }
  }

  private static void deleteImportAndEngineDataIndices(final OptimizeElasticsearchClient prefixAwareClient) {
    log.info("Deleting import and engine data indices from Elasticsearch...");
    STATIC_INDICES_TO_DELETE.forEach(prefixAwareClient::deleteIndex);
    log.info("Finished deleting import and engine data indices from Elasticsearch.");

    log.info("Deleting event process indices and Camunda event data from Elasticsearch...");
    DYNAMIC_EVENT_INDICES_TO_DELETE.forEach(prefixAwareClient::deleteIndex);
    log.info("Finished deleting event process indices and Camunda event data from Elasticsearch.");

    log.info("Deleting Camunda event count/trace indices from Elasticsearch...");
    DYNAMIC_EVENT_TRACE_INDICES_TO_DELETE.forEach(index -> prefixAwareClient.deleteIndexByRawIndexNames(
      createIndexPatternExcludingExternalEventIndexVariant(prefixAwareClient, index)
    ));
    log.info("Finished Camunda event count/trace indices from Elasticsearch.");
  }

  private static String createIndexPatternExcludingExternalEventIndexVariant(final OptimizeElasticsearchClient prefixAwareClient,
                                                                             final IndexMappingCreator index) {
    return prefixAwareClient.getIndexNameService().getOptimizeIndexNameWithVersionWithWildcardSuffix(index)
      + ",-"
      + prefixAwareClient.getIndexNameService().getOptimizeIndexAliasForIndex(index.getIndexName())
      + EXTERNAL_EVENTS_INDEX_SUFFIX + "*";
  }

  private static void recreateStaticIndices(final OptimizeElasticsearchClient prefixAwareClient) {
    log.info("Recreating import indices and engine data...");

    final ObjectMapper objectMapper = new ObjectMapper();
    final ElasticSearchSchemaManager schemaManager = new ElasticSearchSchemaManager(
      new ElasticsearchMetadataService(objectMapper),
      CONFIGURATION_SERVICE,
      prefixAwareClient.getIndexNameService(),
      STATIC_INDICES_TO_DELETE
    );

    schemaManager.createOptimizeIndices(prefixAwareClient);

    log.info("Finished recreating import and engine data indices from Elasticsearch.");
  }

}
