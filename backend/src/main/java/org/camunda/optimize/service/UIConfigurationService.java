/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import static org.camunda.optimize.service.util.configuration.OptimizeProfile.CCSM;
import static org.camunda.optimize.service.util.configuration.OptimizeProfile.CLOUD;
import static org.camunda.optimize.service.util.configuration.OptimizeProfile.PLATFORM;

import com.google.common.collect.Lists;
import io.camunda.identity.sdk.Identity;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.MixpanelConfigResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.OnboardingResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.WebappsEndpointDto;
import org.camunda.optimize.rest.cloud.CloudSaasMetaInfoService;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.metadata.OptimizeVersionService;
import org.camunda.optimize.service.tenant.TenantService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.OptimizeProfile;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UIConfigurationService {

  private final ConfigurationService configurationService;
  private final OptimizeVersionService versionService;
  private final TenantService tenantService;
  private final SettingsService settingService;
  private final Environment environment;
  // optional as it is only available conditionally, see implementations of the interface
  private final Optional<CloudSaasMetaInfoService> cloudSaasMetaInfoService;
  private final Identity identity;

  public UIConfigurationResponseDto getUIConfiguration() {
    final UIConfigurationResponseDto uiConfigurationDto = new UIConfigurationResponseDto();
    uiConfigurationDto.setLogoutHidden(configurationService.getUiConfiguration().isLogoutHidden());
    uiConfigurationDto.setEmailEnabled(configurationService.getEmailEnabled());
    uiConfigurationDto.setSharingEnabled(
        settingService.getSettings().getSharingEnabled().orElse(false));
    uiConfigurationDto.setTenantsAvailable(tenantService.isMultiTenantEnvironment());
    uiConfigurationDto.setOptimizeVersion(versionService.getRawVersion());
    uiConfigurationDto.setOptimizeDocsVersion(versionService.getDocsVersion());
    final OptimizeProfile optimizeProfile = ConfigurationService.getOptimizeProfile(environment);
    uiConfigurationDto.setEnterpriseMode(isEnterpriseMode(optimizeProfile));
    uiConfigurationDto.setUserSearchAvailable(isUserSearchAvailable(optimizeProfile));
    uiConfigurationDto.setOptimizeProfile(optimizeProfile.getId());
    uiConfigurationDto.setWebappsEndpoints(getCamundaWebappsEndpoints());
    uiConfigurationDto.setWebhooks(getConfiguredWebhooks());
    uiConfigurationDto.setExportCsvLimit(
        configurationService.getCsvConfiguration().getExportCsvLimit());

    final SettingsResponseDto settings = settingService.getSettings();
    uiConfigurationDto.setMetadataTelemetryEnabled(
        settings.getMetadataTelemetryEnabled().orElse(true));
    uiConfigurationDto.setSettingsManuallyConfirmed(settings.isTelemetryManuallyConfirmed());

    final MixpanelConfigResponseDto mixpanel = uiConfigurationDto.getMixpanel();
    mixpanel.setEnabled(configurationService.getAnalytics().isEnabled());
    mixpanel.setApiHost(configurationService.getAnalytics().getMixpanel().getApiHost());
    mixpanel.setToken(configurationService.getAnalytics().getMixpanel().getToken());
    mixpanel.setOrganizationId(
        configurationService.getAnalytics().getMixpanel().getProperties().getOrganizationId());
    mixpanel.setOsanoScriptUrl(
        configurationService.getAnalytics().getOsano().getScriptUrl().orElse(null));
    mixpanel.setStage(configurationService.getAnalytics().getMixpanel().getProperties().getStage());
    mixpanel.setClusterId(
        configurationService.getAnalytics().getMixpanel().getProperties().getClusterId());

    final OnboardingResponseDto onboarding = uiConfigurationDto.getOnboarding();
    onboarding.setEnabled(configurationService.getOnboarding().isEnabled());
    onboarding.setAppCuesScriptUrl(configurationService.getOnboarding().getAppCuesScriptUrl());
    onboarding.setOrgId(configurationService.getOnboarding().getProperties().getOrganizationId());
    onboarding.setClusterId(configurationService.getOnboarding().getProperties().getClusterId());

    cloudSaasMetaInfoService
        .flatMap(CloudSaasMetaInfoService::getSalesPlanType)
        .ifPresent(onboarding::setSalesPlanType);
    cloudSaasMetaInfoService.ifPresent(
        service -> {
          uiConfigurationDto.setWebappsLinks(service.getWebappsLinks());
          uiConfigurationDto.setNotificationsUrl(
              configurationService.getPanelNotificationConfiguration().getUrl());
        });

    return uiConfigurationDto;
  }

  private boolean isEnterpriseMode(final OptimizeProfile optimizeProfile) {
    if (Arrays.asList(CLOUD, PLATFORM).contains(optimizeProfile)) {
      return true;
    } else if (optimizeProfile.equals(CCSM)) {
      return configurationService.getSecurityConfiguration().getLicense().isEnterprise();
    }
    throw new OptimizeConfigurationException(
        "Could not determine whether Optimize is running in enterprise mode");
  }

  private Map<String, WebappsEndpointDto> getCamundaWebappsEndpoints() {
    Map<String, WebappsEndpointDto> engineNameToEndpoints = new HashMap<>();
    for (Map.Entry<String, EngineConfiguration> entry :
        configurationService.getConfiguredEngines().entrySet()) {
      EngineConfiguration engineConfiguration = entry.getValue();
      WebappsEndpointDto webappsEndpoint = new WebappsEndpointDto();
      String endpointAsString = "";
      if (engineConfiguration.getWebapps().isEnabled()) {
        endpointAsString = engineConfiguration.getWebapps().getEndpoint();
      }
      webappsEndpoint.setEndpoint(endpointAsString);
      webappsEndpoint.setEngineName(engineConfiguration.getName());
      engineNameToEndpoints.put(entry.getKey(), webappsEndpoint);
    }
    return engineNameToEndpoints;
  }

  private List<String> getConfiguredWebhooks() {
    List<String> sortedWebhooksList =
        Lists.newArrayList(configurationService.getConfiguredWebhooks().keySet());
    sortedWebhooksList.sort(String.CASE_INSENSITIVE_ORDER);
    return sortedWebhooksList;
  }

  private boolean isUserSearchAvailable(final OptimizeProfile optimizeProfile) {
    return !CCSM.equals(optimizeProfile) || identity.users().isAvailable();
  }
}
