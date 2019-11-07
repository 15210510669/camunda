/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
public class CollectionIndex extends StrictIndexMappingCreator {

  public static final int VERSION = 3;

  @Override
  public String getIndexName() {
    return COLLECTION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    XContentBuilder newBuilder = xContentBuilder
    .startObject(BaseCollectionDefinitionDto.Fields.id.name())
      .field("type", "keyword")
    .endObject()
    .startObject(BaseCollectionDefinitionDto.Fields.name.name())
      .field("type", "keyword")
    .endObject()
    .startObject(BaseCollectionDefinitionDto.Fields.lastModified.name())
      .field("type", "date")
      .field("format", OPTIMIZE_DATE_FORMAT)
    .endObject()
    .startObject(BaseCollectionDefinitionDto.Fields.created.name())
      .field("type", "date")
      .field("format", OPTIMIZE_DATE_FORMAT)
    .endObject()
    .startObject(BaseCollectionDefinitionDto.Fields.owner.name())
      .field("type", "keyword")
    .endObject()
    .startObject(BaseCollectionDefinitionDto.Fields.lastModifier.name())
      .field("type", "keyword")
    .endObject();
    newBuilder = addDataField(newBuilder);
    return newBuilder;
     // @formatter:on
  }

  private XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    final XContentBuilder contentBuilder = xContentBuilder.
      startObject(BaseCollectionDefinitionDto.Fields.data.name())
        .field("type", "nested")
        .startObject("properties")
          .startObject(CollectionDataDto.Fields.configuration.name())
            .field("enabled", false)
          .endObject();
          addRolesField(contentBuilder);
          addScopeField(contentBuilder)
        .endObject()
      .endObject();
    // @formatter:on
    return contentBuilder;
  }

  private XContentBuilder addScopeField(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(CollectionDataDto.Fields.scope.name())
        .field("type", "nested")
        .startObject("properties")
          .startObject(CollectionScopeEntryDto.Fields.definitionKey.name())
            .field("type", "keyword")
          .endObject()
          .startObject(CollectionScopeEntryDto.Fields.definitionType.name())
            .field("type", "keyword")
          .endObject()
          .startObject(CollectionScopeEntryDto.Fields.id.name())
            .field("type", "keyword")
          .endObject()
          .startObject(CollectionScopeEntryDto.Fields.tenants.name())
            .field("type", "keyword")
          .endObject()
        .endObject()
      .endObject();

    // @formatter:on
  }

    private XContentBuilder addRolesField(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder.
      startObject(CollectionDataDto.Fields.roles.name())
        .field("type", "nested")
        .startObject("properties")
          .startObject(CollectionRoleDto.Fields.id.name())
              .field("type", "keyword")
          .endObject()
          .startObject(CollectionRoleDto.Fields.identity.name())
            .field("type", "object")
            .startObject("properties")
              .startObject(IdentityDto.Fields.id.name())
                .field("type", "keyword")
              .endObject()
              .startObject(IdentityDto.Fields.type.name())
                .field("type", "keyword")
              .endObject()
            .endObject()
          .endObject()
          .startObject(CollectionRoleDto.Fields.role.name())
            .field("type", "keyword")
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }

}
