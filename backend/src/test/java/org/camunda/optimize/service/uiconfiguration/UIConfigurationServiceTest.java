/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.uiconfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CLOUD_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_DATABASE_PROPERTY;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.PLATFORM_PROFILE;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.users.Users;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.rest.cloud.CloudSaasMetaInfoService;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.UIConfigurationService;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.metadata.PlatformOptimizeVersionService;
import org.camunda.optimize.service.tenant.TenantService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.DatabaseType;
import org.camunda.optimize.service.util.configuration.OptimizeProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UIConfigurationServiceTest {

  @InjectMocks UIConfigurationService underTest;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ConfigurationService configurationService;

  @Mock private PlatformOptimizeVersionService versionService;
  @Mock private TenantService tenantService;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SettingsService settingService;

  @Mock private Environment environment;
  @Mock private Optional<CloudSaasMetaInfoService> metaInfoService = Optional.empty();
  @Mock private Identity identity;
  @Mock private Users identityUsers;

  private static Stream<Arguments> optimizeProfiles() {
    return Stream.of(
        Arguments.of(CLOUD_PROFILE), Arguments.of(CCSM_PROFILE), Arguments.of(PLATFORM_PROFILE));
  }

  private static Stream<Arguments> optimizeProfilesAndExpectedIsEnterpriseModeResult() {
    return Stream.of(
        Arguments.of(CLOUD_PROFILE, true),
        Arguments.of(CCSM_PROFILE, false), // false by default because it's not mocked
        Arguments.of(PLATFORM_PROFILE, true));
  }

  @ParameterizedTest
  @MethodSource("optimizeProfiles")
  public void testProfileReadCorrectly(final String activeProfile) {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {activeProfile});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getOptimizeProfile())
        .isEqualTo(OptimizeProfile.toProfile(activeProfile));
  }

  @Test
  public void testDefaultProfileUsed() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getOptimizeProfile()).isEqualTo(OptimizeProfile.PLATFORM);
  }

  @Test
  public void testMultipleProfilesDoesNotWork() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {CLOUD_PROFILE, CCSM_PROFILE});

    // then
    assertThatThrownBy(() -> underTest.getUIConfiguration())
        .isInstanceOf(OptimizeConfigurationException.class)
        .hasMessage("Cannot configure more than one Optimize profile");
  }

  @Test
  public void testUnknownProfileUsesDefault() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {"someUnknownProfile"});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getOptimizeProfile()).isEqualTo(OptimizeProfile.PLATFORM);
  }

  @ParameterizedTest
  @MethodSource("optimizeProfilesAndExpectedIsEnterpriseModeResult")
  public void testIsEnterpriseModeDeterminedCorrectly(
      final String activeProfile, final boolean expectedIsEnterprise) {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {activeProfile});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.isEnterpriseMode()).isEqualTo(expectedIsEnterprise);
  }

  private void initializeMocks() {
    when(configurationService.getConfiguredWebhooks()).thenReturn(Collections.emptyMap());
    when(identity.users()).thenReturn(identityUsers);
    when(identityUsers.isAvailable()).thenReturn(true);
    when(environment.getProperty(CAMUNDA_OPTIMIZE_DATABASE, ELASTICSEARCH_DATABASE_PROPERTY))
        .thenReturn(DatabaseType.ELASTICSEARCH.toString());
  }
}
