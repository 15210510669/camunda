/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.elasticsearch.writer;

import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.DecisionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Profile("!opensearch")
@Component
public class DecisionWriter implements io.camunda.operate.webapp.writer.DecisionWriter {

  private static final Logger logger = LoggerFactory.getLogger(DecisionWriter.class);

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private DecisionStore decisionStore;

  @Override
  public long deleteDecisionRequirements(long decisionRequirementsKey) throws IOException {
    return decisionStore.deleteDocuments(decisionRequirementsIndex.getAlias(), DecisionRequirementsIndex.KEY, String.valueOf(decisionRequirementsKey));
  }

  @Override
  public long deleteDecisionDefinitionsFor(long decisionRequirementsKey) throws IOException {
    return decisionStore.deleteDocuments(decisionIndex.getAlias(), DecisionIndex.DECISION_REQUIREMENTS_KEY, String.valueOf(decisionRequirementsKey));
  }

  @Override
  public long deleteDecisionInstancesFor(long decisionRequirementsKey) throws IOException {
    return decisionStore.deleteDocuments(decisionInstanceTemplate.getAlias(), DecisionInstanceTemplate.DECISION_REQUIREMENTS_KEY, String.valueOf(decisionRequirementsKey));
  }
}
