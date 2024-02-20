/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.client.indexlifecycle.DeleteAction;
import org.elasticsearch.client.indexlifecycle.LifecycleAction;
import org.elasticsearch.client.indexlifecycle.LifecyclePolicy;
import org.elasticsearch.client.indexlifecycle.Phase;
import org.elasticsearch.client.indexlifecycle.PutLifecyclePolicyRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("schemaManager")
@Profile("!test")
public class ElasticsearchSchemaManager implements SchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  private static final String NUMBER_OF_SHARDS = "index.number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "index.number_of_replicas";
  @Autowired protected RetryElasticsearchClient retryElasticsearchClient;
  @Autowired protected OperateProperties operateProperties;
  @Autowired private List<AbstractIndexDescriptor> indexDescriptors;
  @Autowired private List<TemplateDescriptor> templateDescriptors;

  @Override
  public void createSchema() {
    if (operateProperties.getArchiver().isIlmEnabled()) {
      createIndexLifeCycles();
    }
    createDefaults();
    createTemplates();
    createIndices();
  }

  @Override
  public boolean setIndexSettingsFor(Map<String, ?> settings, String indexPattern) {
    return retryElasticsearchClient.setIndexSettingsFor(
        Settings.builder().loadFromMap(settings).build(), indexPattern);
  }

  @Override
  public String getOrDefaultRefreshInterval(String indexName, String defaultValue) {
    return retryElasticsearchClient.getOrDefaultRefreshInterval(indexName, defaultValue);
  }

  @Override
  public String getOrDefaultNumbersOfReplica(String indexName, String defaultValue) {
    return retryElasticsearchClient.getOrDefaultNumbersOfReplica(indexName, defaultValue);
  }

  @Override
  public void refresh(String indexPattern) {
    retryElasticsearchClient.refresh(indexPattern);
  }

  @Override
  public boolean isHealthy() {
    return retryElasticsearchClient.isHealthy();
  }

  @Override
  public Set<String> getIndexNames(String indexPattern) {
    return retryElasticsearchClient.getIndexNames(indexPattern);
  }

  @Override
  public Set<String> getAliasesNames(String indexPattern) {
    return retryElasticsearchClient.getAliasesNames(indexPattern);
  }

  @Override
  public long getNumberOfDocumentsFor(String... indexPatterns) {
    return retryElasticsearchClient.getNumberOfDocumentsFor(indexPatterns);
  }

  @Override
  public boolean deleteIndicesFor(String indexPattern) {
    return retryElasticsearchClient.deleteIndicesFor(indexPattern);
  }

  @Override
  public boolean deleteTemplatesFor(String deleteTemplatePattern) {
    return retryElasticsearchClient.deleteTemplatesFor(deleteTemplatePattern);
  }

  @Override
  public void removePipeline(String pipelineName) {
    retryElasticsearchClient.removePipeline(pipelineName);
  }

  @Override
  public boolean addPipeline(String name, String pipelineDefinition) {
    return retryElasticsearchClient.addPipeline(name, pipelineDefinition);
  }

  @Override
  public Map<String, String> getIndexSettingsFor(String indexName, String... fields) {
    return retryElasticsearchClient.getIndexSettingsFor(indexName, fields);
  }

  private String settingsTemplateName() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    return String.format("%s_template", elsConfig.getIndexPrefix());
  }

  private Settings getDefaultIndexSettings() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, elsConfig.getNumberOfShards())
        .put(NUMBER_OF_REPLICAS, elsConfig.getNumberOfReplicas())
        .build();
  }

  private Settings getIndexSettings(String indexName) {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    var shards =
        elsConfig
            .getNumberOfShardsForIndices()
            .getOrDefault(indexName, elsConfig.getNumberOfShards());
    var replicas =
        elsConfig
            .getNumberOfReplicasForIndices()
            .getOrDefault(indexName, elsConfig.getNumberOfReplicas());
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, shards)
        .put(NUMBER_OF_REPLICAS, replicas)
        .build();
  }

  private void createDefaults() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    final String settingsTemplate = settingsTemplateName();
    logger.info(
        "Create default settings from '{}' with {} shards and {} replicas per index.",
        settingsTemplate,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    Settings settings = getDefaultIndexSettings();

    final Template template = new Template(settings, null, null);
    final ComponentTemplate componentTemplate = new ComponentTemplate(template, null, null);
    final PutComponentTemplateRequest request =
        new PutComponentTemplateRequest()
            .name(settingsTemplate)
            .componentTemplate(componentTemplate);
    retryElasticsearchClient.createComponentTemplate(request);
  }

  private void createIndexLifeCycles() {
    final TimeValue timeValue =
        TimeValue.parseTimeValue(
            operateProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices(),
            "IndexLifeCycle " + INDEX_LIFECYCLE_NAME);
    logger.info(
        "Create Index Lifecycle {} for min age of {} ",
        OPERATE_DELETE_ARCHIVED_INDICES,
        timeValue.getStringRep());
    Map<String, Phase> phases = new HashMap<>();
    Map<String, LifecycleAction> deleteActions =
        Collections.singletonMap(DeleteAction.NAME, new DeleteAction());
    phases.put(DELETE_PHASE, new Phase(DELETE_PHASE, timeValue, deleteActions));

    LifecyclePolicy policy = new LifecyclePolicy(OPERATE_DELETE_ARCHIVED_INDICES, phases);
    PutLifecyclePolicyRequest request = new PutLifecyclePolicyRequest(policy);
    retryElasticsearchClient.putLifeCyclePolicy(request);
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename =
        String.format(
            "/schema/elasticsearch/create/index/operate-%s.json", indexDescriptor.getIndexName());
    final Map<String, Object> indexDescription = readJSONFileToMap(indexFilename);
    createIndex(
        new CreateIndexRequest(indexDescriptor.getFullQualifiedName())
            .source(indexDescription)
            .aliases(Set.of(new Alias(indexDescriptor.getAlias()).writeIndex(false)))
            .settings(getIndexSettings(indexDescriptor.getIndexName())),
        indexDescriptor.getFullQualifiedName());
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    Template template = getTemplateFrom(templateDescriptor);
    ComposableIndexTemplate composableTemplate =
        new ComposableIndexTemplate.Builder()
            .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
            .template(template)
            .componentTemplates(List.of(settingsTemplateName()))
            .build();
    putIndexTemplate(
        new PutComposableIndexTemplateRequest()
            .name(templateDescriptor.getTemplateName())
            .indexTemplate(composableTemplate));
    // This is necessary, otherwise operate won't find indexes at startup
    String indexName = templateDescriptor.getFullQualifiedName();
    var createIndexRequest =
        new CreateIndexRequest(indexName)
            .aliases(Set.of(new Alias(templateDescriptor.getAlias()).writeIndex(false)))
            .settings(getIndexSettings(templateDescriptor.getIndexName()));
    createIndex(createIndexRequest, indexName);
  }

  private void overrideTemplateSettings(
      final Map<String, Object> templateConfig, final TemplateDescriptor templateDescriptor) {
    Settings indexSettings = getIndexSettings(templateDescriptor.getIndexName());
    Map<String, Object> settings =
        (Map<String, Object>) templateConfig.getOrDefault("settings", new HashMap<>());
    Map<String, Object> index =
        (Map<String, Object>) settings.getOrDefault("index", new HashMap<>());
    index.put("number_of_shards", indexSettings.get(NUMBER_OF_SHARDS));
    index.put("number_of_replicas", indexSettings.get(NUMBER_OF_REPLICAS));
    settings.put("index", index);
    templateConfig.put("settings", settings);
  }

  private Template getTemplateFrom(final TemplateDescriptor templateDescriptor) {
    final String templateFilename =
        String.format(
            "/schema/elasticsearch/create/template/operate-%s.json",
            templateDescriptor.getIndexName());
    // Easiest way to create Template from json file: create 'old' request ang retrieve needed info
    final Map<String, Object> templateConfig = readJSONFileToMap(templateFilename);
    overrideTemplateSettings(templateConfig, templateDescriptor);
    PutIndexTemplateRequest ptr =
        new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(templateConfig);
    try {
      final Map<String, AliasMetadata> aliases =
          Map.of(
              templateDescriptor.getAlias(),
              AliasMetadata.builder(templateDescriptor.getAlias()).build());
      return new Template(ptr.settings(), new CompressedXContent(ptr.mappings()), aliases);
    } catch (IOException e) {
      throw new OperateRuntimeException(
          String.format("Error in reading mappings for %s ", templateDescriptor.getTemplateName()),
          e);
    }
  }

  private Map<String, Object> readJSONFileToMap(final String filename) {
    final Map<String, Object> result;
    try (InputStream inputStream = ElasticsearchSchemaManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        result = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new OperateRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (IOException e) {
      throw new OperateRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
    return result;
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    boolean created = retryElasticsearchClient.createIndex(createIndexRequest);
    if (created) {
      logger.debug("Index [{}] was successfully created", indexName);
    } else {
      logger.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void putIndexTemplate(final PutComposableIndexTemplateRequest request) {
    boolean created = retryElasticsearchClient.createTemplate(request);
    if (created) {
      logger.debug("Template [{}] was successfully created", request.name());
    } else {
      logger.debug("Template [{}] was NOT created", request.name());
    }
  }

  @Override
  public String getIndexPrefix() {
    return operateProperties.getElasticsearch().getIndexPrefix();
  }
}
