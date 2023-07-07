/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.dashboard;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.CountProgressDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.UserTasksGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;

@Slf4j
@AllArgsConstructor
@Component
public class ManagementDashboardService {

  public static final String MANAGEMENT_DASHBOARD_LOCALIZATION_CODE = "dashboardName";
  public static final String PROCESS_INSTANCE_USAGE_REPORT_LOCALIZATION_CODE = "processInstanceUsage";
  public static final String INCIDENT_FREE_RATE_REPORT_LOCALIZATION_CODE = "incidentFreeRate";
  public static final String AUTOMATION_RATE_REPORT_LOCALIZATION_CODE = "automationRate";
  public static final String LONG_RUNNING_INSTANCES_REPORT_LOCALIZATION_CODE = "longRunningInstances";
  public static final String AUTOMATION_CANDIDATES_REPORT_LOCALIZATION_CODE = "automationCandidates";
  public static final String ACTIVE_BOTTLENECKS_REPORT_LOCALIZATION_CODE = "activeBottlenecks";
  public static final String MANAGEMENT_DASHBOARD_ID = "management-dashboard";

  private final DashboardWriter dashboardWriter;
  private final ReportWriter reportWriter;
  private final ConfigurationService configurationService;

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (configurationService.getCreateManagementEntitiesOnStartup()) {
      // First we delete all existing management entities
      log.info("Deleting Management entities");
      reportWriter.deleteAllManagementReports();
      dashboardWriter.deleteManagementDashboard();

      // Then recreate the management reports and dashboard
      log.info("Creating Management Reports and Management Dashboard");
      createManagementDashboardForReports(
        List.of(
          createProcessInstanceByStartMonthReport(new PositionDto(0, 0), new DimensionDto(3, 4)),
          createOverallIncidentFreeRateReport(new PositionDto(3, 0), new DimensionDto(3, 2)),
          createAutomationRateReport(new PositionDto(3, 2), new DimensionDto(3, 2)),
          createLongRunningInstancesReport(new PositionDto(6, 0), new DimensionDto(4, 4)),
          createAutomationCandidatesReport(new PositionDto(10, 0), new DimensionDto(4, 4)),
          createActiveBottlenecksReport(new PositionDto(14, 0), new DimensionDto(4, 4))
        )
      );
    }
  }

  private DashboardReportTileDto createProcessInstanceByStartMonthReport(final PositionDto positionDto,
                                                                         final DimensionDto dimensionDto) {
    final ProcessReportDataDto processInstanceGroupedByMonth = ProcessReportDataDto.builder()
      .definitions(Collections.emptyList())
      .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
      .groupBy(new StartDateGroupByDto(new DateGroupByValueDto(AggregateByDateUnit.MONTH)))
      .visualization(ProcessVisualization.BAR)
      .managementReport(true)
      .build();
    final String reportId = createReportAndGetId(processInstanceGroupedByMonth, PROCESS_INSTANCE_USAGE_REPORT_LOCALIZATION_CODE);
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto createOverallIncidentFreeRateReport(final PositionDto positionDto,
                                                                     final DimensionDto dimensionDto) {
    final SingleReportTargetValueDto targetConfig = new SingleReportTargetValueDto();
    targetConfig.setActive(true);
    final CountProgressDto countProgressConfig = new CountProgressDto();
    countProgressConfig.setTarget("99.5");
    targetConfig.setCountProgress(countProgressConfig);
    final ProcessReportDataDto overAllIncidentFreeRate = ProcessReportDataDto.builder()
      .definitions(Collections.emptyList())
      .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.PERCENTAGE))
      .groupBy(new NoneGroupByDto())
      .visualization(ProcessVisualization.NUMBER)
      .filter(ProcessFilterBuilder.filter().noIncidents().add().buildList())
      .configuration(SingleReportConfigurationDto.builder().targetValue(targetConfig).build())
      .managementReport(true)
      .build();
    final String reportId = createReportAndGetId(overAllIncidentFreeRate, INCIDENT_FREE_RATE_REPORT_LOCALIZATION_CODE);
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto createAutomationRateReport(final PositionDto positionDto, final DimensionDto dimensionDto) {
    final SingleReportTargetValueDto targetConfig = new SingleReportTargetValueDto();
    targetConfig.setActive(true);
    final CountProgressDto countProgressConfig = new CountProgressDto();
    countProgressConfig.setTarget("70");
    targetConfig.setCountProgress(countProgressConfig);
    final ProcessReportDataDto automationRate = ProcessReportDataDto.builder()
      .definitions(Collections.emptyList())
      .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.PERCENTAGE))
      .groupBy(new NoneGroupByDto())
      .visualization(ProcessVisualization.NUMBER)
      .filter(ProcessFilterBuilder.filter()
                .duration()
                .operator(LESS_THAN)
                .unit(DurationUnit.HOURS)
                .value(1L)
                .add()
                .buildList())
      .configuration(SingleReportConfigurationDto.builder().targetValue(targetConfig).build())
      .managementReport(true)
      .build();
    final String reportId = createReportAndGetId(automationRate, AUTOMATION_RATE_REPORT_LOCALIZATION_CODE);
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto createLongRunningInstancesReport(final PositionDto positionDto,
                                                                  final DimensionDto dimensionDto) {
    final ProcessReportDataDto longRunningInstances = ProcessReportDataDto.builder()
      .definitions(Collections.emptyList())
      .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, List.of(ViewProperty.FREQUENCY, ViewProperty.DURATION)))
      .groupBy(new NoneGroupByDto())
      .distributedBy(new ProcessDistributedByDto())
      .visualization(ProcessVisualization.PIE)
      .filter(ProcessFilterBuilder.filter()
                .duration()
                .operator(GREATER_THAN)
                .unit(DurationUnit.DAYS)
                .value(7L)
                .add()
                .runningInstancesOnly()
                .add()
                .buildList())
      .managementReport(true)
      .build();
    final String reportId = createReportAndGetId(longRunningInstances, LONG_RUNNING_INSTANCES_REPORT_LOCALIZATION_CODE);
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto createAutomationCandidatesReport(final PositionDto positionDto,
                                                                  final DimensionDto dimensionDto) {
    final ProcessReportDataDto automationCandidates = ProcessReportDataDto.builder()
      .definitions(Collections.emptyList())
      .view(new ProcessViewDto(ProcessViewEntity.USER_TASK, List.of(ViewProperty.FREQUENCY, ViewProperty.DURATION)))
      .groupBy(new UserTasksGroupByDto())
      .visualization(ProcessVisualization.PIE)
      .managementReport(true)
      .build();
    final String reportId = createReportAndGetId(automationCandidates, AUTOMATION_CANDIDATES_REPORT_LOCALIZATION_CODE);
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto createActiveBottlenecksReport(final PositionDto positionDto, final DimensionDto dimensionDto) {
    final ProcessReportDataDto activeBottlenecks = ProcessReportDataDto.builder()
      .definitions(Collections.emptyList())
      .view(new ProcessViewDto(ProcessViewEntity.FLOW_NODE, List.of(ViewProperty.FREQUENCY, ViewProperty.DURATION)))
      .groupBy(new FlowNodesGroupByDto())
      .visualization(ProcessVisualization.PIE)
      .managementReport(true)
      .build();
    final String reportId = createReportAndGetId(activeBottlenecks, ACTIVE_BOTTLENECKS_REPORT_LOCALIZATION_CODE);
    return buildDashboardReportTileDto(positionDto, dimensionDto, reportId);
  }

  private DashboardReportTileDto buildDashboardReportTileDto(final PositionDto positionDto, final DimensionDto dimensionDto,
                                                             final String reportId) {
    return DashboardReportTileDto.builder()
      .id(reportId)
      .type(DashboardTileType.OPTIMIZE_REPORT)
      .position(positionDto)
      .dimensions(dimensionDto)
      .build();
  }

  private void createManagementDashboardForReports(final List<DashboardReportTileDto> reportsForDashboard) {
    final DashboardDefinitionRestDto dashboardDefinition = new DashboardDefinitionRestDto();
    dashboardDefinition.setId(MANAGEMENT_DASHBOARD_ID);
    dashboardDefinition.setName(MANAGEMENT_DASHBOARD_LOCALIZATION_CODE);
    dashboardDefinition.setTiles(reportsForDashboard);

    DashboardInstanceStartDateFilterDto filterDto = new DashboardInstanceStartDateFilterDto();
    RollingDateFilterDataDto rollingFilter = new RollingDateFilterDataDto(new RollingDateFilterStartDto(12L, DateUnit.MONTHS));
    filterDto.setData(new DashboardDateFilterDataDto(rollingFilter));

    dashboardDefinition.setAvailableFilters(List.of(filterDto));
    dashboardDefinition.setManagementDashboard(true);
    dashboardWriter.saveDashboard(dashboardDefinition);
  }

  private String createReportAndGetId(final ProcessReportDataDto processReportDataDto, final String localisationCodeForName) {
    return reportWriter.createNewSingleProcessReport(
      null, processReportDataDto, localisationCodeForName, null, null
    ).getId();
  }

}
