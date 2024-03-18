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
package io.camunda.operate.schema.opensearch;

import static io.camunda.operate.schema.templates.IncidentTemplate.KEY;
import static io.camunda.operate.schema.templates.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.*;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.ReindexWithQueryAndScriptPlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.Tuple;
import java.util.*;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.reindex.Destination;
import org.opensearch.client.opensearch.core.reindex.Source;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class OpensearchReindexWithQueryAndScriptPlan implements ReindexWithQueryAndScriptPlan {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchReindexWithQueryAndScriptPlan.class);
  private final MigrationProperties migrationProperties;
  private final RichOpenSearchClient richOpenSearchClient;
  private List<Step> steps = List.of();
  private String srcIndex;
  private String dstIndex;
  private String listViewIndexName;

  @Autowired
  public OpensearchReindexWithQueryAndScriptPlan(
      final RichOpenSearchClient richOpenSearchClient,
      final MigrationProperties migrationProperties) {
    this.richOpenSearchClient = richOpenSearchClient;
    this.migrationProperties = migrationProperties;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setSrcIndex(String srcIndex) {
    this.srcIndex = srcIndex;
    return this;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setDstIndex(String dstIndex) {
    this.dstIndex = dstIndex;
    return this;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setListViewIndexName(String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
    return this;
  }

  private Script buildScript(
      final String scriptContent, final Map<String, Tuple<String, String>> bpmnProcessIdsMap) {
    final Map<String, JsonData> paramsMap =
        Map.of("dstIndex", JsonData.of(dstIndex), "bpmnProcessIds", JsonData.of(bpmnProcessIdsMap));
    return new Script.Builder()
        .inline(InlineScript.of(s -> s.lang("painless").source(scriptContent).params(paramsMap)))
        .build();
  }

  private Map<String, Tuple<String, String>> getBpmnProcessIds(Set<Long> processInstanceKeys) {
    final var request =
        searchRequestBuilder(listViewIndexName + "*")
            .query(longTerms(KEY, processInstanceKeys))
            .source(sourceInclude(KEY, BPMN_PROCESS_ID, PROCESS_KEY))
            .size(migrationProperties.getScriptParamsCount());
    record Result(String key, String bpmnProcessId, String processKey) {}
    final Map<String, Tuple<String, String>> results = new HashMap<>();
    richOpenSearchClient
        .doc()
        .scrollWith(
            request,
            Result.class,
            hits ->
                hits.forEach(
                    hit -> {
                      final var source = hit.source();
                      if (source != null) {
                        results.put(
                            source.key(), new Tuple<>(source.bpmnProcessId(), source.processKey()));
                      }
                    }));
    return results;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    // iterate over process instance ids
    final String processInstanceKeyField = "processInstanceKey";
    final var searchRequest =
        searchRequestBuilder(srcIndex + "_*")
            .source(sourceInclude(processInstanceKeyField))
            .sort(sortOptions(processInstanceKeyField, SortOrder.Asc))
            .size(migrationProperties.getScriptParamsCount());
    final Set<Long> processInstanceKeys = new HashSet<>();
    try {
      richOpenSearchClient
          .doc()
          .scrollWith(
              searchRequest,
              Long.class,
              rethrowConsumer(
                  hits -> {
                    final Set<Long> currentProcessInstanceKeys =
                        hits.stream().map(Hit::source).collect(Collectors.toSet());
                    if (processInstanceKeys.size() + currentProcessInstanceKeys.size()
                        >= migrationProperties.getScriptParamsCount()) {
                      final int remainingSize =
                          migrationProperties.getScriptParamsCount() - processInstanceKeys.size();
                      final Set<Long> subSet =
                          currentProcessInstanceKeys.stream()
                              .limit(remainingSize)
                              .collect(Collectors.toSet());
                      currentProcessInstanceKeys.removeAll(subSet);
                      processInstanceKeys.addAll(subSet);

                      reindexPart(processInstanceKeys);

                      processInstanceKeys.clear();
                      processInstanceKeys.addAll(currentProcessInstanceKeys);
                    } else {
                      processInstanceKeys.addAll(currentProcessInstanceKeys);
                    }
                  }));
      if (!processInstanceKeys.isEmpty()) {
        reindexPart(processInstanceKeys);
      }
    } catch (Exception e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  @Override
  public void validateMigrationResults(final SchemaManager schemaManager)
      throws MigrationException {
    final long srcCount = schemaManager.getNumberOfDocumentsFor(srcIndex + "_*");
    final long dstCount = schemaManager.getNumberOfDocumentsFor(dstIndex + "_*");
    if (srcCount != dstCount) {
      throw new MigrationException(
          String.format(
              "Exception occurred when migrating %s. Number of documents in source indices: %s, number of documents in destination indices: %s",
              srcIndex, srcCount, dstCount));
    }
  }

  private void reindexPart(Set<Long> processInstanceKeys) {
    final Map<String, Tuple<String, String>> bpmnProcessIdsMap =
        getBpmnProcessIds(processInstanceKeys);
    LOGGER.debug(
        "Migrate srcIndex: {}, processInstanceKeys: {}, bpmnProcessIdsMap: {}",
        srcIndex,
        processInstanceKeys,
        bpmnProcessIdsMap);
    final String content = steps.get(0).getContent();
    final var reindexRequest =
        new ReindexRequest.Builder()
            .source(
                Source.of(
                    b ->
                        b.index(srcIndex)
                            .query(longTerms(PROCESS_INSTANCE_KEY, processInstanceKeys))
                            .size(migrationProperties.getReindexBatchSize())))
            .dest(Destination.of(b -> b.index(dstIndex + "_")))
            .script(buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT + content, bpmnProcessIdsMap));
    if (migrationProperties.getSlices() > 0) {
      reindexRequest.slices((long) migrationProperties.getSlices());
    }
    richOpenSearchClient.index().reindexWithRetries(reindexRequest.build(), false);
  }

  @Override
  public String toString() {
    return "OpensearchReindexWithQueryAndScriptPlan [steps="
        + steps
        + ",  srcIndex="
        + srcIndex
        + ", dstIndex="
        + dstIndex
        + "]";
  }
}
