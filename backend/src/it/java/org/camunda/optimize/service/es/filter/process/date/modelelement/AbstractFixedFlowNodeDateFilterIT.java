/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date.modelelement;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public abstract class AbstractFixedFlowNodeDateFilterIT extends AbstractFlowNodeDateFilterIT {

  protected abstract List<ProcessFilterDto<?>> createFixedDateViewFilter(final OffsetDateTime startDate,
                                                                         final OffsetDateTime endDate);

  protected abstract List<ProcessFilterDto<?>> createFixedDateInstanceFilter(final List<String> flowNodeIds,
                                                                             final OffsetDateTime startDate,
                                                                             final OffsetDateTime endDate);

  @Override
  protected List<ProcessFilterDto<?>> createViewLevelDateFilterForDate1() {
    return createFixedDateViewFilter(DATE_1);
  }

  @Override
  protected List<ProcessFilterDto<?>> createInstanceLevelDateFilterForDate1(final List<String> flowNodeIds) {
    return createFixedDateInstanceFilter(flowNodeIds, DATE_1);
  }

  @Override
  protected List<ProcessFilterDto<?>> createViewLevelDateFilterForDate2() {
    return createFixedDateViewFilter(DATE_2);
  }

  @Override
  protected List<ProcessFilterDto<?>> createInvalidFilter() {
    return createFixedDateViewFilter(null);
  }

  // Additional tests for the broader range logic as the tests in the abstract class all filter for the specific
  // dates of the flowNodes
  @Test
  public void viewLevel_broaderFilterRangeWorks() {
    // given
    final ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(instance1.getDefinitionId());

    updateFlowNodeDate(instance1.getId(), START_EVENT, DATE_2);
    updateFlowNodeDate(instance1.getId(), END_EVENT, DATE_2);
    updateFlowNodeDate(instance2.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance2.getId(), END_EVENT, DATE_1);
    importAllEngineEntitiesFromScratch();

    final List<ProcessFilterDto<?>> rangeFilter = createFixedDateViewFilter(DATE_1.minusDays(1), DATE_1.plusDays(1));

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      evaluateReportWithFlowNodeDateFilter(
        ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE,
        rangeFilter
      );

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(new Tuple(START_EVENT, 1.), new Tuple(END_EVENT, 1.));
  }

  @Test
  public void instanceLevel_broaderFilterRangeWorks() {
    // given
    final ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(instance1.getDefinitionId());

    updateFlowNodeDate(instance1.getId(), START_EVENT, DATE_2);
    updateFlowNodeDate(instance1.getId(), END_EVENT, DATE_2);
    updateFlowNodeDate(instance2.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance2.getId(), END_EVENT, DATE_1);
    importAllEngineEntitiesFromScratch();

    final List<ProcessFilterDto<?>> rangeFilter = createFixedDateInstanceFilter(
      singletonList(START_EVENT),
      DATE_1.minusDays(1),
      DATE_1.plusDays(1)
    );

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      evaluateReportWithFlowNodeDateFilter(
        ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE,
        rangeFilter
      );

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(new Tuple(START_EVENT, 1.), new Tuple(END_EVENT, 1.));
  }

  private List<ProcessFilterDto<?>> createFixedDateViewFilter(final OffsetDateTime date) {
    return createFixedDateViewFilter(date, date);
  }

  private List<ProcessFilterDto<?>> createFixedDateInstanceFilter(final List<String> flowNodeIds,
                                                                  final OffsetDateTime date) {
    return createFixedDateInstanceFilter(flowNodeIds, date, date);

  }
}
