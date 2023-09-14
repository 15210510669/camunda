/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.NoBeansTest;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.es.ImportBatchElasticSearch;
import io.camunda.tasklist.zeebeimport.es.ImportJobElasticSearch;
import io.camunda.tasklist.zeebeimport.v830.processors.es.BulkProcessorElasticSearch;
import java.util.ArrayList;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      ImportJobElasticSearch.class,
      ImportListenerTestElasticSearch.TestImportListener.class
    })
@Conditional(ElasticSearchCondition.class)
public class ImportListenerTestElasticSearch extends NoBeansTest {

  @MockBean private ImportBatchProcessorFactory importBatchProcessorFactory;

  @MockBean private BulkProcessorElasticSearch elasticsearchBulkProcessor;

  @MockBean private ImportPositionHolder importPositionHolder;

  @MockBean
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @MockBean private RecordsReaderHolder recordsReaderHolder;

  @MockBean private TasklistProperties tasklistProperties;

  @Autowired private BeanFactory beanFactory;

  @Autowired private TestImportListener importListener;

  @Before
  public void before() {
    importListener.cancel();
  }

  @Test
  public void testFinished() throws Exception {
    final ImportBatch importBatchElasticSearch =
        new ImportBatchElasticSearch(
            1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), "some_name");
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(
            ImportJobElasticSearch.class, importBatchElasticSearch, previousPosition);

    // mock import methods
    try {
      when(importBatchProcessorFactory.getImportBatchProcessor(anyString()))
          .thenReturn(elasticsearchBulkProcessor);
      doNothing().when(elasticsearchBulkProcessor).performImport(importBatchElasticSearch);
    } catch (PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertTrue(importListener.isFinishedCalled());
    assertFalse(importListener.isFailedCalled());
    assertEquals(importListener.getImportBatch(), importBatchElasticSearch);
  }

  @Test
  public void testFailed() throws Exception {
    final ImportBatchElasticSearch importBatchElasticSearch =
        new ImportBatchElasticSearch(1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), null);
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(
            ImportJobElasticSearch.class, importBatchElasticSearch, previousPosition);
    // mock import methods
    try {
      doThrow(new PersistenceException())
          .when(elasticsearchBulkProcessor)
          .performImport(importBatchElasticSearch);
    } catch (PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertTrue(importListener.isFailedCalled());
    assertFalse(importListener.isFinishedCalled());
    assertEquals(importListener.getImportBatch(), importBatchElasticSearch);
  }

  @Component
  static class TestImportListener implements ImportListener {

    private boolean finishedCalled = false;
    private boolean failedCalled = false;
    private ImportBatch importBatchElasticSearch;

    @Override
    public void finished(ImportBatch importBatchElasticSearch) {
      finishedCalled = true;
      this.importBatchElasticSearch = importBatchElasticSearch;
    }

    @Override
    public void failed(ImportBatch importBatchElasticSearch) {
      failedCalled = true;
      this.importBatchElasticSearch = importBatchElasticSearch;
    }

    public boolean isFinishedCalled() {
      return finishedCalled;
    }

    public boolean isFailedCalled() {
      return failedCalled;
    }

    public ImportBatch getImportBatch() {
      return importBatchElasticSearch;
    }

    public void cancel() {
      finishedCalled = false;
      failedCalled = false;
      importBatchElasticSearch = null;
    }
  }
}
