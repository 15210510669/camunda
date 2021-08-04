/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import org.camunda.optimize.service.es.filter.FilterContext;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.common.TriFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.service.es.report.command.util.DurationScriptUtil.getDurationFilterScript;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_MI_BODY;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelElementFilterQueryUtil {

  private static final Set<Class<? extends ProcessFilterDto<?>>> FLOW_NODE_VIEW_LEVEL_FILTERS = Set.of(
    RunningFlowNodesOnlyFilterDto.class,
    CompletedFlowNodesOnlyFilterDto.class,
    CompletedOrCanceledFlowNodesOnlyFilterDto.class,
    CanceledFlowNodesOnlyFilterDto.class,
    CandidateGroupFilterDto.class,
    AssigneeFilterDto.class,
    FlowNodeDurationFilterDto.class,
    ExecutedFlowNodeFilterDto.class,
    FlowNodeStartDateFilterDto.class,
    FlowNodeEndDateFilterDto.class
  );

  private static final Map<Class<? extends ProcessFilterDto<?>>, Function<BoolQueryBuilder, QueryBuilder>>
    FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES =
    Map.of(
      RunningFlowNodesOnlyFilterDto.class,
      ModelElementFilterQueryUtil::createRunningFlowNodesOnlyFilterQuery,
      CompletedFlowNodesOnlyFilterDto.class,
      ModelElementFilterQueryUtil::createCompletedFlowNodesOnlyFilterQuery,
      CompletedOrCanceledFlowNodesOnlyFilterDto.class,
      ModelElementFilterQueryUtil::createCompletedOrCanceledFlowNodesOnlyFilterQuery,
      CanceledFlowNodesOnlyFilterDto.class,
      ModelElementFilterQueryUtil::createCanceledFlowNodesOnlyFilterQuery
    );

  private static final Map<Class<? extends ProcessFilterDto<?>>,
    TriFunction<DateFilterDataDto<?>, ZoneId, BoolQueryBuilder, QueryBuilder>>
    FLOW_NODE_DATE_VIEW_FILTER_INSTANCE_QUERIES =
    Map.of(
      FlowNodeStartDateFilterDto.class,
      ModelElementFilterQueryUtil::createFlowNodeStartDateFilterQuery,
      FlowNodeEndDateFilterDto.class,
      ModelElementFilterQueryUtil::createFlowNodeEndDateFilterQuery
    );

  public static Optional<NestedQueryBuilder> addInstanceFilterForRelevantViewLevelFilters(final List<ProcessFilterDto<?>> filters,
                                                                                          final FilterContext filterContext) {
    final List<ProcessFilterDto<?>> viewLevelFiltersForInstanceMatch = filters.stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filter -> FLOW_NODE_VIEW_LEVEL_FILTERS.contains(filter.getClass()))
      .collect(toList());
    if (!viewLevelFiltersForInstanceMatch.isEmpty()) {
      BoolQueryBuilder viewFilterInstanceQuery = createFlowNodeTypeFilterQuery(filterContext.isUserTaskReport());
      viewLevelFiltersForInstanceMatch
        .forEach(filter -> {
          if (filter instanceof FlowNodeDurationFilterDto) {
            final FlowNodeDurationFiltersDataDto filterData = (FlowNodeDurationFiltersDataDto) filter.getData();
            createFlowNodeDurationFilterQuery(filterData, viewFilterInstanceQuery);
          } else if (filter instanceof CandidateGroupFilterDto) {
            final IdentityLinkFilterDataDto filterData = (IdentityLinkFilterDataDto) filter.getData();
            createCandidateGroupFilterQuery(filterData, viewFilterInstanceQuery);
          } else if (filter instanceof AssigneeFilterDto) {
            final IdentityLinkFilterDataDto filterData = (IdentityLinkFilterDataDto) filter.getData();
            createAssigneeFilterQuery(filterData, viewFilterInstanceQuery);
          } else if (filter instanceof ExecutedFlowNodeFilterDto) {
            final ExecutedFlowNodeFilterDataDto filterData = (ExecutedFlowNodeFilterDataDto) filter.getData();
            createExecutedFlowNodeFilterQuery(filterData, viewFilterInstanceQuery);
          } else if (FLOW_NODE_DATE_VIEW_FILTER_INSTANCE_QUERIES.containsKey(filter.getClass())) {
            FLOW_NODE_DATE_VIEW_FILTER_INSTANCE_QUERIES.get(filter.getClass())
              .apply((DateFilterDataDto<?>) filter.getData(), filterContext.getTimezone(), viewFilterInstanceQuery);
          } else {
            FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES.get(filter.getClass()).apply(viewFilterInstanceQuery);
          }
        });
      return Optional.of(nestedQuery(FLOW_NODE_INSTANCES, viewFilterInstanceQuery, ScoreMode.None));
    }
    return Optional.empty();
  }

  public static BoolQueryBuilder createModelElementAggregationFilter(final ProcessReportDataDto reportDataDto,
                                                                     final FilterContext filterContext) {
    final BoolQueryBuilder filterBoolQuery = createFlowNodeTypeFilterQuery(reportDataDto);
    addFlowNodeStatusFilter(filterBoolQuery, reportDataDto);
    addAssigneeFilter(filterBoolQuery, reportDataDto);
    addCandidateGroupFilter(filterBoolQuery, reportDataDto);
    addFlowNodeDurationFilter(filterBoolQuery, reportDataDto);
    addFlowNodeIdFilter(filterBoolQuery, reportDataDto);
    addFlowNodeStartDateFilter(filterBoolQuery, reportDataDto, filterContext.getTimezone());
    addFlowNodeEndDateFilter(filterBoolQuery, reportDataDto, filterContext.getTimezone());
    return filterBoolQuery;
  }

  public static BoolQueryBuilder createUserTaskFlowNodeTypeFilter() {
    return boolQuery().must(
      termQuery(
        nestedFieldReference(FLOW_NODE_TYPE),
        FLOW_NODE_TYPE_USER_TASK
      ));
  }

  public static BoolQueryBuilder createInclusiveFlowNodeIdFilterQuery(final ProcessReportDataDto reportDataDto,
                                                                      final Set<String> flowNodeIds,
                                                                      final FilterContext filterContext) {
    return createExecutedFlowNodeFilterQuery(
      createModelElementAggregationFilter(reportDataDto, filterContext),
      nestedFieldReference(FLOW_NODE_ID),
      new ArrayList<>(flowNodeIds),
      IN
    );
  }

  public static BoolQueryBuilder createExecutedFlowNodeFilterQuery(final ExecutedFlowNodeFilterDataDto executedFlowNodeFilterData,
                                                                   final BoolQueryBuilder boolQuery) {
    return createExecutedFlowNodeFilterQuery(
      boolQuery,
      nestedFieldReference(FLOW_NODE_ID),
      executedFlowNodeFilterData.getValues(),
      executedFlowNodeFilterData.getOperator()
    );
  }

  public static BoolQueryBuilder createExecutedFlowNodeFilterQuery(final ExecutedFlowNodeFilterDataDto executedFlowNodeFilterData,
                                                                   final String nestedFieldReference,
                                                                   final BoolQueryBuilder boolQuery) {
    return createExecutedFlowNodeFilterQuery(
      boolQuery,
      nestedFieldReference,
      executedFlowNodeFilterData.getValues(),
      executedFlowNodeFilterData.getOperator()
    );
  }

  public static BoolQueryBuilder createExecutedFlowNodeFilterQuery(final BoolQueryBuilder boolQuery,
                                                                   final String nestedFieldReference,
                                                                   final List<String> flowNodeIds,
                                                                   final MembershipFilterOperator operator) {
    final TermsQueryBuilder termsQuery = termsQuery(nestedFieldReference, flowNodeIds);
    if (IN.equals(operator)) {
      boolQuery.filter(termsQuery);
    } else {
      boolQuery.filter(boolQuery().mustNot(termsQuery));
    }
    return boolQuery;
  }

  public static QueryBuilder createFlowNodeDurationFilterQuery(final FlowNodeDurationFiltersDataDto durationFilterData) {
    return createFlowNodeDurationFilterQuery(durationFilterData, boolQuery());
  }

  public static QueryBuilder createRunningFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery
      .mustNot(termQuery(nestedFieldReference(FLOW_NODE_TYPE), FLOW_NODE_TYPE_MI_BODY))
      .mustNot(existsQuery(nestedFieldReference(FLOW_NODE_END_DATE)));
  }

  public static QueryBuilder createCompletedFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery
      .mustNot(termQuery(nestedFieldReference(FLOW_NODE_TYPE), FLOW_NODE_TYPE_MI_BODY))
      .must(termQuery(nestedFieldReference(FLOW_NODE_CANCELED), false))
      .must(existsQuery(nestedFieldReference(FLOW_NODE_END_DATE)));
  }

  public static QueryBuilder createCanceledFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery
      .mustNot(termQuery(nestedFieldReference(FLOW_NODE_TYPE), FLOW_NODE_TYPE_MI_BODY))
      .must(termQuery(nestedFieldReference(FLOW_NODE_CANCELED), true));
  }

  public static QueryBuilder createCompletedOrCanceledFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery
      .mustNot(termQuery(nestedFieldReference(FLOW_NODE_TYPE), FLOW_NODE_TYPE_MI_BODY))
      .must(existsQuery(nestedFieldReference(FLOW_NODE_END_DATE)));
  }

  public static QueryBuilder createFlowNodeStartDateFilterQuery(final DateFilterDataDto<?> filterData,
                                                                final ZoneId timezone,
                                                                final BoolQueryBuilder queryBuilder) {
    DateFilterQueryUtil.addFilters(
      queryBuilder,
      List.of(filterData),
      nestedFieldReference(FLOW_NODE_START_DATE),
      timezone
    );
    return queryBuilder;
  }

  public static QueryBuilder createFlowNodeEndDateFilterQuery(final DateFilterDataDto<?> filterData,
                                                              final ZoneId timezone,
                                                              final BoolQueryBuilder queryBuilder) {
    DateFilterQueryUtil.addFilters(
      queryBuilder,
      List.of(filterData),
      nestedFieldReference(FLOW_NODE_END_DATE),
      timezone
    );
    return queryBuilder;
  }

  public static QueryBuilder createAssigneeFilterQuery(final IdentityLinkFilterDataDto assigneeFilter) {
    return createAssigneeFilterQuery(assigneeFilter, boolQuery());
  }

  private static QueryBuilder createAssigneeFilterQuery(final IdentityLinkFilterDataDto assigneeFilter,
                                                        final BoolQueryBuilder queryBuilder) {
    return createIdentityLinkFilterQuery(assigneeFilter, USER_TASK_ASSIGNEE, queryBuilder);
  }

  public static QueryBuilder createCandidateGroupFilterQuery(final IdentityLinkFilterDataDto candidateGroupFilter) {
    return createCandidateGroupFilterQuery(candidateGroupFilter, boolQuery());
  }

  private static QueryBuilder createCandidateGroupFilterQuery(final IdentityLinkFilterDataDto candidateGroupFilter,
                                                              final BoolQueryBuilder queryBuilder) {
    return createIdentityLinkFilterQuery(
      candidateGroupFilter,
      USER_TASK_CANDIDATE_GROUPS,
      queryBuilder
    );
  }

  private static QueryBuilder createFlowNodeDurationFilterQuery(
    final FlowNodeDurationFiltersDataDto durationFilterData,
    final BoolQueryBuilder queryBuilder) {
    queryBuilder.minimumShouldMatch(1);
    durationFilterData.forEach((flowNodeId, durationFilter) -> {
      final BoolQueryBuilder particularFlowNodeQuery = boolQuery()
        .must(termQuery(nestedFieldReference(FLOW_NODE_ID), flowNodeId))
        .must(QueryBuilders.scriptQuery(getDurationFilterScript(
          LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
          nestedFieldReference(FLOW_NODE_TOTAL_DURATION),
          nestedFieldReference(FLOW_NODE_START_DATE),
          durationFilter
        )));
      queryBuilder.should(particularFlowNodeQuery);
    });
    return queryBuilder;
  }

  private static QueryBuilder createIdentityLinkFilterQuery(final IdentityLinkFilterDataDto identityFilter,
                                                            final String valueField,
                                                            final BoolQueryBuilder queryBuilder) {
    if (CollectionUtils.isEmpty(identityFilter.getValues())) {
      throw new OptimizeValidationException("Filter values are not allowed to be empty.");
    }

    final AtomicBoolean includeNull = new AtomicBoolean(false);
    final Set<String> nonNullValues = identityFilter.getValues().stream()
      .peek(value -> {
        if (value == null) {
          includeNull.set(true);
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    // identity filters should always only return flowNodes of type userTask
    queryBuilder.must(createUserTaskFlowNodeTypeFilter());

    final BoolQueryBuilder identityQuery = boolQuery().minimumShouldMatch(1);
    if (!nonNullValues.isEmpty()) {
      identityQuery.should(termsQuery(nestedFieldReference(valueField), nonNullValues));
    }
    if (includeNull.get()) {
      identityQuery.should(boolQuery().mustNot(existsQuery(nestedFieldReference(valueField))));
    }

    if (MembershipFilterOperator.NOT_IN.equals(identityFilter.getOperator())) {
      return queryBuilder.mustNot(identityQuery);
    } else {
      return queryBuilder.must(identityQuery);
    }
  }

  private static void addFlowNodeStatusFilter(final BoolQueryBuilder boolQuery,
                                              final ProcessReportDataDto reportDataDto) {
    reportDataDto.getFilter().stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filter -> FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES.containsKey(filter.getClass()))
      .forEach(filter -> boolQuery.filter(
        FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES.get(filter.getClass()).apply(boolQuery())));
  }

  private static void addFlowNodeDurationFilter(final BoolQueryBuilder boolQuery,
                                                final ProcessReportDataDto reportDataDto) {
    findAllViewLevelFiltersOfType(
      reportDataDto.getFilter(),
      FlowNodeDurationFilterDto.class
    ).map(ProcessFilterDto::getData)
      .forEach(durationFilterData -> boolQuery.filter(createFlowNodeDurationFilterQuery(durationFilterData)));
  }

  private static void addAssigneeFilter(final BoolQueryBuilder userTaskFilterBoolQuery,
                                        final ProcessReportDataDto reportDataDto) {
    findAllViewLevelFiltersOfType(reportDataDto.getFilter(), AssigneeFilterDto.class)
      .map(ProcessFilterDto::getData)
      .forEach(assigneeFilterData -> userTaskFilterBoolQuery.filter(createAssigneeFilterQuery(
        assigneeFilterData,
        boolQuery()
      )));
  }

  private static void addCandidateGroupFilter(final BoolQueryBuilder userTaskFilterBoolQuery,
                                              final ProcessReportDataDto reportDataDto) {
    findAllViewLevelFiltersOfType(reportDataDto.getFilter(), CandidateGroupFilterDto.class)
      .map(ProcessFilterDto::getData)
      .forEach(candidateFilterData -> userTaskFilterBoolQuery.filter(createCandidateGroupFilterQuery(
        candidateFilterData,
        boolQuery()
      )));
  }

  public static void addFlowNodeIdFilter(final BoolQueryBuilder boolQuery,
                                         final ProcessReportDataDto reportDataDto) {
    findAllViewLevelFiltersOfType(
      reportDataDto.getFilter(),
      ExecutedFlowNodeFilterDto.class
    ).map(ProcessFilterDto::getData)
      .forEach(executedFlowNodeFilterData -> boolQuery.filter(createExecutedFlowNodeFilterQuery(
        executedFlowNodeFilterData,
        boolQuery()
      )));
  }

  public static void addFlowNodeStartDateFilter(final BoolQueryBuilder boolQuery,
                                                final ProcessReportDataDto reportDataDto,
                                                final ZoneId timezone) {
    findAllViewLevelFiltersOfType(
      reportDataDto.getFilter(),
      FlowNodeStartDateFilterDto.class
    ).map(ProcessFilterDto::getData)
      .forEach(flowNodeStartDateFilterData -> createFlowNodeStartDateFilterQuery(
        flowNodeStartDateFilterData,
        timezone,
        boolQuery
      ));
  }

  public static void addFlowNodeEndDateFilter(final BoolQueryBuilder boolQuery,
                                              final ProcessReportDataDto reportDataDto,
                                              final ZoneId timezone) {
    findAllViewLevelFiltersOfType(
      reportDataDto.getFilter(),
      FlowNodeEndDateFilterDto.class
    ).map(ProcessFilterDto::getData)
      .forEach(flowNodeEndDateFilterData -> createFlowNodeEndDateFilterQuery(
        flowNodeEndDateFilterData,
        timezone,
        boolQuery
      ));
  }

  private static <T extends ProcessFilterDto<?>> Stream<T> findAllViewLevelFiltersOfType(
    final List<ProcessFilterDto<?>> filters,
    final Class<T> filterClass) {
    return filters.stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filterClass::isInstance)
      .map(filterClass::cast);
  }

  private static String nestedFieldReference(final String fieldName) {
    return FLOW_NODE_INSTANCES + "." + fieldName;
  }

  private static BoolQueryBuilder createFlowNodeTypeFilterQuery(final ProcessReportDataDto reportDataDto) {
    return createFlowNodeTypeFilterQuery(reportDataDto.isUserTaskReport());
  }

  private static BoolQueryBuilder createFlowNodeTypeFilterQuery(final boolean isUserTaskReport) {
    return isUserTaskReport
      ? createUserTaskFlowNodeTypeFilter()
      : boolQuery();
  }

}
