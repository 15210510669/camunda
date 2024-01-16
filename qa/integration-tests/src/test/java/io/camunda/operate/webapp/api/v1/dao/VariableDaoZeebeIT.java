/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class VariableDaoZeebeIT extends OperateZeebeAbstractIT {

  @Autowired
  private VariableDao dao;

  private Results<Variable> variableResults;
  private Variable variable;
  private Long key;
  private Long processInstanceKey;

  @Before
  public void setUp(){
    tester
        .deployProcess("manual-task.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed();
  }

  protected Long createVariablesAndGetProcessInstanceKey(String bpmnProcessId, Map<String,Object> variables) {
    try {
      final String payload = objectMapper.writeValueAsString(variables);
      tester
          .startProcessInstance(bpmnProcessId, payload)
          .waitUntil().processInstanceExists();
      for(String name: variables.keySet()){
        tester.variableExists(name);
      }
      return tester.getProcessInstanceKey();
    } catch (JsonProcessingException e) {
      fail("Could not build payload from map ", variables);
    }
    return null;
  }

  @Test
  public void shouldReturnVariables() throws Exception {
    given(() ->
        processInstanceKey = createVariablesAndGetProcessInstanceKey(
            "manual-task-process", Map.of("customerId", "23", "orderId", "5"))
    );
    when(() ->
        variableResults = dao.search(new Query<Variable>().setSort(Sort.listOf("name")))
    );
    then(() -> {
      assertThat(variableResults.getItems()).hasSize(2);
      assertThat(variableResults.getItems().get(0))
          .extracting(
              "processInstanceKey",
              "name",
              "value",
              "tenantId")
          .containsExactly(
              processInstanceKey,
              "customerId",
              "\"23\"",
              DEFAULT_TENANT_ID
          );
      assertThat(variableResults.getItems().get(1))
          .extracting(
              "processInstanceKey",
              "name",
              "value",
              "tenantId")
          .containsExactly(
              processInstanceKey,
              "orderId",
              "\"5\"",
              DEFAULT_TENANT_ID
          );
    });
  }

  @Test
  public void shouldReturnByKey() throws Exception {
    given(() -> {
      processInstanceKey = createVariablesAndGetProcessInstanceKey(
          "manual-task-process", Map.of("customerId", "23", "orderId", "5"));
      variableResults = dao.search(new Query<Variable>().setSort(Sort.listOf("name")));
      key = variableResults.getItems().get(0).getKey();
    });
    when(() ->
        variable = dao.byKey(key)
    );
    then(() -> {
      assertThat(variable.getValue()).isEqualTo("\"23\"");
      assertThat(variable.getKey()).isEqualTo(key);
    });
  }

  @Test
  public void shouldReturnVariablesWithEmptyFilter() throws Exception {
    given(() ->
        processInstanceKey = createVariablesAndGetProcessInstanceKey(
            "manual-task-process", Map.of("customerId", "23", "orderId", "5"))
    );
    when(() ->
        variableResults = dao.search(new Query<Variable>().setFilter(new Variable()).setSort(Sort.listOf("name")))
    );
    then(() -> {
      assertThat(variableResults.getItems()).hasSize(2);
      assertThat(variableResults.getItems().get(0))
          .extracting(
              "processInstanceKey",
              "name",
              "value",
              "tenantId")
          .containsExactly(
              processInstanceKey,
              "customerId",
              "\"23\"",
              DEFAULT_TENANT_ID
          );
      assertThat(variableResults.getItems().get(1))
          .extracting(
              "processInstanceKey",
              "name",
              "value",
              "tenantId")
          .containsExactly(
              processInstanceKey,
              "orderId",
              "\"5\"",
              DEFAULT_TENANT_ID
          );
    });
  }

  @Test
  public void shouldFilterVariables() throws Exception {
    given(() -> {
      createVariablesAndGetProcessInstanceKey(
          "manual-task-process", Map.of("customerId", "23","orderId","5"));
      processInstanceKey = createVariablesAndGetProcessInstanceKey(
          "process", Map.of("movie", "From dusk till dawn"));
    });
    when(() ->
        variableResults = dao.search(new Query<Variable>()
            .setFilter(
                new Variable().setName("movie")))
    );
    then(() -> {
      assertThat(variableResults.getItems()).hasSize(1);
      assertThat(variableResults.getItems().get(0))
          .extracting(
              "processInstanceKey",
              "name",
              "value")
          .containsExactly(
              processInstanceKey,
              "movie",
              "\"From dusk till dawn\""
          );
    });
  }

  @Test
  public void shouldSortVariables() throws Exception {
    given(() -> {
      createVariablesAndGetProcessInstanceKey(
          "manual-task-process", Map.of("number", 5));
      createVariablesAndGetProcessInstanceKey(
          "process", Map.of("ordered", true));
    });
    when(() ->
        variableResults = dao.search(new Query<Variable>()
            .setSort(Sort.listOf(
                Variable.NAME, Order.DESC)))
    );
    then(() -> {
      assertThat(variableResults.getItems()).hasSize(2);
      assertThat(variableResults.getItems().get(0).getValue()).isEqualTo("true");
      assertThat(variableResults.getItems().get(1).getValue()).isEqualTo("5");
    });
  }

  @Test
  public void shouldPageVariables() throws Exception {
    given(() -> {
      for (int i = 0; i < 7; i++) {
        createVariablesAndGetProcessInstanceKey(
            "manual-task-process", Map.of("counter", "Counted: " + i));
      }
    });
    when(() ->
        variableResults = dao.search(new Query<Variable>().setSize(5))
    );
    then(() -> {
      assertThat(variableResults.getTotal()).isEqualTo(7);
      assertThat(variableResults.getItems()).hasSize(5);
      Object[] searchAfter = variableResults.getSortValues();
      assertThat(String.valueOf(variableResults.getItems().get(4).getKey())).isEqualTo(String.valueOf(searchAfter[0]));

      Long firstKey = variableResults.getItems().get(0).getKey();
      Long secondKey = variableResults.getItems().get(1).getKey();

      Results<Variable> nextResults = dao.search(new Query<Variable>().setSearchAfter(new Object[]{firstKey}).setSize(2));
      assertThat(nextResults.getTotal()).isEqualTo(7);
      assertThat(nextResults.getItems()).hasSize(2);
      assertThat(nextResults.getItems().get(0).getKey()).isEqualTo(secondKey);
    });
  }

  protected void given(Runnable conditions) throws Exception {
    conditions.run();
  }

  protected void when(Runnable actions) throws Exception {
    actions.run();
  }

  protected void then(Runnable asserts) throws Exception {
    asserts.run();
  }

}

