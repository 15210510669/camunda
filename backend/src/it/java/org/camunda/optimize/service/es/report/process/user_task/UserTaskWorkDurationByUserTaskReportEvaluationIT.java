package org.camunda.optimize.service.es.report.process.user_task;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.sql.SQLException;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskWorkDurationMapGroupByUserTaskReport;

@RunWith(Parameterized.class)
public class UserTaskWorkDurationByUserTaskReportEvaluationIT
  extends AbstractUserTaskDurationByUserTaskReportEvaluationIT {

  public UserTaskWorkDurationByUserTaskReportEvaluationIT(final ProcessViewOperation viewOperation) {
    super(viewOperation);
  }

  @Override
  protected ProcessViewProperty getViewProperty() {
    return ProcessViewProperty.WORK_DURATION;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        changeUserOperationClaimTimestamp(processInstanceDto, setDuration, historicUserTaskInstanceDto);
      });
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final long duration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(historicUserTaskInstanceDto -> {
        changeUserOperationClaimTimestamp(processInstanceDto, duration, historicUserTaskInstanceDto);
      });
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createUserTaskWorkDurationMapGroupByUserTaskReport(processDefinitionKey, version, viewOperation);
  }

  private void changeUserOperationClaimTimestamp(final ProcessInstanceEngineDto processInstanceDto,
                                                 final long millis,
                                                 final HistoricUserTaskInstanceDto historicUserTaskInstanceDto) {
    try {
      engineDatabaseRule.changeUserTaskClaimOperationTimestamp(
        processInstanceDto.getId(),
        historicUserTaskInstanceDto.getId(),
        historicUserTaskInstanceDto.getEndTime().minus(millis, ChronoUnit.MILLIS)
      );
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }


}
