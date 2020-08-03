/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.webapp.es.cache.WorkflowCache;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import io.zeebe.tasklist.webapp.security.UserReader;
import io.zeebe.tasklist.zeebe.PartitionHolder;
import io.zeebe.tasklist.zeebeimport.ImportPositionHolder;
import io.zeebe.test.ClientRule;
import io.zeebe.test.EmbeddedBrokerRule;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public abstract class TasklistZeebeIntegrationTest extends TasklistIntegrationTest {

  public static final String USERNAME_DEFAULT = "demo";
  @Autowired public BeanFactory beanFactory;
  @Rule public final TasklistZeebeRule zeebeRule;
  public EmbeddedBrokerRule brokerRule;
  @Rule public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @MockBean protected ZeebeClient mockedZeebeClient;
  // we don't want to create ZeebeClient, we will rather use the one from
  // test rule
  protected ZeebeClient zeebeClient;
  protected ClientRule clientRule;
  @Autowired protected PartitionHolder partitionHolder;
  @Autowired protected ImportPositionHolder importPositionHolder;
  @Autowired protected TasklistProperties tasklistProperties;
  protected TasklistTester tester;
  @MockBean protected UserReader userReader;
  @Autowired private WorkflowCache workflowCache;
  private String workerName;
  @Autowired private MeterRegistry meterRegistry;

  public TasklistZeebeIntegrationTest() {
    zeebeRule = new TasklistZeebeRule();
  }

  @Before
  public void before() {
    super.before();

    clientRule = zeebeRule.getClientRule();
    assertThat(clientRule).as("clientRule is not null").isNotNull();
    brokerRule = zeebeRule.getBrokerRule();
    assertThat(brokerRule).as("brokerRule is not null").isNotNull();

    zeebeClient = getClient();
    workerName = TestUtil.createRandomString(10);

    tester = beanFactory.getBean(TasklistTester.class, zeebeClient, elasticsearchTestRule);

    workflowCache.clearCache();
    importPositionHolder.clearCache();
    try {
      FieldSetter.setField(
          partitionHolder, PartitionHolder.class.getDeclaredField("zeebeClient"), getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }

    setDefaultCurrentUser();
  }

  protected void setDefaultCurrentUser() {
    setCurrentUser(getDefaultCurrentUser());
  }

  protected UserDTO getDefaultCurrentUser() {
    return new UserDTO().setUsername(USERNAME_DEFAULT).setFirstname("Demo").setLastname("User");
  }

  protected void setCurrentUser(UserDTO user) {
    Mockito.when(userReader.getCurrentUser()).thenReturn(user);
    Mockito.when(userReader.getUsersByUsernames(any())).thenReturn(List.of(user));
  }

  @After
  public void after() {
    setDefaultCurrentUser();
    workflowCache.clearCache();
    importPositionHolder.clearCache();
  }

  public ZeebeClient getClient() {
    return clientRule.getClient();
  }

  public BrokerCfg getBrokerCfg() {
    return brokerRule.getBrokerCfg();
  }

  public String getWorkerName() {
    return workerName;
  }

  protected void clearMetrics() {
    for (Meter meter : meterRegistry.getMeters()) {
      meterRegistry.remove(meter);
    }
  }
}
