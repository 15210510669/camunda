/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static io.camunda.operate.schema.indices.DecisionIndex.DECISION_ID;
import static io.camunda.operate.schema.indices.DecisionIndex.NAME;
import static io.camunda.operate.schema.indices.DecisionIndex.VERSION;
import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static io.camunda.operate.webapp.api.v1.entities.DecisionDefinition.DECISION_REQUIREMENTS_NAME;
import static io.camunda.operate.webapp.api.v1.entities.DecisionDefinition.DECISION_REQUIREMENTS_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

public class DecisionDefinitionDaoZeebeIT extends OperateZeebeAbstractIT {

  @Autowired
  DecisionDefinitionDao dao;

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private TestSearchRepository searchRepository;

  private DecisionDefinition decisionDefinition;
  private Long key;
  private Results<DecisionDefinition> decisionDefinitionResults, decisionDefinitionResultsPage1, decisionDefinitionResultsPage2;

  @Test
  public void shouldReturnWhenByKey() throws Exception {
    given(() -> {
      tester.deployDecision("invoiceBusinessDecisions_v_1.dmn").waitUntil().decisionsAreDeployed(2);

      List<DecisionDefinition> hits;
      try {
        hits = searchRepository.searchTerm(decisionIndex.getAlias(), "decisionId", "invoiceClassification", DecisionDefinition.class, 1);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      key = hits.get(0).getKey();

    });
    when(() -> decisionDefinition = dao.byKey(key));
    then(() -> {
      assertThat(decisionDefinition.getKey()).isEqualTo(key);
      assertThat(decisionDefinition.getName()).isEqualTo("Invoice Classification");
      assertThat(decisionDefinition.getDecisionId()).isEqualTo("invoiceClassification");
      assertThat(decisionDefinition.getDecisionRequirementsId()).isEqualTo("invoiceBusinessDecisions");
      assertThat(decisionDefinition.getDecisionRequirementsName()).isEqualTo("Invoice Business Decisions");
      assertThat(decisionDefinition.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
    });
  }

  @Test
  public void shouldReturnDecisionDefinitionsOnSearch() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> decisionDefinitionResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);
      assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_ID)
          .containsExactly("invoiceAssignApprover", "invoiceClassification", "invoiceAssignApprover", "invoiceClassification");
      assertThat(decisionDefinitionResults.getItems()).extracting(VERSION).containsExactly(1, 1, 2, 2);
      assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_REQUIREMENTS_NAME)
          .containsExactly("Invoice Business Decisions", "Invoice Business Decisions", "Invoice Business Decisions", "Invoice Business Decisions");
      assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_REQUIREMENTS_VERSION).containsExactly(1, 1, 2, 2);
    });
  }

  @Test
  public void shouldReturnDecisionDefinitionsOnSearchWithEmptyFilter() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> decisionDefinitionResults = dao.search(new Query<DecisionDefinition>().setFilter(new DecisionDefinition())));
    then(() -> {
      assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);
      assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_ID)
          .containsExactly("invoiceAssignApprover", "invoiceClassification", "invoiceAssignApprover", "invoiceClassification");
      assertThat(decisionDefinitionResults.getItems()).extracting(VERSION).containsExactly(1, 1, 2, 2);
      assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_REQUIREMENTS_NAME)
          .containsExactly("Invoice Business Decisions", "Invoice Business Decisions", "Invoice Business Decisions", "Invoice Business Decisions");
      assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_REQUIREMENTS_VERSION).containsExactly(1, 1, 2, 2);
    });
  }

  @Test
  public void shouldPageWithSearchAfterSizeAndSortedAsc() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      decisionDefinitionResultsPage1 = dao.search(new Query<DecisionDefinition>().setSize(3)
          .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.ASC)));
      decisionDefinitionResultsPage2 = dao.search(new Query<DecisionDefinition>().setSize(3)
          .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.ASC))
          .setSearchAfter(new Object[] { "invoiceClassification", decisionDefinitionResultsPage1.getItems().get(2).getKey() }));
    });
    then(() -> {
      assertThat(decisionDefinitionResultsPage1.getTotal()).isEqualTo(4);
      assertThat(decisionDefinitionResultsPage1.getItems()).hasSize(3);
      assertThat(decisionDefinitionResultsPage1.getItems()).extracting(DECISION_ID)
          .containsExactly("invoiceAssignApprover", "invoiceAssignApprover", "invoiceClassification");
      assertThat(decisionDefinitionResultsPage1.getItems()).extracting(VERSION).containsExactly(1, 2, 1);
      assertThat(decisionDefinitionResultsPage2.getTotal()).isEqualTo(4);
      assertThat(decisionDefinitionResultsPage2.getItems()).hasSize(1);
      assertThat(decisionDefinitionResultsPage2.getItems()).extracting(DECISION_ID).containsExactly("invoiceClassification");
      assertThat(decisionDefinitionResultsPage2.getItems()).extracting(VERSION).containsExactly(2);
    });
  }

  @Test
  public void shouldPageWithSearchAfterSizeAndSortedDesc() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      decisionDefinitionResultsPage1 = dao.search(new Query<DecisionDefinition>().setSize(3)
          .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.DESC)));
      decisionDefinitionResultsPage2 = dao.search(new Query<DecisionDefinition>().setSize(3)
          .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.DESC))
          .setSearchAfter(new Object[] { "invoiceAssignApprover", decisionDefinitionResultsPage1.getItems().get(2).getKey() }));
    });
    then(() -> {
      assertThat(decisionDefinitionResultsPage1.getTotal()).isEqualTo(4);
      assertThat(decisionDefinitionResultsPage1.getItems()).hasSize(3);
      assertThat(decisionDefinitionResultsPage1.getItems()).extracting(DECISION_ID)
          .containsExactly("invoiceClassification", "invoiceClassification", "invoiceAssignApprover");
      assertThat(decisionDefinitionResultsPage1.getItems()).extracting(VERSION).containsExactly(1, 2, 1);
      assertThat(decisionDefinitionResultsPage2.getTotal()).isEqualTo(4);
      assertThat(decisionDefinitionResultsPage2.getItems()).hasSize(1);
      assertThat(decisionDefinitionResultsPage2.getItems()).extracting(DECISION_ID).containsExactly("invoiceAssignApprover");
      assertThat(decisionDefinitionResultsPage2.getItems()).extracting(VERSION).containsExactly(2);
    });
  }

  @Test
  public void shouldFilterByFieldAndSortDesc() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      final DecisionDefinition decisionDefinitionFilter = new DecisionDefinition().setName("Invoice Classification");
      decisionDefinitionResults = dao.search(new Query<DecisionDefinition>()
          .setFilter(decisionDefinitionFilter)
          .setSort(Query.Sort.listOf(VERSION, Query.Sort.Order.DESC)));
    });
    then(() -> {
      assertThat(decisionDefinitionResults.getTotal()).isEqualTo(2);
      List<DecisionDefinition> decisionDefinitions = decisionDefinitionResults.getItems();
      assertThat(decisionDefinitions).hasSize(2);
      assertThat(decisionDefinitions).extracting(NAME).containsExactly("Invoice Classification", "Invoice Classification");
      assertThat(decisionDefinitions).extracting(VERSION).containsExactly(2, 1);
    });
  }

  @Test
  public void shouldFilterByMultipleFields() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      final DecisionDefinition decisionDefinitionFilter = new DecisionDefinition().setName("Invoice Classification").setDecisionRequirementsVersion(2);
      decisionDefinitionResults = dao.search(new Query<DecisionDefinition>()
          .setFilter(decisionDefinitionFilter));
    });
    then(() -> {
      assertThat(decisionDefinitionResults.getTotal()).isEqualTo(1);
      List<DecisionDefinition> decisionDefinitions = decisionDefinitionResults.getItems();
      assertThat(decisionDefinitions).hasSize(1);
      assertThat(decisionDefinitions).extracting(NAME).containsExactly("Invoice Classification");
      assertThat(decisionDefinitions).extracting(DECISION_REQUIREMENTS_VERSION).containsExactly(2);
    });
  }

  @Test
  public void shouldFilterAndPageAndSort() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      final DecisionDefinition decisionDefinitionFilter = new DecisionDefinition().setName("Invoice Classification");
      decisionDefinitionResults = dao.search(new Query<DecisionDefinition>()
          .setFilter(decisionDefinitionFilter)
          .setSort(Query.Sort.listOf(VERSION, Query.Sort.Order.DESC))
          .setSize(1));
    });
    then(() -> {
      assertThat(decisionDefinitionResults.getTotal()).isEqualTo(2);
      List<DecisionDefinition> decisionDefinitions = decisionDefinitionResults.getItems();
      assertThat(decisionDefinitions).hasSize(1);
      assertThat(decisionDefinitions).extracting(NAME).containsExactly("Invoice Classification");
      assertThat(decisionDefinitions).extracting(VERSION).containsExactly(2);
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
