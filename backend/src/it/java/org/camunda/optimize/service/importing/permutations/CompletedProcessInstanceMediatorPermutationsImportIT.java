/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.permutations;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.CompletedProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.CompletedUserTaskEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.IdentityLinkLogEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.VariableUpdateEngineImportMediator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.COMPLETED_STATE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class CompletedProcessInstanceMediatorPermutationsImportIT extends AbstractImportMediatorPermutationsIT {

  @BeforeAll
  public static void given() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartUserTaskProcess();
    engineIntegrationExtension.claimAllRunningUserTasks(processInstanceDto.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(processInstanceDto.getId(), CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
  }

  @ParameterizedTest(name = "Completed Process Instances are fully imported with mediator order {0}")
  @MethodSource("completedActivityRelatedMediators")
  public void completedInstanceIsFullyImportedCamundaEventImportEnabled(
    final List<Class<? extends EngineImportMediator>> mediatorOrder) {
    // when
    performOrderedImport(mediatorOrder);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .hasSize(1)
      .singleElement()
      .satisfies(persistedProcessInstanceDto -> {
        // general instance sanity check
        assertThat(persistedProcessInstanceDto.getStartDate()).isNotNull();
        assertThat(persistedProcessInstanceDto.getEndDate()).isNotNull();
        assertThat(persistedProcessInstanceDto.getState()).isEqualTo(COMPLETED_STATE);
        assertThat(persistedProcessInstanceDto.getVariables())
          .hasSize(2)
          .allSatisfy(variable -> {
            assertThat(variable.getName()).isNotNull();
            assertThat(variable.getValue()).isNotNull();
            assertThat(variable.getType()).isNotNull();
            assertThat(variable.getVersion()).isEqualTo(1L);
          });
        assertThat(persistedProcessInstanceDto.getEvents())
          .hasSize(3)
          .allSatisfy(activity -> {
            assertThat(activity.getStartDate()).isNotNull();
            assertThat(activity.getEndDate()).isNotNull();
            assertThat(activity.getDurationInMs()).isGreaterThanOrEqualTo(0L);
          });
        assertThat(persistedProcessInstanceDto.getUserTasks())
          .hasSize(1)
          .singleElement()
          .satisfies(userTask -> {
            assertThat(userTask.getStartDate()).isNotNull();
            assertThat(userTask.getEndDate()).isNotNull();
            assertThat(userTask.getAssignee()).isEqualTo(DEFAULT_USERNAME);
            assertThat(userTask.getCandidateGroups()).containsOnly(CANDIDATE_GROUP);
            assertThat(userTask.getIdleDurationInMs()).isGreaterThan(0L);
            assertThat(userTask.getWorkDurationInMs()).isGreaterThan(0L);
            assertThat(userTask.getAssigneeOperations()).hasSize(1);
          });
      });

    final List<CamundaActivityEventDto> allStoredCamundaActivityEventsForDefinition =
      elasticSearchIntegrationTestExtension.getAllStoredCamundaActivityEventsForDefinition(TEST_PROCESS);
    // start event, end event, user task start/end, process instance start/end
    assertThat(allStoredCamundaActivityEventsForDefinition).hasSize(6);
  }

  private static Stream<List<Class<? extends EngineImportMediator>>> completedActivityRelatedMediators() {
    return getMediatorPermutationsStream(ImmutableList.of(
      CompletedActivityInstanceEngineImportMediator.class,
      CompletedUserTaskEngineImportMediator.class,
      CompletedProcessInstanceEngineImportMediator.class,
      IdentityLinkLogEngineImportMediator.class,
      VariableUpdateEngineImportMediator.class
    ));
  }

}
