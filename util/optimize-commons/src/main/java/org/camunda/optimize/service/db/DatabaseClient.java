/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db;

import lombok.Getter;
import org.apache.tika.utils.StringUtils;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class DatabaseClient implements ConfigurationReloadable {

  /**
   * Get all the aliases for the indexes matching the indexNamePattern
   *
   * @param indexNamePattern Pattern for the name of an index, may contain wildcards
   * @return A Map where the keys are the name of the matching indexes and the value is a set containing the aliases
   * for the respective index. This map can have multiple keys because indexNamePattern may contain wildcards
   * @throws IOException
   */
  public abstract Map<String, Set<String>> getAliasesForIndexPattern(final String indexNamePattern) throws IOException;

  public abstract Set<String> getAllIndicesForAlias(final String aliasName) throws IOException;

  public abstract boolean triggerRollover(final String indexAliasName, final int maxIndexSizeGB);

  public abstract void deleteIndex(final String indexAlias);

  @Getter
  protected OptimizeIndexNameService indexNameService;

  public abstract <T> long count(final String[] indexNames, final T query) throws IOException;

  //todo will be handle in the OPT-7469
  public abstract SearchResponse scroll(final SearchScrollRequest scrollRequest) throws IOException;

  //todo will be handle in the OPT-7469
  public abstract SearchResponse search(final SearchRequest searchRequest) throws IOException;

  //todo will be handle in the OPT-7469
  public abstract ClearScrollResponse clearScroll(final ClearScrollRequest clearScrollRequest) throws IOException;

  public abstract String getElasticsearchVersion() throws IOException;

  public abstract void setDefaultRequestOptions();

  public abstract void update(final String indexName, final String entityId, final ScriptData script);

  public abstract void executeImportRequestsAsBulk(final String bulkRequestName,
                                                   final List<ImportRequestDto> importRequestDtos,
                                                   final Boolean retryFailedRequestsOnNestedDocLimit);

  public abstract Set<String> performSearchDefinitionQuery(final String indexName,
                                                           final String definitionXml,
                                                           final String definitionIdField,
                                                           final int maxPageSize,
                                                           final String engineAlias);

  protected String[] convertToPrefixedAliasNames(final String[] indices) {
    return Arrays.stream(indices)
      .map(this::convertToPrefixedAliasName)
      .toArray(String[]::new);
  }

  protected String convertToPrefixedAliasName(final String index) {
    final boolean hasExcludePrefix = '-' == index.charAt(0);
    final String rawIndexName = hasExcludePrefix ? index.substring(1) : index;
    final String prefixedIndexName = indexNameService.getOptimizeIndexAliasForIndex(rawIndexName);
    return hasExcludePrefix ? "-" + prefixedIndexName : prefixedIndexName;
  }

  protected void validateOperationParams(final ImportRequestDto importRequestDto) {
    if (Objects.isNull(importRequestDto.getType())) {
      throw new OptimizeRuntimeException(String.format(
        "The %s param of ImportRequestDto is not set for request",
        ImportRequestDto.Fields.type.name()
      ));
    }
    if (StringUtils.isBlank(importRequestDto.getIndexName())) {
      throw new OptimizeRuntimeException(generateErrorMessageForValidationImportRequestDto(
        importRequestDto.getType(),
        ImportRequestDto.Fields.indexName.name()
      ));
    }
    if (StringUtils.isBlank(importRequestDto.getId())) {
      throw new OptimizeRuntimeException(generateErrorMessageForValidationImportRequestDto(
        importRequestDto.getType(),
        ImportRequestDto.Fields.id.name()
      ));
    }
    switch (importRequestDto.getType()) {
      case INDEX -> {
        if (Objects.isNull(importRequestDto.getSource())) {
          throw new OptimizeRuntimeException(generateErrorMessageForValidationImportRequestDto(
            RequestType.INDEX,
            ImportRequestDto.Fields.source.name()
          ));
        }
      }
      case UPDATE -> {
        if (Objects.isNull(importRequestDto.getScriptData())) {
          throw new OptimizeRuntimeException(generateErrorMessageForValidationImportRequestDto(
            RequestType.UPDATE,
            ImportRequestDto.Fields.scriptData.name()
          ));
        }
      }
    }
  }

  protected boolean validateImportName(final ImportRequestDto importRequestDto) {
    if (StringUtils.isBlank(importRequestDto.getImportName())) {
      throw new OptimizeRuntimeException(generateErrorMessageForValidationImportRequestDto(
        importRequestDto.getType(),
        ImportRequestDto.Fields.importName.name()
      ));
    }
    return true;
  }

  private String generateErrorMessageForValidationImportRequestDto(final RequestType type, final String fieldName) {
    return String.format("The %s param of ImportRequestDto is not valid for request type %s", fieldName, type);
  }

}
