/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.zeebe.client.ZeebeClient;
import io.zeebe.containers.ZeebeContainer;
import java.time.Instant;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.springframework.beans.factory.annotation.Autowired;

public class OperateZeebeRule extends TestWatcher {
  @Autowired public OperateZeebeRuleProvider operateZeebeRuleProvider;

  @Override
  public void starting(Description description) {
    operateZeebeRuleProvider.starting(description);
  }

  public void updateRefreshInterval(String value) {
    operateZeebeRuleProvider.updateRefreshInterval(value);
  }

  public void refreshIndices(Instant instant) {
    operateZeebeRuleProvider.refreshIndices(instant);
  }

  @Override
  public void finished(Description description) {
    operateZeebeRuleProvider.finished(description);
  }

  @Override
  protected void failed(Throwable e, Description description) {
    operateZeebeRuleProvider.failed(e, description);
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void startZeebe() {
    operateZeebeRuleProvider.startZeebe();
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    operateZeebeRuleProvider.stopZeebe();
  }

  public String getPrefix() {
    return operateZeebeRuleProvider.getPrefix();
  }

  //  public void setPrefix(String prefix) {
  //    this.prefix = prefix;
  //  }
  //
  public ZeebeContainer getZeebeContainer() {
    return operateZeebeRuleProvider.getZeebeContainer();
  }

  //  public void setOperateProperties(final OperateProperties operateProperties) {
  //    this.operateProperties = operateProperties;
  //  }
  //
  //  public void setZeebeEsClient(final RestHighLevelClient zeebeEsClient) {
  //    this.zeebeEsClient = zeebeEsClient;
  //  }
  //
  public ZeebeClient getClient() {
    return operateZeebeRuleProvider.getClient();
  }
}
