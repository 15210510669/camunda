/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.indexes.RenameFieldTestIndex;
import org.camunda.optimize.upgrade.indexes.UserTestIndex;
import org.camunda.optimize.upgrade.indexes.UserTestUpdatedMappingIndex;
import org.camunda.optimize.upgrade.indexes.UserTestWithTemplateIndex;
import org.camunda.optimize.upgrade.indexes.UserTestWithTemplateUpdatedMappingIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.DeleteDataStep;
import org.camunda.optimize.upgrade.steps.document.InsertDataStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateMappingIndexStep;
import org.camunda.optimize.upgrade.util.UpgradeUtil;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UpgradeStepsIT extends AbstractUpgradeIT {

  private static final IndexMappingCreator TEST_INDEX_V1 = new UserTestIndex(1);
  private static final IndexMappingCreator TEST_INDEX_V2 = new UserTestIndex(2);
  private static final IndexMappingCreator TEST_INDEX_WITH_UPDATED_MAPPING = new UserTestUpdatedMappingIndex();
  private static final IndexMappingCreator TEST_INDEX_WITH_TEMPLATE = new UserTestWithTemplateIndex();
  private static final IndexMappingCreator TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING =
    new UserTestWithTemplateUpdatedMappingIndex();

  private static final String FROM_VERSION = "2.6.0";
  private static final String TO_VERSION = "2.7.0";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(METADATA_INDEX));
    setMetadataIndexVersion(FROM_VERSION);
  }

  @Test
  public void executeCreateIndexWithAliasStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    final String versionedIndexName = indexNameService
      .getOptimizeIndexNameWithVersionForAllIndicesOf(TEST_INDEX_WITH_UPDATED_MAPPING);
    assertThat(
      prefixAwareClient.getHighLevelClient().indices().exists(
        new GetIndexRequest(versionedIndexName).features(GetIndexRequest.Feature.MAPPINGS),
        RequestOptions.DEFAULT
      )
    ).isTrue();
    final GetAliasesResponse alias = getAliasesForAlias(
      indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_WITH_UPDATED_MAPPING.getIndexName()));
    assertThatIndexIsSetAsWriteIndex(versionedIndexName, alias);
  }

  @Test
  public void executeCreateTemplateBasedIndexWithAliasStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_WITH_TEMPLATE))
        .build();

    // when
    upgradePlan.execute();

    // then
    final String versionedIndexName = indexNameService
      .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE);
    assertThat(
      prefixAwareClient.getHighLevelClient().indices().exists(
        new GetIndexRequest(versionedIndexName).features(GetIndexRequest.Feature.MAPPINGS),
        RequestOptions.DEFAULT
      )
    ).isTrue();
    final GetAliasesResponse alias = getAliasesForAlias(
      indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_WITH_TEMPLATE.getIndexName()));
    assertThatIndexIsSetAsWriteIndex(versionedIndexName, alias);
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexStep() {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_UPDATED_MAPPING)).isTrue();
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexStep_preexistingIndexWithoutAliasWriteIndexFlag() {
    // given
    final String aliasForIndex = indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_V1.getIndexName());
    createIndexWithoutWriteIndexFlagOnAlias(aliasForIndex);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_UPDATED_MAPPING)).isTrue();
    // even though not being set before the writeIndex flag is now set
    final GetAliasesResponse alias = getAliasesForAlias(aliasForIndex);
    assertThatIndexIsSetAsWriteIndex(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_UPDATED_MAPPING),
      alias
    );
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexWithAliasFromTemplateStep() {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_WITH_TEMPLATE))
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING)).isTrue();

    final Map<String, Object> mappingFields = getMappingFields();
    assertThat(mappingFields).containsKey("email");
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexFromTemplateStep_preexistingIndexWasNotFromTemplateAndLackedAliasWriteIndexFlag() {
    // given
    final String aliasForIndex = indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_V1.getIndexName());
    createIndexWithoutWriteIndexFlagOnAlias(aliasForIndex);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING)).isTrue();

    final Map<String, Object> mappingFields = getMappingFields();
    assertThat(mappingFields).containsKey("email");

    // even though not being set before the writeIndex flag is now set
    assertThatIndexIsSetAsWriteIndex(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING),
      getAliasesForAlias(aliasForIndex)
    );
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexFromTemplateStep_preexistingIndexHadWriteAndReadAlias() {
    // given
    final String aliasForIndex = indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_V1.getIndexName());
    final String readOnlyAliasForIndex = indexNameService.getOptimizeIndexAliasForIndex("im-read-only");

    final CreateIndexRequest request = new CreateIndexRequest(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V1)
    );
    request.alias(new Alias(aliasForIndex).writeIndex(true));
    request.alias(new Alias(readOnlyAliasForIndex).writeIndex(false));
    request.mapping(TEST_INDEX_V1.getSource());
    prefixAwareClient.getHighLevelClient().indices().create(request, RequestOptions.DEFAULT);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING)).isTrue();

    final String versionedIndexName = indexNameService
      .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING);
    assertThatIndexIsSetAsWriteIndex(versionedIndexName, getAliasesForAlias(aliasForIndex));

    assertThat(getAliasesForAlias(readOnlyAliasForIndex).getAliases())
      .hasSize(1)
      .extractingByKey(versionedIndexName)
      .satisfies(aliasMetaData -> assertThat(aliasMetaData)
        .hasSize(1)
        .extracting(AliasMetaData::writeIndex)
        .containsExactly(false)
      );
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexWithTemplateAfterRolloverStep() {
    // given rolled over users index
    UpgradePlan buildIndexPlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_WITH_TEMPLATE))
        .build();

    buildIndexPlan.execute();

    ElasticsearchWriterUtil.triggerRollover(prefixAwareClient, TEST_INDEX_WITH_TEMPLATE.getIndexName(), 0);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING))
        .build();

    // when update index after rollover
    upgradePlan.execute();

    // then optimize-users write alias points to updated users index
    final String expectedSuffixAfterRollover = "-000002";
    final String indexAlias =
      indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING.getIndexName());
    final Map<String, Set<AliasMetaData>> aliasMap = getAliasMap(indexAlias);
    final List<String> indicesWithWriteAlias = aliasMap.entrySet()
      .stream()
      .filter(e -> e.getValue().removeIf(AliasMetaData::writeIndex))
      .map(Map.Entry::getKey)
      .collect(toList());
    final Map<String, Object> mappingFields = getMappingFields();
    assertThat(mappingFields).containsKey("email");
    assertThat(aliasMap.keySet()).hasSize(2);
    assertThat(indicesWithWriteAlias).hasSize(1);
    assertThat(indicesWithWriteAlias.get(0)).contains(expectedSuffixAfterRollover);
  }

  @Test
  public void executeInsertDataStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildInsertDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX_V2.getIndexName()),
      RequestOptions.DEFAULT
    );

    assertThat(searchResponse.getHits())
      .hasSize(1)
      .extracting(SearchHit::getSourceAsMap)
      .extracting("username", "password")
      .containsExactly(new Tuple("admin", "admin"));
  }

  @Test
  public void executeUpdateDataStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildUpdateDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX_V2.getIndexName()),
      RequestOptions.DEFAULT
    );
    assertThat(searchResponse.getHits())
      .hasSize(1)
      .extracting(SearchHit::getSourceAsMap)
      .extracting("username", "password")
      .containsExactly(new Tuple("admin", "admin1"));
  }

  @Test
  public void executeDeleteByQueryDataStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildDeleteDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX_V2.getIndexName()),
      RequestOptions.DEFAULT
    );
    assertThat(searchResponse.getHits().getHits()).isEmpty();
  }

  @Test
  public void executeDeleteIndexStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX_V2))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_V2)).isFalse();
  }

  @Test
  public void executeDeleteIndexStep_rolledOverIndex() throws Exception {
    // given rolled over users index
    UpgradePlan buildIndexPlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_WITH_TEMPLATE))
        .build();

    buildIndexPlan.execute();

    ElasticsearchWriterUtil.triggerRollover(prefixAwareClient, TEST_INDEX_WITH_TEMPLATE.getIndexName(), 0);

    // then two indices exist after the rollover
    boolean indicesExist = prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE);
    assertThat(indicesExist).isTrue();
    final GetIndexResponse response = getIndicesForMapping(TEST_INDEX_WITH_TEMPLATE);
    assertThat(response.getIndices()).hasSize(2);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX_WITH_TEMPLATE))
        .build();

    // when update index after rollover
    upgradePlan.execute();

    // then both the initial and rolled over index are deleted
    indicesExist = prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE);
    assertThat(indicesExist).isFalse();
  }

  @Test
  public void executeUpgradeMappingIndexStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(new UpdateMappingIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    final Map<String, Object> mappingFields = getMappingFields();
    assertThat(mappingFields).containsKey("email");
  }

  @Test
  public void fieldRenameWithoutRemovingOldFieldAbortsUpgrade() throws IOException {
    //given
    createOptimizeIndexWithTypeAndVersion(new RenameFieldTestIndex(), 1);

    IndexRequest indexRequest = new IndexRequest("users")
      .source("{\"name\": \"yuri_loza\"}", XContentType.JSON);

    prefixAwareClient.index(indexRequest, RequestOptions.DEFAULT);

    RefreshRequest refreshRequest = new RefreshRequest("*");
    prefixAwareClient.getHighLevelClient().indices().refresh(refreshRequest, RequestOptions.DEFAULT);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new UpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING, "def foo = \"noop\";"))
        .build();

    // when
    assertThrows(UpgradeRuntimeException.class, upgradePlan::execute);
  }

  @Test
  public void versionIsUpdatedAfterPlanWasExecuted() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(METADATA_INDEX.getIndexName()),
      RequestOptions.DEFAULT
    );
    assertThat(searchResponse.getHits())
      .hasSize(1)
      .extracting(SearchHit::getSourceAsMap)
      .extracting(MetadataIndex.SCHEMA_VERSION)
      .containsExactly(TO_VERSION);
  }

  private GetAliasesResponse getAliasesForAlias(final String readOnlyAliasForIndex) throws IOException {
    return prefixAwareClient.getAlias(new GetAliasesRequest(readOnlyAliasForIndex), RequestOptions.DEFAULT);
  }

  private void assertThatIndexIsSetAsWriteIndex(final String versionedIndexName, final GetAliasesResponse alias) {
    assertThat(alias.getAliases())
      .hasSize(1)
      .extractingByKey(versionedIndexName)
      .satisfies(aliasMetaData -> assertThat(aliasMetaData)
        .hasSize(1)
        .extracting(AliasMetaData::writeIndex)
        .containsExactly(true)
      );
  }

  private void createIndexWithoutWriteIndexFlagOnAlias(final String aliasForIndex) throws IOException {
    final CreateIndexRequest request = new CreateIndexRequest(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V1)
    );
    request.alias(new Alias(aliasForIndex));
    request.mapping(TEST_INDEX_V1.getSource());
    prefixAwareClient.getHighLevelClient().indices().create(request, RequestOptions.DEFAULT);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getMappingFields() throws IOException {
    GetMappingsRequest request = new GetMappingsRequest();
    request.indices(TEST_INDEX_WITH_UPDATED_MAPPING.getIndexName());
    GetMappingsResponse getMappingResponse = prefixAwareClient.getMapping(request, RequestOptions.DEFAULT);
    final Object propertiesMap = getMappingResponse.mappings()
      .values()
      .stream()
      .findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("There should be at least one mapping available for the index!"))
      .getSourceAsMap()
      .get("properties");
    if (propertiesMap instanceof Map) {
      return (Map<String, Object>) propertiesMap;
    } else {
      throw new OptimizeRuntimeException("ElasticSearch index mapping properties should be of type map");
    }
  }

  private InsertDataStep buildInsertDataStep() {
    return new InsertDataStep(
      TEST_INDEX_V2,
      UpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json")
    );
  }

  private CreateIndexStep buildCreateIndexStep(final IndexMappingCreator index) {
    return new CreateIndexStep(index);
  }

  private UpdateIndexStep buildUpdateIndexStep(final IndexMappingCreator index) {
    return new UpdateIndexStep(index, null);
  }

  private UpdateDataStep buildUpdateDataStep() {
    return new UpdateDataStep(
      TEST_INDEX_V2.getIndexName(),
      termQuery("username", "admin"),
      "ctx._source.password = ctx._source.password + \"1\""
    );
  }

  private UpgradeStep buildDeleteDataStep() {
    return new DeleteDataStep(
      TEST_INDEX_V2.getIndexName(),
      QueryBuilders.termQuery("username", "admin")
    );
  }

  @SuppressWarnings("SameParameterValue")
  private DeleteIndexIfExistsStep buildDeleteIndexStep(final IndexMappingCreator indexMapping) {
    return new DeleteIndexIfExistsStep(indexMapping);
  }

  private Map<String, Set<AliasMetaData>> getAliasMap(final String aliasName) {
    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasName);
    try {
      return prefixAwareClient
        .getHighLevelClient()
        .indices()
        .getAlias(aliasesRequest, RequestOptions.DEFAULT)
        .getAliases();
    } catch (IOException e) {
      String message = String.format("Could not retrieve alias map for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private GetIndexResponse getIndicesForMapping(final IndexMappingCreator mapping) throws IOException {
    return prefixAwareClient.getHighLevelClient().indices().get(
      new GetIndexRequest(indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(mapping)),
      RequestOptions.DEFAULT
    );
  }

}
