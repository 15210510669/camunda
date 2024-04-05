/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.Application.SPRING_THYMELEAF_PREFIX_KEY;
import static io.camunda.tasklist.Application.SPRING_THYMELEAF_PREFIX_VALUE;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.zeebe.PartitionHolder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + "importer.jobType = testJobType",
      "graphql.servlet.exception-handlers-enabled = true",
      "management.endpoints.web.exposure.include = info,prometheus,loggers,usage-metrics",
      SPRING_THYMELEAF_PREFIX_KEY + " = " + SPRING_THYMELEAF_PREFIX_VALUE,
      "server.servlet.session.cookie.name = " + TasklistURIs.COOKIE_JSESSIONID
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class TasklistIntegrationTest {

  protected OffsetDateTime testStartTime;

  @BeforeEach
  public void before() {
    testStartTime = OffsetDateTime.now();
  }

  protected void mockPartitionHolder(PartitionHolder partitionHolder) {
    final List<Integer> partitions = new ArrayList<>();
    partitions.add(1);
    when(partitionHolder.getPartitionIds()).thenReturn(partitions);
  }
}
