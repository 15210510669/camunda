/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.schema;

import static io.camunda.operate.util.CollectionUtil.map;

import com.google.common.collect.Maps;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.migration.SemanticVersion;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IndexSchemaValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexSchemaValidator.class);

  private static final Pattern VERSION_PATTERN = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+.*)_.*");

  @Autowired Set<IndexDescriptor> indexDescriptors;

  @Autowired SchemaManager schemaManager;

  @Autowired private OperateProperties operateProperties;

  private Set<String> getAllIndexNamesForIndex(final String index) {
    final String indexPattern = String.format("%s-%s*", getIndexPrefix(), index);
    LOGGER.debug("Getting all indices for {}", indexPattern);
    final Set<String> indexNames = schemaManager.getIndexNames(indexPattern);
    // since we have indices with similar names, we need to additionally filter index names
    // e.g. task and task-variable
    final String patternWithVersion = String.format("%s-%s-\\d.*", getIndexPrefix(), index);
    return indexNames.stream()
        .filter(n -> n.matches(patternWithVersion))
        .collect(Collectors.toSet());
  }

  private String getIndexPrefix() {
    return schemaManager.getIndexPrefix();
  }

  public Set<String> newerVersionsForIndex(final IndexDescriptor indexDescriptor) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    final Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> SemanticVersion.fromVersion(version).isNewerThan(currentVersion))
        .collect(Collectors.toSet());
  }

  public Set<String> olderVersionsForIndex(final IndexDescriptor indexDescriptor) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    final Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> currentVersion.isNewerThan(SemanticVersion.fromVersion(version)))
        .collect(Collectors.toSet());
  }

  private Set<String> versionsForIndex(final IndexDescriptor indexDescriptor) {
    final Set<String> allIndexNames = getAllIndexNamesForIndex(indexDescriptor.getIndexName());
    return allIndexNames.stream()
        .map(this::getVersionFromIndexName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  private Optional<String> getVersionFromIndexName(final String indexName) {
    final Matcher matcher = VERSION_PATTERN.matcher(indexName);
    if (matcher.matches() && matcher.groupCount() > 0) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  /**
   * Validates whether there are no more than one older versions, which would mean not finished
   * migration. Or whether there are newer index versions, meaning that older Operate version is
   * run.
   */
  public void validateIndexVersions() {
    if (!hasAnyOperateIndices()) {
      return;
    }
    final Set<String> errors = new HashSet<>();
    indexDescriptors.forEach(
        indexDescriptor -> {
          final Set<String> oldVersions = olderVersionsForIndex(indexDescriptor);
          final Set<String> newerVersions = newerVersionsForIndex(indexDescriptor);
          if (oldVersions.size() > 1) {
            errors.add(
                String.format(
                    "More than one older version for %s (%s) found: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), oldVersions));
          }
          if (!newerVersions.isEmpty()) {
            errors.add(
                String.format(
                    "Newer version(s) for %s (%s) already exists: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), newerVersions));
          }
        });
    if (!errors.isEmpty()) {
      throw new OperateRuntimeException("Error(s) in index schema: " + String.join(";", errors));
    }
  }

  public boolean hasAnyOperateIndices() {
    final Set<String> indices = schemaManager.getIndexNames(schemaManager.getIndexPrefix() + "*");
    return !indices.isEmpty();
  }

  public boolean schemaExists() {
    try {
      final Set<String> indices = schemaManager.getIndexNames(schemaManager.getIndexPrefix() + "*");
      final List<String> allIndexNames =
          map(indexDescriptors, IndexDescriptor::getFullQualifiedName);

      final Set<String> aliases =
          schemaManager.getAliasesNames(schemaManager.getIndexPrefix() + "*");
      final List<String> allAliasesNames = map(indexDescriptors, IndexDescriptor::getAlias);

      return indices.containsAll(allIndexNames) && aliases.containsAll(allAliasesNames);
    } catch (final Exception e) {
      LOGGER.error("Check for existing schema failed", e);
      return false;
    }
  }

  /**
   * Validates existing indices mappings against schema files defined in codebase.
   *
   * @return newFields map with the new field definitions per index
   * @throws OperateRuntimeException in case some fields would need to be deleted or have different
   *     settings
   */
  public Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndexMappings() {
    final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields = new HashMap<>();
    final Map<String, IndexMapping> indexMappings =
        schemaManager.getIndexMappings(schemaManager.getIndexPrefix() + "*");
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final Map<String, IndexMapping> indexMappingsGroup =
          filterIndexMappings(indexMappings, indexDescriptor);
      // we don't check indices that were not yet created
      if (!indexMappingsGroup.isEmpty()) {
        final IndexMappingDifference difference =
            getDifference(indexDescriptor, indexMappingsGroup);
        validateDifferenceAndCollectNewFields(indexDescriptor, difference, newFields);
      }
    }
    return newFields;
  }

  private IndexMappingDifference getDifference(
      final IndexDescriptor indexDescriptor, final Map<String, IndexMapping> indexMappingsGroup) {
    return getIndexMappingDifference(indexDescriptor, indexMappingsGroup);
  }

  private void validateDifferenceAndCollectNewFields(
      final IndexDescriptor indexDescriptor,
      final IndexMappingDifference difference,
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    if (difference != null && !difference.isEqual()) {
      LOGGER.debug(
          String.format(
              "Index fields differ from expected. Index name: %s. Difference: %s.",
              indexDescriptor.getIndexName(), difference));
      if (!difference.getEntriesDiffering().isEmpty()) {
        final String errorMsg =
            String.format(
                "Index name: %s. Not supported index changes are introduced. Data migration is required. Changes found: %s",
                indexDescriptor.getIndexName(), difference.getEntriesDiffering());
        LOGGER.error(errorMsg);
        throw new OperateRuntimeException(errorMsg);
      } else if (!difference.getEntriesOnlyOnRight().isEmpty()) {
        final String message =
            String.format(
                "Index name: %s. Field deletion is requested, will be ignored. Fields: %s",
                indexDescriptor.getIndexName(), difference.getEntriesOnlyOnRight());
        LOGGER.info(message);
      } else if (!difference.getEntriesOnlyOnLeft().isEmpty()) {
        newFields.put(indexDescriptor, difference.getEntriesOnlyOnLeft());
      }
    } else {
      LOGGER.debug(
          String.format(
              "Index fields are up to date. Index name: %s.", indexDescriptor.getIndexName()));
    }
  }

  private IndexMappingDifference getIndexMappingDifference(
      final IndexDescriptor indexDescriptor, final Map<String, IndexMapping> indexMappingsGroup) {
    final IndexMapping indexMappingMustBe = schemaManager.getExpectedIndexFields(indexDescriptor);

    IndexMappingDifference difference = null;
    // compare every index in group
    for (final Map.Entry<String, IndexMapping> singleIndexMapping : indexMappingsGroup.entrySet()) {
      // Defer to the builder to exclude fields that would normally be detected as "changed" if
      // either the actual or expected is dynamic. This is done so that when data is inserted
      // that adds subfields dynamically (such as when an object is inserted), it does not get
      // picked up by validation and cause a false failure
      final IndexMappingDifference currentDifference =
          new IndexMappingDifference.IndexMappingDifferenceBuilder()
              .setIgnoreDynamicDifferences(true)
              .setLeft(indexMappingMustBe)
              .setRight(singleIndexMapping.getValue())
              .build();
      if (!currentDifference.isEqual()) {
        if (difference == null) {
          difference = currentDifference;
        } else if (!difference.equals(currentDifference)) {
          throw new OperateRuntimeException(
              "Ambiguous schema update. First bring runtime and date indices to one schema. Difference 1: "
                  + difference
                  + ". Difference 2: "
                  + currentDifference);
        }
      }
    }
    return difference;
  }

  /**
   * Leave only runtime and dated indices that correspond to the given IndexDescriptor.
   *
   * @param indexMappings
   * @param indexDescriptor
   * @return
   */
  private Map<String, IndexMapping> filterIndexMappings(
      final Map<String, IndexMapping> indexMappings, final IndexDescriptor indexDescriptor) {
    return Maps.filterEntries(
        indexMappings,
        e -> e.getKey().matches(indexDescriptor.getAllVersionsIndexNameRegexPattern()));
  }
}
