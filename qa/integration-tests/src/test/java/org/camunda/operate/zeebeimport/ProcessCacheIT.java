/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.cache.ProcessCache;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

public class ProcessCacheIT extends OperateZeebeIntegrationTest {

  @SpyBean
  private ProcessCache processCache;

  @After
  public void after() {
    //clean the cache
    processCache.clearCache();
    super.after();
  }

  @Test
  public void testProcessDoesNotExist() {
    final String processNameDefault = processCache.getProcessNameOrDefaultValue(2L,"default_value");
    assertThat(processNameDefault).isEqualTo("default_value");
  }

  @Test
  public void testProcessVersionAndNameReturnedAndReused() {
    Long processDefinitionKey1 = ZeebeTestUtil.deployProcess(zeebeClient, "demoProcess_v_1.bpmn");
    Long processDefinitionKey2 = ZeebeTestUtil.deployProcess(zeebeClient, "processWithGateway.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey2);

    String demoProcessName = processCache.getProcessNameOrDefaultValue(processDefinitionKey1,null);
    assertThat(demoProcessName).isNotNull();

    //request once again, the cache should be used
    demoProcessName = processCache.getProcessNameOrDefaultValue(processDefinitionKey1,null);
    assertThat(demoProcessName).isNotNull();

    verify(processCache, times(1)).putToCache(any(), any());
  }

}
