/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.secured.es;

import org.camunda.optimize.service.metadata.PreviousVersion;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.main.UpgradeProcedureFactory;
import org.camunda.optimize.upgrade.plan.factories.CurrentVersionNoOperationUpgradePlanFactory;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.util.UpgradeUtil.createUpgradeDependenciesWithAdditionalConfigLocation;

public abstract class AbstractConnectToElasticsearchIT {

  @RegisterExtension
  public EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension(getClass().getCanonicalName());

  protected abstract String getCustomConfigFile();

  @BeforeEach
  public void before() throws Exception {
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();
  }

  @Test
  public void connectToSecuredElasticsearch() {
    // given a license and a secured optimize -> es connection
    String license = FileReaderUtil.readValidTestLicense();

    // when doing a request to add the license to optimize
    Response response =
      embeddedOptimizeExtension.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .withoutAuthentication()
        .execute();

    // then Optimize should be able to successfully perform the underlying request to elasticsearch
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void runUpgradeAgainstSecuredElasticSearch() {
    // given an upgrade procedure against ES with custom configuration
    final UpgradeProcedure testUpgradeProcedure = UpgradeProcedureFactory
      .create(createUpgradeDependenciesWithAdditionalConfigLocation(getCustomConfigFile()));
    // the metadata version needs to match the stated versionFrom for the upgrade to pass validation
    embeddedOptimizeExtension.getElasticsearchMetadataService().upsertMetadata(
      embeddedOptimizeExtension.getOptimizeElasticClient(), PreviousVersion.PREVIOUS_VERSION
    );

    // when
    embeddedOptimizeExtension.stopOptimize();

    // then
    testUpgradeProcedure.performUpgrade(new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan());
  }

}
