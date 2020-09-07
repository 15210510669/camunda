/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.flownode;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByFlowNode extends AbstractGroupByFlowNode {

  private static final String NESTED_EVENTS_AGGREGATION = "nestedEvents";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final FlowNodeExecutionState flowNodeExecutionState = context.getReportConfiguration().getFlowNodeExecutionState();
    return Collections.singletonList(
      createExecutionStateFilteredFlowNodeAggregation(
        flowNodeExecutionState,
        terms(NESTED_EVENTS_AGGREGATION)
          .size(configurationService.getEsAggregationBucketLimit())
          .field(EVENTS + "." + ACTIVITY_ID)
          .subAggregation(distributedByPart.createAggregation(context))
      )
    );
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    getExecutionStateFilteredFlowNodesAggregation(response)
      .map(filteredFlowNodes -> (Terms) filteredFlowNodes.getAggregations().get(NESTED_EVENTS_AGGREGATION))
      .ifPresent(byFlowNodeIdAggregation -> {
        final Map<String, String> flowNodeNames = getFlowNodeNames(context.getReportData());
        final List<GroupByResult> groupedData = new ArrayList<>();
        for (Terms.Bucket flowNodeBucket : byFlowNodeIdAggregation.getBuckets()) {
          final String flowNodeKey = flowNodeBucket.getKeyAsString();
          if (flowNodeNames.containsKey(flowNodeKey)) {
            final List<DistributedByResult> singleResult =
              distributedByPart.retrieveResult(response, flowNodeBucket.getAggregations(), context);
            String label = flowNodeNames.get(flowNodeKey);
            groupedData.add(GroupByResult.createGroupByResult(flowNodeKey, label, singleResult));
            flowNodeNames.remove(flowNodeKey);
          }
        }

        // enrich data with flow nodes that haven't been executed, but should still show up in the result
        flowNodeNames.keySet().forEach(flowNodeKey -> {
          GroupByResult emptyResult = GroupByResult.createResultWithEmptyDistributedBy(flowNodeKey);
          emptyResult.setLabel(flowNodeNames.get(flowNodeKey));
          groupedData.add(emptyResult);
        });

        compositeCommandResult.setGroups(groupedData);
        compositeCommandResult.setIsComplete(byFlowNodeIdAggregation.getSumOfOtherDocCounts() == 0L);
      });
  }

  private Map<String, String> getFlowNodeNames(final ProcessReportDataDto reportData) {
    return definitionService
      .getLatestDefinition(
        DefinitionType.PROCESS,
        reportData.getDefinitionKey(),
        reportData.getDefinitionVersions(),
        reportData.getTenantIds()
      )
      .map(def -> ((ProcessDefinitionOptimizeDto) def).getFlowNodeNames())
      .orElse(Collections.emptyMap());
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new FlowNodesGroupByDto());
  }

}
