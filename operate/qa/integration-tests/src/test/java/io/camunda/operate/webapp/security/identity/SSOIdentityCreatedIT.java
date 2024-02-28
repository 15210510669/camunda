/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.identity.sdk.Identity;
import io.camunda.operate.property.OperateProperties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ActiveProfiles({"sso-auth"})
@SpringBootTest(
    classes = {IdentityConfigurer.class, OperateProperties.class},
    properties = {
      OperateProperties.PREFIX + ".identity.resourcePermissionsEnabled = true",
      "camunda.identity.baseUrl=http://IdentiyURL:8080"
    })
public class SSOIdentityCreatedIT {

  @Autowired
  @Qualifier("saasIdentity")
  private Identity identity;

  @Autowired private PermissionsService permissionsService;

  @Test
  public void testIdentityIsCreated() {
    assertThat(identity).isNotNull();
    assertThat(permissionsService).isNotNull();
  }
}
