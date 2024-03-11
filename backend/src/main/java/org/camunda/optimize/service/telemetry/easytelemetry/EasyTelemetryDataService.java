/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.easytelemetry;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.camunda.bpm.licensecheck.LicenseKeyImpl;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.dto.optimize.query.telemetry.DatabaseDto;
import org.camunda.optimize.dto.optimize.query.telemetry.InternalsDto;
import org.camunda.optimize.dto.optimize.query.telemetry.LicenseKeyDto;
import org.camunda.optimize.dto.optimize.query.telemetry.ProductDto;
import org.camunda.optimize.dto.optimize.query.telemetry.TelemetryDataDto;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.schema.DatabaseMetadataService;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.service.telemetry.TelemetryDataConstants;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CamundaPlatformCondition.class)
@AllArgsConstructor
@Slf4j
public class EasyTelemetryDataService {

  public static final String INFORMATION_UNAVAILABLE_STRING = "Unknown";
  public static final String OPTIMIZE_FEATURE = TelemetryDataConstants.OPTIMIZE_PRODUCT;
  public static final String CAMUNDA_BPM_FEATURE = "camundaBPM";
  public static final String CAWEMO_FEATURE = "cawemo";
  public static final Set<String> FEATURE_NAMES =
      Set.of(OPTIMIZE_FEATURE, CAMUNDA_BPM_FEATURE, CAWEMO_FEATURE);

  private final DatabaseMetadataService databaseMetadataService;
  private final EngineContextFactory engineContextFactory;
  private final LicenseManager licenseManager;
  private final DatabaseClient databaseClient;

  public TelemetryDataDto getTelemetryData() {
    Optional<MetadataDto> metadata = Optional.empty();
    try {
      metadata = databaseMetadataService.readMetadata(databaseClient);
    } catch (final Exception e) {
      log.error("Failed retrieving Optimize metadata.", e);
    }

    return TelemetryDataDto.builder()
        .installation(
            metadata.map(MetadataDto::getInstallationId).orElse(INFORMATION_UNAVAILABLE_STRING))
        .product(getProductData())
        .build();
  }

  private ProductDto getProductData() {
    return ProductDto.builder().version(Version.RAW_VERSION).internals(getInternalsData()).build();
  }

  private InternalsDto getInternalsData() {
    return InternalsDto.builder()
        .engineInstallationIds(getEngineInstallationIds())
        .database(getDatabaseData())
        .licenseKey(getLicenseKeyData())
        .build();
  }

  private List<String> getEngineInstallationIds() {
    return engineContextFactory.getConfiguredEngines().stream()
        .map(
            engineContext ->
                engineContext.getInstallationId().orElse(INFORMATION_UNAVAILABLE_STRING))
        .toList();
  }

  private DatabaseDto getDatabaseData() {
    String dbVersion = null;
    String dbVendor = null;
    try {
      dbVersion = databaseClient.getDatabaseVersion();
      dbVendor = databaseClient.getDatabaseVendor().toString();
    } catch (final IOException e) {
      log.info("Failed to retrieve Database version and vendor for telemetry data.");
    }
    return DatabaseDto.builder()
        .version(Optional.ofNullable(dbVersion).orElse(INFORMATION_UNAVAILABLE_STRING))
        .vendor(Optional.ofNullable(dbVendor).orElse(INFORMATION_UNAVAILABLE_STRING))
        .build();
  }

  private LicenseKeyDto getLicenseKeyData() {
    final Optional<LicenseKey> licenseKey = getLicenseKey();
    final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    final String customer =
        licenseKey.map(LicenseKey::getCustomerId).orElse(INFORMATION_UNAVAILABLE_STRING);
    final String licenseType =
        licenseKey.map(key -> key.getLicenseType().name()).orElse(INFORMATION_UNAVAILABLE_STRING);
    final String validUntil =
        licenseKey
            .map(LicenseKey::getValidUntil)
            .filter(Objects::nonNull)
            .map(dateFormatter::format)
            .orElse(INFORMATION_UNAVAILABLE_STRING);
    final boolean isUnlimited = licenseKey.map(LicenseKey::isUnlimited).orElse(false);
    final Map<String, String> features =
        licenseKey
            .map(LicenseKey::getProperties)
            .map(this::mapPropertiesToFeaturesMap)
            .orElse(Collections.emptyMap());
    final String raw =
        licenseKey.map(LicenseKey::getLicenseBody).orElse(INFORMATION_UNAVAILABLE_STRING);

    return LicenseKeyDto.builder()
        .customer(customer)
        .type(licenseType)
        .validUntil(validUntil)
        .unlimited(isUnlimited)
        .features(features)
        .raw(raw)
        .build();
  }

  private Optional<LicenseKey> getLicenseKey() {
    return licenseManager
        .getOptimizeLicense()
        .flatMap(
            licenseKeyString -> {
              try {
                return Optional.of(new LicenseKeyImpl(licenseKeyString));
              } catch (final InvalidLicenseException e) {
                log.info("Failed to retrieve LicenseKey information for telemetry data.");
                return Optional.empty();
              }
            });
  }

  private Map<String, String> mapPropertiesToFeaturesMap(final Map<String, String> properties) {
    final Map<String, String> features = getDefaultFeaturesMap();
    FEATURE_NAMES.forEach(
        featureName -> {
          if (properties.containsKey(featureName)) {
            features.put(featureName, properties.get(featureName));
          }
        });

    return features;
  }

  private Map<String, String> getDefaultFeaturesMap() {
    final Map<String, String> features = new HashMap<>();
    features.put(OPTIMIZE_FEATURE, "true");
    features.put(CAMUNDA_BPM_FEATURE, "false");
    features.put(CAWEMO_FEATURE, "false");
    return features;
  }
}
