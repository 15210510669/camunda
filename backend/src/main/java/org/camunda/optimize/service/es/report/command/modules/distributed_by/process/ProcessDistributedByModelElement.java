/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

@RequiredArgsConstructor
public abstract class ProcessDistributedByModelElement extends ProcessDistributedByPart {

  private static final String MODEL_ELEMENT_ID_TERMS_AGGREGATION = "modelElement";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    final TermsAggregationBuilder modelElementTermsAggregation = AggregationBuilders
      .terms(MODEL_ELEMENT_ID_TERMS_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .order(BucketOrder.key(true))
      .field(getModelElementIdPath());
    viewPart.createAggregations(context).forEach(modelElementTermsAggregation::subAggregation);
    return Collections.singletonList(modelElementTermsAggregation);
  }

  @Override
  public List<DistributedByResult> retrieveResult(final SearchResponse response,
                                                  final Aggregations aggregations,
                                                  final ExecutionContext<ProcessReportDataDto> context) {
    final Terms byModelElementAggregation = aggregations.get(MODEL_ELEMENT_ID_TERMS_AGGREGATION);
    final Map<String, String> modelElementNames = getModelElementNames(context.getReportData());
    final List<DistributedByResult> distributedByModelElements = new ArrayList<>();
    for (Terms.Bucket modelElementBucket : byModelElementAggregation.getBuckets()) {
      final ViewResult viewResult = viewPart.retrieveResult(response, modelElementBucket.getAggregations(), context);
      final String modelElementKey = modelElementBucket.getKeyAsString();
      if (modelElementNames.containsKey(modelElementKey)) {
        String label = modelElementNames.get(modelElementKey);
        distributedByModelElements.add(createDistributedByResult(modelElementKey, label, viewResult));
        modelElementNames.remove(modelElementKey);
      }
    }
    addMissingDistributions(modelElementNames, distributedByModelElements, context);
    return distributedByModelElements;
  }

  private void addMissingDistributions(final Map<String, String> modelElementNames,
                                       final List<DistributedByResult> distributedByModelElements,
                                       final ExecutionContext<ProcessReportDataDto> context) {
    final Set<String> excludedFlowNodes = context.getReportData()
      .getFilter()
      .stream()
      .filter(filter -> filter instanceof ExecutedFlowNodeFilterDto
        && FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .map(ExecutedFlowNodeFilterDto.class::cast)
      .map(ExecutedFlowNodeFilterDto::getData)
      .filter(data -> NOT_IN.equals(data.getOperator()))
      .flatMap(data -> data.getValues().stream())
      .collect(toSet());

    // If view level executedFlowNodeFilter exist, we can filter the distributedBy buckets accordingly and only
    // enrich those which have not been excluded by the filter. Otherwise, enrich result with all missing modelElements.
    modelElementNames.keySet()
      .stream()
      .filter(key -> !excludedFlowNodes.contains(key))
      .forEach(key -> distributedByModelElements.add(
        DistributedByResult.createDistributedByResult(
          key, modelElementNames.get(key), getViewPart().createEmptyResult(context)
        )));
  }

  private Map<String, String> getModelElementNames(final ProcessReportDataDto reportData) {
    return reportData.getDefinitions().stream()
      .map(definitionDto -> definitionService.getDefinition(
        DefinitionType.PROCESS, definitionDto.getKey(), definitionDto.getVersions(), definitionDto.getTenantIds()
      ))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(this::extractModelElementNames)
      .map(Map::entrySet)
      .flatMap(Collection::stream)
      // can't use Collectors.toMap as value can be null, see https://bugs.openjdk.java.net/browse/JDK-8148463
      .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(getDistributedBy());
  }

  protected abstract String getModelElementIdPath();

  protected abstract Map<String, String> extractModelElementNames(DefinitionOptimizeResponseDto def);

  protected abstract ProcessDistributedByDto getDistributedBy();

}
