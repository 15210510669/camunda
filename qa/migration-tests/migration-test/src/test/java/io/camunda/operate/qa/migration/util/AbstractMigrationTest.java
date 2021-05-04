/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.qa.migration.util;

import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.qa.util.migration.TestContext;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.schema.templates.*;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import static org.junit.Assume.assumeTrue;

@RunWith(SpringRunner.class)
@ContextConfiguration
@TestPropertySource(locations = "/test.properties")
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractMigrationTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractMigrationTest.class);

  @Autowired
  protected EntityReader entityReader;

  @Autowired
  protected ListViewTemplate listViewTemplate;

  @Autowired
  protected EventTemplate eventTemplate;

  @Autowired
  protected SequenceFlowTemplate sequenceFlowTemplate;

  @Autowired
  protected FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  protected VariableTemplate variableTemplate;

  @Autowired
  protected IncidentTemplate incidentTemplate;

  @Autowired
  protected ImportPositionIndex importPositionIndex;

  @Autowired
  protected OperationTemplate operationTemplate;

  @Autowired
  protected UserIndex userIndex;

  @Autowired
  protected RestHighLevelClient esClient;

  @Autowired
  protected TestContext testContext;

  protected void assumeThatProcessIsUnderTest(String bpmnProcessId) {
    assumeTrue(testContext.getProcessesToAssert().contains(bpmnProcessId));
  }

  @Configuration
  @ComponentScan(basePackages = {
      "io.camunda.operate.property",
      "io.camunda.operate.schema.indices","io.camunda.operate.schema.templates",
      "io.camunda.operate.qa.migration",
      "io.camunda.operate.util.rest"},
      nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
  public static class SpringConfig {
  }
}
