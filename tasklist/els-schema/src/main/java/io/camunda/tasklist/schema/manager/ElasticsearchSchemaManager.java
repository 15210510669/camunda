/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.manager;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistElasticsearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.AbstractIndexDescriptor;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
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

@Component("tasklistSchemaManager")
@Profile("!test")
@Conditional(ElasticSearchCondition.class)
public class ElasticsearchSchemaManager implements SchemaManager {

  public static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  public static final String INDEX_LIFECYCLE_NAME = "index.lifecycle.name";
  public static final String DELETE_PHASE = "delete";

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  private static final String NUMBER_OF_SHARDS = "index.number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "index.number_of_replicas";

  @Autowired protected RetryElasticsearchClient retryElasticsearchClient;

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired private List<AbstractIndexDescriptor> indexDescriptors;

  @Autowired private List<TemplateDescriptor> templateDescriptors;

  @Override
  public void createSchema() {
    if (tasklistProperties.getArchiver().isIlmEnabled()) {
      createIndexLifeCycles();
    }
    createDefaults();
    createTemplates();
    createIndices();
  }

  private String settingsTemplateName() {
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    return String.format("%s_template", elsConfig.getIndexPrefix());
  }

  private Settings getIndexSettings() {
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, elsConfig.getNumberOfShards())
        .put(NUMBER_OF_REPLICAS, elsConfig.getNumberOfReplicas())
        .build();
  }

  private void createDefaults() {
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();

    final String settingsTemplateName = settingsTemplateName();
    LOGGER.info(
        "Create default settings '{}' with {} shards and {} replicas per index.",
        settingsTemplateName,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    final Settings settings = getIndexSettings();
    final Template template = new Template(settings, null, null);
    final ComponentTemplate settingsTemplate = new ComponentTemplate(template, null, null);
    final PutComponentTemplateRequest request =
        new PutComponentTemplateRequest()
            .name(settingsTemplateName)
            .componentTemplate(settingsTemplate);

    retryElasticsearchClient.createComponentTemplate(request);
  }

  public void createIndexLifeCycles() {
    final TimeValue timeValue =
        TimeValue.parseTimeValue(
            tasklistProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices(),
            "IndexLifeCycle " + INDEX_LIFECYCLE_NAME);
    LOGGER.info(
        "Create Index Lifecycle {} for min age of {} ",
        TASKLIST_DELETE_ARCHIVED_INDICES,
        timeValue.getStringRep());
    final Map<String, Phase> phases = new HashMap<>();
    final Map<String, LifecycleAction> deleteActions =
        Collections.singletonMap(DeleteAction.NAME, new DeleteAction());
    phases.put(DELETE_PHASE, new Phase(DELETE_PHASE, timeValue, deleteActions));

    final LifecyclePolicy policy = new LifecyclePolicy(TASKLIST_DELETE_ARCHIVED_INDICES, phases);
    retryElasticsearchClient.putLifeCyclePolicy(new PutLifecyclePolicyRequest(policy));
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename =
        String.format("/schema/es/create/index/tasklist-%s.json", indexDescriptor.getIndexName());
    final Map<String, Object> indexDescription = readJSONFileToMap(indexFilename);
    createIndex(
        new CreateIndexRequest(indexDescriptor.getFullQualifiedName())
            .source(indexDescription)
            .aliases(Set.of(new Alias(indexDescriptor.getAlias()).writeIndex(false)))
            .settings(getIndexSettings()),
        indexDescriptor.getFullQualifiedName());
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    final Template template = getTemplateFrom(templateDescriptor);
    final ComposableIndexTemplate composableTemplate =
        new ComposableIndexTemplate.Builder()
            .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
            .template(template)
            .componentTemplates(List.of(settingsTemplateName()))
            .build();
    putIndexTemplate(
        new PutComposableIndexTemplateRequest()
            .name(templateDescriptor.getTemplateName())
            .indexTemplate(composableTemplate));
    // This is necessary, otherwise tasklist won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    createIndex(new CreateIndexRequest(indexName), indexName);
  }

  private Template getTemplateFrom(final TemplateDescriptor templateDescriptor) {
    final String templateFilename =
        String.format(
            "/schema/es/create/template/tasklist-%s.json", templateDescriptor.getIndexName());
    final Map<String, Object> templateConfig = readJSONFileToMap(templateFilename);
    final PutIndexTemplateRequest ptr =
        new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(templateConfig);
    try {
      final Map<String, AliasMetadata> aliases =
          Map.of(
              templateDescriptor.getAlias(),
              AliasMetadata.builder(templateDescriptor.getAlias()).build());
      return new Template(ptr.settings(), new CompressedXContent(ptr.mappings()), aliases);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  private Map<String, Object> readJSONFileToMap(final String filename) {
    final Map<String, Object> result;
    try (final InputStream inputStream =
        ElasticsearchSchemaManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        result = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new TasklistRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
    return result;
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, final String indexName) {
    final boolean created = retryElasticsearchClient.createIndex(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void putIndexTemplate(final PutComposableIndexTemplateRequest request) {
    final boolean created = retryElasticsearchClient.createTemplate(request);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }
}
