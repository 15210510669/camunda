/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

public class ProcessCacheZeebeIT extends OperateZeebeAbstractIT {

  @SpyBean private ProcessCache processCache;

  @After
  public void after() {
    // clean the cache
    processCache.clearCache();
    super.after();
  }

  @Test
  public void testProcessDoesNotExist() {
    final String processNameDefault =
        processCache.getProcessNameOrDefaultValue(2L, "default_value");
    assertThat(processNameDefault).isEqualTo("default_value");
  }

  @Test
  public void testProcessVersionAndNameReturnedAndReused() {
    Long processDefinitionKey1 =
        ZeebeTestUtil.deployProcess(zeebeClient, null, "demoProcess_v_1.bpmn");
    Long processDefinitionKey2 =
        ZeebeTestUtil.deployProcess(zeebeClient, null, "processWithGateway.bpmn");

    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey1);
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey2);

    String demoProcessName = processCache.getProcessNameOrDefaultValue(processDefinitionKey1, null);
    assertThat(demoProcessName).isNotNull();

    // request once again, the cache should be used
    demoProcessName = processCache.getProcessNameOrDefaultValue(processDefinitionKey1, null);
    assertThat(demoProcessName).isNotNull();

    verify(processCache, times(1)).putToCache(any(), any());
  }

  @Test
  public void testProcessFlowNodeNameReturnedAndReused() {
    Long processDefinitionKey1 =
        ZeebeTestUtil.deployProcess(zeebeClient, null, "demoProcess_v_1.bpmn");
    Long processDefinitionKey2 =
        ZeebeTestUtil.deployProcess(zeebeClient, null, "processWithGateway.bpmn");

    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey1);
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey2);

    String flowNodeName =
        processCache.getFlowNodeNameOrDefaultValue(processDefinitionKey1, "start", null);
    assertThat(flowNodeName).isEqualTo("start");

    // request once again, the cache should be used
    flowNodeName = processCache.getFlowNodeNameOrDefaultValue(processDefinitionKey1, "start", null);
    assertThat(flowNodeName).isEqualTo("start");

    verify(processCache, times(1)).putToCache(any(), any());
  }
}
