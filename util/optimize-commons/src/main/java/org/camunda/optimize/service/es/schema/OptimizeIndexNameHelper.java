package org.camunda.optimize.service.es.schema;

public class OptimizeIndexNameHelper {
  public static final String OPTIMIZE_INDEX_PREFIX = "optimize-";

  private OptimizeIndexNameHelper() {
  }

  public static String getOptimizeIndexAliasForType(String type) {
    String original = OPTIMIZE_INDEX_PREFIX + type;
    return original.toLowerCase();
  }

  public static String getVersionedOptimizeIndexNameForTypeMapping(final TypeMappingCreator typeMappingCreator) {
    return getOptimizeIndexNameForAliasAndVersion(
      getOptimizeIndexAliasForType(typeMappingCreator.getType()),
      String.valueOf(typeMappingCreator.getVersion())
    );
  }

  public static String getOptimizeIndexNameForAliasAndVersion(final String indexAlias, final String version) {
    final String versionSuffix = version != null ? "_v" + version : "";
    return indexAlias + versionSuffix;
  }
}