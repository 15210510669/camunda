/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.schema;

import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import org.camunda.optimize.service.db.es.schema.index.report.SingleDecisionReportIndexES;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.db.os.schema.index.ProcessDefinitionIndexOS;
import org.camunda.optimize.service.db.os.schema.index.report.SingleDecisionReportIndexOS;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.schema.type.MyUpdatedEventIndexOS;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.common.settings.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockserver.integration.ClientAndServer;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.testcontainers.shaded.org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static jakarta.ws.rs.HttpMethod.HEAD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.ApplicationContextProvider.getBean;
import static org.camunda.optimize.service.db.DatabaseConstants.MAPPING_NESTED_OBJECTS_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_REPLICAS_SETTING;
import static org.camunda.optimize.service.db.DatabaseConstants.REFRESH_INTERVAL_SETTING;
import static org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager.INDEX_EXIST_BATCH_SIZE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

// We can unfortunately not use constants in this expression, so it needs to be the literal text "opensearch".
@EnabledIfSystemProperty(named = "CAMUNDA_OPTIMIZE_DATABASE", matches = "opensearch")
public class OpenSearchSchemaManagerIT extends AbstractSchemaManagerIT {

  @Test
  public void doNotFailIfSomeIndexesAlreadyExist() {
    // given
    initializeSchema();

    embeddedOptimizeExtension.getOptimizeDatabaseClient()
      .deleteIndex(indexNameService.getOptimizeIndexAliasForIndex(new SingleDecisionReportIndexOS()));

    // when
    initializeSchema();

    // then
    assertThat(getSchemaManager().schemaExists(getOpenSearchOptimizeClient())).isTrue();
  }

  @Test
  public void optimizeIndexExistsAfterSchemaInitialization() {
    // when
    initializeSchema();
    assertThat(getSchemaManager().indexExists(getOpenSearchOptimizeClient(), METADATA_INDEX_NAME)).isTrue();
  }

