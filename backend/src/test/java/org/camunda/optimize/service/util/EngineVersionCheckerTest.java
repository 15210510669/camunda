/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.service.metadata.Version;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.metadata.Version.getMajorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getMinorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getPatchVersionFrom;

public class EngineVersionCheckerTest {

  public static final List<String> SUPPORTED_ENGINES = EngineVersionChecker.getSupportedEngines();

  @ParameterizedTest
  @MethodSource("validVersions")
  public void testValidEngineVersions(final String version) {
    final boolean isSupported = EngineVersionChecker.isVersionSupported(
      version,
      EngineVersionChecker.getSupportedEngines()
    );

    assertThat(isSupported).isTrue();
  }

  @ParameterizedTest
  @MethodSource("invalidVersions")
  public void testInvalidEngineVersions(final String version) {
    final boolean isSupported = EngineVersionChecker.isVersionSupported(
      version,
      EngineVersionChecker.getSupportedEngines()
    );

    assertThat(isSupported).isFalse();
  }

  private static Stream<String> validVersions() {
    List<String> validVersionsToTest = new ArrayList<>();
    for (String supportedVersion : SUPPORTED_ENGINES) {
      validVersionsToTest.add(supportedVersion);
      final String major = getMajorVersionFrom(supportedVersion);
      final String minor = getMinorVersionFrom(supportedVersion);
      final String patch = getPatchVersionFrom(supportedVersion);
      validVersionsToTest.add(buildVersionFromParts(incrementVersionPart(major), minor, patch));
      validVersionsToTest.add(buildVersionFromParts(major, incrementVersionPart(minor), patch));
      validVersionsToTest.add(buildVersionFromParts(major, minor, incrementVersionPart(patch)));
    }
    return validVersionsToTest.stream();
  }

  private static Stream<String> invalidVersions() {
    List<String> invalidVersions = new ArrayList<>();
    invalidVersions.addAll(findUnsupportedMajorVersions());
    invalidVersions.addAll(findUnsupportedMinorVersions());
    invalidVersions.addAll(findUnsupportedPatchVersions());
    return invalidVersions.stream();
  }

  private static List<String> findUnsupportedPatchVersions() {
    return SUPPORTED_ENGINES.stream()
      .collect(Collectors.groupingBy(Version::getMajorVersionFrom)).entrySet().stream()
      .flatMap(majorEntry -> majorEntry.getValue()
        .stream().collect(Collectors.groupingBy(Version::getMinorVersionFrom)).entrySet().stream()
        .map(minorEntry -> {
          final long minSupportedPatchForMinor = minorEntry.getValue()
            .stream()
            .mapToLong(plainVersion -> Long.parseLong(getPatchVersionFrom(plainVersion)))
            .min().getAsLong();
          if (minSupportedPatchForMinor != 0) {
            return buildVersionFromParts(
              majorEntry.getKey(),
              minorEntry.getKey(),
              String.valueOf(minSupportedPatchForMinor - 1)
            );
          }
          return null;
        })
        .filter(Objects::nonNull))
      .collect(Collectors.toList());
  }

  private static List<String> findUnsupportedMinorVersions() {
    return SUPPORTED_ENGINES.stream()
      .collect(Collectors.groupingBy(Version::getMajorVersionFrom)).entrySet().stream()
      .map(majorEntry -> {
        final long minSupportedMinorForMajor = majorEntry.getValue()
          .stream()
          .mapToLong(plainVersion -> Long.parseLong(getMinorVersionFrom(plainVersion)))
          .min().getAsLong();
        if (minSupportedMinorForMajor != 0) {
          return buildVersionFromParts(majorEntry.getKey(), String.valueOf(minSupportedMinorForMajor - 1), "0");
        }
        return null;
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  private static List<String> findUnsupportedMajorVersions() {
    long oldestSupportedMajor = SUPPORTED_ENGINES.stream()
                       .mapToLong(version -> Long.parseLong(getMajorVersionFrom(version)))
                       .min().getAsLong();
    return LongStream.range(0, oldestSupportedMajor).boxed()
      .map(majorVersion -> buildVersionFromParts(String.valueOf(majorVersion), "0", "0"))
      .collect(Collectors.toList());
  }

  private static String incrementVersionPart(final String versionPart) {
    return String.valueOf(Long.parseLong(versionPart) + 1);
  }

  private static String buildVersionFromParts(final String major, final String minor, final String patch) {
    return String.join(".", major, minor, patch);
  }

}
