/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.RecordsReader;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SearchTestRule extends TestWatcher {

  protected static final Logger logger = LoggerFactory.getLogger(SearchTestRule.class);

  @Autowired
  protected SearchTestRuleProvider searchTestRuleProvider;

  public SearchTestRule() {
  }

  public SearchTestRule(String indexPrefix) {
    searchTestRuleProvider.setIndexPrefix(indexPrefix);
  }

  @Override
  protected void failed(Throwable e, Description description) {
    super.failed(e, description);
    searchTestRuleProvider.failed(e, description);
  }

  @Override
  protected void starting(Description description) {
    searchTestRuleProvider.starting(description);
  }

  @Override
  protected void finished(Description description) {
      searchTestRuleProvider.finished(description);
  }

  public void assertMaxOpenScrollContexts(final int maxOpenScrollContexts) {
    searchTestRuleProvider.assertMaxOpenScrollContexts(maxOpenScrollContexts);
  }

  public void refreshSerchIndexes() {
    searchTestRuleProvider.refreshSearchIndices();
  }

  public void refreshZeebeIndices() {
    searchTestRuleProvider.refreshZeebeIndices();
  }

  public void refreshOperateSearchIndices() {
    searchTestRuleProvider.refreshOperateSearchIndices();
  }

  public void processAllRecordsAndWait(Integer maxWaitingRounds, Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(maxWaitingRounds, predicate, arguments);
  }

  public void processAllRecordsAndWait(Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(predicate, arguments);
  }
  public void processAllRecordsAndWait(Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(predicate, supplier, arguments);
  }

  public void processAllRecordsAndWait(boolean runPostImport, Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(runPostImport, predicate, supplier, arguments);
  }

  public void processRecordsWithTypeAndWait(ImportValueType importValueType, Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processRecordsWithTypeAndWait(importValueType, predicate, arguments);
  }
  public void processRecordsWithTypeAndWait(ImportValueType importValueType, boolean runPostImport, Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processRecordsWithTypeAndWait(importValueType, runPostImport, predicate, arguments);
  }

  public void processRecordsAndWaitFor(Collection<RecordsReader> readers, Integer maxWaitingRounds, boolean runPostImport,
      Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    searchTestRuleProvider.processRecordsAndWaitFor(readers, maxWaitingRounds, runPostImport, predicate, supplier, arguments);
  }

  public void runPostImportActions() {
    searchTestRuleProvider.runPostImportActions();
  }

  public boolean areIndicesCreatedAfterChecks(String indexPrefix, int minCountOfIndices,int maxChecks) {
    return searchTestRuleProvider.areIndicesCreatedAfterChecks(indexPrefix, minCountOfIndices, maxChecks);
  }

  public List<RecordsReader> getRecordsReaders(ImportValueType importValueType) {
    return searchTestRuleProvider.getRecordsReaders(importValueType);
  }

  public void persistNew(OperateEntity... entitiesToPersist) {
    searchTestRuleProvider.persistNew(entitiesToPersist);
  }

  public void persistOperateEntitiesNew(List<? extends OperateEntity> operateEntities) throws PersistenceException {
    searchTestRuleProvider.persistOperateEntitiesNew(operateEntities);
  }

  public Map<Class<? extends OperateEntity>, String> getEntityToAliasMap(){
    return searchTestRuleProvider.getEntityToAliasMap();
  }

  public int getOpenScrollcontextSize() {
    return searchTestRuleProvider.getOpenScrollcontextSize();
  }
}
