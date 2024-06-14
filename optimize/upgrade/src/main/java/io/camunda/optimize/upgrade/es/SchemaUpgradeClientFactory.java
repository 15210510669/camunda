/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.upgrade.es;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.MappingMetadataUtil;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import io.camunda.optimize.service.util.mapper.OptimizeDateTimeFormatterFactory;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.xcontent.XContentBuilder;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SchemaUpgradeClientFactory {
  public static SchemaUpgradeClient createSchemaUpgradeClient(
      final UpgradeExecutionDependencies upgradeDependencies) {
    return createSchemaUpgradeClient(
        upgradeDependencies.metadataService(),
        upgradeDependencies.configurationService(),
        upgradeDependencies.indexNameService(),
        upgradeDependencies.esClient());
  }

  public static SchemaUpgradeClient createSchemaUpgradeClient(
      final ElasticSearchMetadataService metadataService,
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService,
      final OptimizeElasticsearchClient esClient) {
    final MappingMetadataUtil mappingUtil = new MappingMetadataUtil(esClient);
    // TODO remove call to convert list with OPT-7238
    return createSchemaUpgradeClient(
        new ElasticSearchSchemaManager(
            metadataService,
            configurationService,
            indexNameService,
            convertList(mappingUtil.getAllMappings(indexNameService.getIndexPrefix()))),
        metadataService,
        configurationService,
        esClient);
  }

  public static SchemaUpgradeClient createSchemaUpgradeClient(
      final ElasticSearchSchemaManager schemaManager,
      final ElasticSearchMetadataService metadataService,
      final ConfigurationService configurationService,
      final OptimizeElasticsearchClient esClient) {
    return new SchemaUpgradeClient(
        schemaManager,
        metadataService,
        esClient,
        new ObjectMapperFactory(
                new OptimizeDateTimeFormatterFactory().getObject(), configurationService)
            .createOptimizeMapper());
  }

  // TODO delete with OPT-7238
  @SuppressWarnings("unchecked") // Suppress unchecked cast warnings
  private static List<IndexMappingCreator<XContentBuilder>> convertList(
      final List<IndexMappingCreator<?>> wildcardList) {
    return wildcardList.stream()
        .map(creator -> (IndexMappingCreator<XContentBuilder>) creator) // Unchecked cast
        .toList();
  }
}