  @Test
  public void allTypesExistsAfterSchemaInitialization() throws IOException {
    // when
    initializeSchema();

    // then
    final List<IndexMappingCreator<IndexSettings.Builder>> mappings = getSchemaManager().getMappings();
    assertThat(mappings).hasSize(29);
    for (IndexMappingCreator<IndexSettings.Builder> mapping : mappings) {
      assertIndexExists(mapping.getIndexName());
    }
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);
    assertMappingSettings(mappings, getSettingsResponse);
  }

  @Test
  public void mappingsAreUpdated() throws IOException {
    // given schema is created
    initializeSchema();

    // when there is a new mapping and I update the mapping
    IndexMappingCreator<IndexSettings.Builder> myUpdatedEventIndex = new MyUpdatedEventIndexOS();
    try {
      getSchemaManager().addMapping(myUpdatedEventIndex);
      initializeSchema();

      // then the mapping contains the new fields
      assertThatNewFieldExists();
    } finally {
      getSchemaManager().getMappings().remove(myUpdatedEventIndex);
    }
  }

  @Test
  public void dynamicSettingsAreUpdated() throws IOException {
    // given schema exists
    initializeSchema();

    // with a different dynamic setting than default
    final List<IndexMappingCreator<IndexSettings.Builder>> mappings = getSchemaManager().getMappings();
    modifyDynamicIndexSetting(mappings);

    // when
    initializeSchema();

    // then the settings contain values from configuration
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);
    assertMappingSettings(mappings, getSettingsResponse);
  }

  @Test
  public void indexExistCheckIsPerformedInBatches() {
    // given
    final int expectedExistQueryBatchExecutionCount =
      (int) Math.ceil((double) getSchemaManager().getMappings().size() / INDEX_EXIST_BATCH_SIZE);
    assertThat(expectedExistQueryBatchExecutionCount).isGreaterThan(1);
    // TODO fix with OPT-7455
    final ClientAndServer dbMockServer = useAndGetDbMockServer();

    // when
    embeddedOptimizeExtension.getDatabaseSchemaManager()
      .schemaExists(embeddedOptimizeExtension.getOptimizeDatabaseClient());

    // then the index exist check was performed in batches
    dbMockServer.verify(
      request().withPath(String.format(
        "/(%s.*){2,%s}",
        embeddedOptimizeExtension.getOptimizeDatabaseClient().getIndexNameService().getIndexPrefix(),
        INDEX_EXIST_BATCH_SIZE
      )).withMethod(HEAD),
      exactly(expectedExistQueryBatchExecutionCount)
    );
  }

  @Test
  public void dynamicSettingsAreAppliedToStaticIndices() throws IOException {
    final String oldRefreshInterval = embeddedOptimizeExtension.getConfigurationService()
      .getOpenSearchConfiguration()
      .getRefreshInterval();
    final int oldReplicaCount = embeddedOptimizeExtension.getConfigurationService()
      .getOpenSearchConfiguration()
      .getNumberOfReplicas();
    final int oldNestedDocumentLimit = embeddedOptimizeExtension.getConfigurationService()
      .getOpenSearchConfiguration()
      .getNestedDocumentsLimit();

    // given schema exists
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setRefreshInterval("100s");
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setNumberOfReplicas(2);
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setNestedDocumentsLimit(10);

    // when
    initializeSchema();

    // then the settings contain the updated dynamic values
    final GetSettingsResponse getSettingsResponse =
      getIndexSettingsFor(Collections.singletonList(new ProcessDefinitionIndexOS()));
    final String indexName =
      indexNameService.getOptimizeIndexNameWithVersion(new ProcessDefinitionIndexOS());
    final Settings settings = getSettingsResponse.getIndexToSettings().get(indexName);
    assertThat(settings.get("index." + REFRESH_INTERVAL_SETTING)).isEqualTo("100s");
    assertThat(settings.getAsInt("index." + NUMBER_OF_REPLICAS_SETTING, 111)).isEqualTo(2);
    assertThat(settings.getAsInt("index." + MAPPING_NESTED_OBJECTS_LIMIT, 111)).isEqualTo(10);

    // cleanup
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setRefreshInterval(oldRefreshInterval);
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setNumberOfReplicas(oldReplicaCount);
    embeddedOptimizeExtension.getConfigurationService()
      .getOpenSearchConfiguration()
      .setNestedDocumentsLimit(oldNestedDocumentLimit);
    initializeSchema();
  }

  @Test
  public void dynamicSettingsAreAppliedToExistingDynamicIndices() throws IOException {
    final String oldRefreshInterval = embeddedOptimizeExtension.getConfigurationService()
      .getOpenSearchConfiguration()
      .getRefreshInterval();
    final int oldReplicaCount = embeddedOptimizeExtension.getConfigurationService()
      .getOpenSearchConfiguration()
      .getNumberOfReplicas();
    final int oldNestedDocumentLimit = embeddedOptimizeExtension.getConfigurationService()
      .getOpenSearchConfiguration()
      .getNestedDocumentsLimit();

    // given a dynamic index is created by the import of process instance data
    final ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getSimpleBpmnDiagram());
    importAllEngineEntitiesFromScratch();
    // then the dynamic index settings are changed
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setRefreshInterval("100s");
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setNumberOfReplicas(2);
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setNestedDocumentsLimit(10);

    // when
    initializeSchema();

    // then the settings contain the updated dynamic values
    final ProcessInstanceIndex dynamicIndex =
      new ProcessInstanceIndexES(processInstanceEngineDto.getProcessDefinitionKey());
    final GetSettingsResponse getSettingsResponse =
      getIndexSettingsFor(Collections.singletonList(dynamicIndex));
    final String indexName = indexNameService.getOptimizeIndexNameWithVersion(dynamicIndex);
    final Settings settings = getSettingsResponse.getIndexToSettings().get(indexName);
    assertThat(settings.get("index." + REFRESH_INTERVAL_SETTING)).isEqualTo("100s");
    assertThat(settings.getAsInt("index." + NUMBER_OF_REPLICAS_SETTING, 111)).isEqualTo(2);
    assertThat(settings.getAsInt("index." + MAPPING_NESTED_OBJECTS_LIMIT, 111)).isEqualTo(10);

    // cleanup
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setRefreshInterval(oldRefreshInterval);
    embeddedOptimizeExtension.getConfigurationService().getOpenSearchConfiguration().setNumberOfReplicas(oldReplicaCount);
    embeddedOptimizeExtension.getConfigurationService()
      .getOpenSearchConfiguration()
      .setNestedDocumentsLimit(oldNestedDocumentLimit);
    initializeSchema();
  }

  @Test
  public void dynamicSettingsAreUpdatedForExistingIndexesWhenNewIndexesAreCreated() throws IOException {
    // given schema exists
    initializeSchema();

    // with a different dynamic setting than default
    final List<IndexMappingCreator<IndexSettings.Builder>> mappings = getSchemaManager().getMappings();
    modifyDynamicIndexSetting(mappings);

    // one index is missing so recreating of indexes is triggered
    embeddedOptimizeExtension.getOptimizeDatabaseClient()
      .deleteIndex(indexNameService.getOptimizeIndexAliasForIndex(new SingleDecisionReportIndexES()));

    // when
    initializeSchema();

    // then the settings contain values from configuration
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);

    assertMappingSettings(mappings, getSettingsResponse);
  }

  @Override
  protected void initializeSchema() {
    getSchemaManager().initializeSchema(getOpenSearchOptimizeClient());
  }

  @Override
  protected Class<? extends Exception> expectedDatabaseExtensionStatusException() {
    return OpenSearchException.class;
  }

  private static Settings buildStaticSettings(IndexMappingCreator<IndexSettings.Builder> indexMappingCreator,
                                              ConfigurationService configurationService) throws IOException {
    // TODO fix with OPT-7455
    throw new NotImplementedException("Will be implemented with OPT-7455");
//    IndexSettings.Builder builder = jsonBuilder();
//    // @formatter:off
//    builder
//      .startObject();
//    indexMappingCreator.getStaticSettings(builder, configurationService)
//      .endObject();
//    // @formatter:on
//    return Settings.builder().loadFromSource(Strings.toString(builder), XContentType.JSON).build();
  }

  protected OpenSearchSchemaManager getSchemaManager() {
    return getBean(OpenSearchSchemaManager.class);
  }

  private void assertThatNewFieldExists() throws IOException {
    final String aliasForIndex = indexNameService.getOptimizeIndexAliasForIndex(METADATA_INDEX_NAME);
    // TODO fix with OPT-7455
    throw new NotImplementedException("Will be implemented with OPT-7455");

//    GetFieldMappingsRequest request = new GetFieldMappingsRequest()
//      .indices(aliasForIndex)
//      .fields(MyUpdatedEventIndex.MY_NEW_FIELD);
//    GetFieldMappingsResponse response =
//      prefixAwareDatabaseClient.getHighLevelClient()
//        .indices()
//        .getFieldMapping(request, prefixAwareDatabaseClient.requestOptions());
//
//    final MyUpdatedEventIndex updatedEventType = new MyUpdatedEventIndex();
//    final GetFieldMappingsResponse.FieldMappingMetadata fieldEntry =
//      response.fieldMappings(
//        indexNameService.getOptimizeIndexNameWithVersion(updatedEventType),
//        MyUpdatedEventIndex.MY_NEW_FIELD
//      );
//
//    assertThat(fieldEntry).isNotNull();
  }

  private void assertMappingSettings(final List<IndexMappingCreator<IndexSettings.Builder>> mappings,
                                     final GetSettingsResponse getSettingsResponse) throws IOException {
    // TODO fix with OPT-7455
    throw new NotImplementedException("Will be implemented with OPT-7455");
//    for (IndexMappingCreator<IndexSettings.Builder> mapping : mappings) {
//      Settings dynamicSettings = OpenSearchIndexSettingsBuilder.buildDynamicSettings(
//        embeddedOptimizeExtension.getConfigurationService());
//      dynamicSettings.names().forEach(
//        settingName -> {
//          final String setting = getSettingsResponse.getSetting(
//            indexNameService.getOptimizeIndexNameWithVersion(mapping),
//            "index." + settingName
//          );
//          assertThat(setting)
//            .as("Dynamic setting %s of index %s", settingName, mapping.getIndexName())
//            .isEqualTo(dynamicSettings.get(settingName));
//        });
//      Settings staticSettings =
//        buildStaticSettings(mapping, embeddedOptimizeExtension.getConfigurationService());
//      staticSettings.keySet().forEach(
//        settingName -> {
//          final String setting = getSettingsResponse.getSetting(
//            indexNameService.getOptimizeIndexNameWithVersion(mapping),
//            "index." + settingName
//          );
//          assertThat(setting)
//            .as("Static setting %s of index %s", settingName, mapping.getIndexName())
//            .isEqualTo(staticSettings.get(settingName));
//        });
//    }
  }

  private GetSettingsResponse getIndexSettingsFor(final List<IndexMappingCreator<IndexSettings.Builder>> mappings) throws
                                                                                                                   IOException {
    // TODO fix with OPT-7455
    throw new NotImplementedException("Will be implemented with OPT-7455");
//    final String indices = mappings.stream()
//      .map(indexNameService::getOptimizeIndexNameWithVersion)
//      .collect(Collectors.joining(","));
//
//    Response response = prefixAwareDatabaseClient.getLowLevelClient().performRequest(
//      new Request(HttpGet.METHOD_NAME, "/" + indices + "/_settings")
//    );
//    return GetSettingsResponse.fromXContent(JsonXContent.jsonXContent.createParser(
//      NamedXContentRegistry.EMPTY,
//      DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
//      response.getEntity().getContent()
//    ));
  }

  private void modifyDynamicIndexSetting(final List<IndexMappingCreator<IndexSettings.Builder>> mappings) throws IOException {
    // TODO fix with OPT-7455
    throw new NotImplementedException("Will be implemented with OPT-7455");
  }
//    for (IndexMappingCreator<IndexSettings.Builder> mapping : mappings) {
//      final String indexName = indexNameService.getOptimizeIndexNameWithVersion(mapping);
//      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest(indexName);
//      updateSettingsRequest.settings(Settings.builder().put(MAX_NGRAM_DIFF, "10").build());
//      prefixAwareDatabaseClient.getHighLevelClient()
//        .indices().putSettings(updateSettingsRequest, prefixAwareDatabaseClient.requestOptions());
//    }
//  }

  private OptimizeOpenSearchClient getOpenSearchOptimizeClient() {
    return (OptimizeOpenSearchClient) prefixAwareDatabaseClient;
  }

}
