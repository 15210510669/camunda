/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class OptimizeIndexNameService implements ConfigurationReloadable {

  @Getter
  private String indexPrefix;

  @Autowired
  public OptimizeIndexNameService(final ConfigurationService configurationService) {
    this.indexPrefix = configurationService.getEsIndexPrefix();
  }

  public OptimizeIndexNameService(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public String getOptimizeIndexAliasForIndex(String index) {
    return getOptimizeIndexAliasForIndexNameAndPrefix(index, indexPrefix);
  }

  /**
   * This will suffix the indices that are created from templates with their initial suffix
   */
  public String getOptimizeIndexNameWithVersion(final IndexMappingCreator indexMappingCreator) {
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator) + indexMappingCreator.getIndexNameInitialSuffix();
  }

  /**
   * This will suffix the wildcard for any indices that get rolled over, which is not compatible with all ES APIs
   */
  public String getOptimizeIndexNameWithVersionForAllIndicesOf(final IndexMappingCreator indexMappingCreator) {
    if (StringUtils.isNotEmpty(indexMappingCreator.getIndexNameInitialSuffix())) {
      return getOptimizeIndexNameWithVersionWithWildcardSuffix(indexMappingCreator);
    }
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator);
  }

  public String getOptimizeIndexNameWithVersionWithWildcardSuffix(final IndexMappingCreator indexMappingCreator) {
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator)
      // match all indices of that version with this wildcard, which also catches potentially rolled over indices
      + "*";
  }

  public String getOptimizeIndexNameWithVersionWithoutSuffix(final IndexMappingCreator indexMappingCreator) {
    return getOptimizeIndexNameForAliasAndVersion(
      getOptimizeIndexAliasForIndex(indexMappingCreator.getIndexName()),
      String.valueOf(indexMappingCreator.getVersion())
    );
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    this.indexPrefix = context.getBean(ConfigurationService.class).getEsIndexPrefix();
  }

  public static String getOptimizeIndexNameForAliasAndVersion(final String indexAlias, final String version) {
    final String versionSuffix = version != null ? "_v" + version : "";
    return indexAlias + versionSuffix;
  }

  public static String getOptimizeIndexAliasForIndexNameAndPrefix(final String indexName, final String indexPrefix) {
    String original = indexName;
    if (!indexName.startsWith(indexPrefix)) {
      original = String.join("-", indexPrefix, indexName);
    }
    return original.toLowerCase();
  }

}