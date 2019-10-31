/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.ArrayList;
import org.camunda.operate.entities.meta.ImportPositionEntity;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.NoBeansTest;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.camunda.operate.zeebe.ImportValueType;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.stereotype.Component;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(
    classes = { TestApplicationWithNoBeans.class, ImportJob.class, ImportListenerTest.TestImportListener.class })
public class ImportListenerTest extends NoBeansTest {

  @MockBean
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @MockBean
  private ImportPositionHolder importPositionHolder;

  @MockBean
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @MockBean
  private RecordsReaderHolder recordsReaderHolder;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private TestImportListener importListener;

  @Before
  public void before() {
    importListener.cancel();
  }

  @Component
  static class TestImportListener implements ImportListener {

    private boolean finishedCalled = false;
    private boolean failedCalled = false;
    private ImportBatch importBatch;

    @Override
    public void finished(ImportBatch importBatch) {
      finishedCalled = true;
      this.importBatch = importBatch;
    }

    @Override
    public void failed(ImportBatch importBatch) {
      failedCalled = true;
      this.importBatch = importBatch;
    }

    public boolean isFinishedCalled() {
      return finishedCalled;
    }

    public boolean isFailedCalled() {
      return failedCalled;
    }

    public ImportBatch getImportBatch() {
      return importBatch;
    }

    public void cancel() {
      finishedCalled = false;
      failedCalled = false;
      importBatch = null;
    }
  }

  @Test
  public void testFinished() {
    ImportBatch importBatch = new ImportBatch(1, ImportValueType.WORKFLOW_INSTANCE, new ArrayList<>(), null);
    ImportPositionEntity previousPosition = new ImportPositionEntity("alias", 1, 0);
    ImportJob importJob = beanFactory.getBean(ImportJob.class, importBatch, previousPosition);

    //mock import methods
    try {
      doNothing().when(elasticsearchBulkProcessor).persistZeebeRecords(importBatch);
    } catch (PersistenceException e) {
      //ignore
    }

    //when the job is executed
    importJob.call();

    //then
    assertTrue(importListener.isFinishedCalled());
    assertFalse(importListener.isFailedCalled());
    assertEquals(importListener.getImportBatch(), importBatch);
  }

  @Test
  public void testFailed() {
    ImportBatch importBatch = new ImportBatch(1, ImportValueType.WORKFLOW_INSTANCE, new ArrayList<>(), null);
    ImportPositionEntity previousPosition = new ImportPositionEntity("alias", 1, 0);
    ImportJob importJob = beanFactory.getBean(ImportJob.class, importBatch, previousPosition);
    //mock import methods
    try {
      doThrow(new PersistenceException()).when(elasticsearchBulkProcessor).persistZeebeRecords(importBatch);
    } catch (PersistenceException e) {
      //ignore
    }

    //when the job is executed
    importJob.call();

    //then
    assertTrue(importListener.isFailedCalled());
    assertFalse(importListener.isFinishedCalled());
    assertEquals(importListener.getImportBatch(), importBatch);
  }

}
