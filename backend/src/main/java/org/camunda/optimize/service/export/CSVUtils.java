/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

@Slf4j
public class CSVUtils {

  static final String VARIABLE_PREFIX = "variable:";
  static final String INPUT_PREFIX = "input:";
  static final String OUTPUT_PREFIX = "output:";

  public static byte[] mapCsvLinesToCsvBytes(final List<String[]> csvStrings) {
    final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
    final BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(arrayOutputStream));
    final CSVWriter csvWriter = new CSVWriter(bufferedWriter, ',', '"', '\'', "\r\n");

    byte[] bytes = null;
    try {
      csvWriter.writeAll(csvStrings);
      bufferedWriter.flush();
      bufferedWriter.close();
      arrayOutputStream.flush();
      bytes = arrayOutputStream.toByteArray();
      arrayOutputStream.close();
    } catch (Exception e) {
      log.error("can't write CSV to buffer", e);
    }
    return bytes;
  }

  public static <T extends IdDto> List<String[]> mapIdList(final List<T> ids) {
    final List<String[]> result = new ArrayList<>();

    result.add(new String[]{"processInstanceId"});

    ids.forEach(idDto -> result.add(new String[]{idDto.getId()}));

    return result;
  }

  public static List<String[]> mapRawProcessReportInstances(List<RawDataProcessInstanceDto> rawData) {
    return mapRawProcessReportInstances(rawData, null, null, Collections.emptySet());
  }

  public static List<String[]> mapRawProcessReportInstances(List<RawDataProcessInstanceDto> rawData,
                                                            Integer limit,
                                                            Integer offset,
                                                            Set<String> excludedColumns) {
    final List<String[]> result = new ArrayList<>();

    // column names contain prefixes that must be stripped off to get the plain keys needed for exclusion
    final Set<String> excludedKeys = stripOffPrefixes(excludedColumns, VARIABLE_PREFIX);

    final List<String> includedDtoFields = extractAllDtoFieldKeys(RawDataProcessInstanceDto.class);
    includedDtoFields.removeAll(excludedKeys);
    final List<String> includedVariableKeys = extractAllVariableKeys(rawData);
    includedVariableKeys.removeAll(excludedKeys);

    final List<String> allIncludedKeys = union(includedDtoFields, includedVariableKeys);
    final String[] headerLine = constructRawProcessHeaderLine(includedDtoFields, includedVariableKeys);
    result.add(headerLine);

    int currentPosition = 0;
    for (RawDataProcessInstanceDto instanceDto : rawData) {
      boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      if ((offset == null && limitNotExceeded) || (isOffsetPassed(offset, currentPosition) && limitNotExceeded)) {
        final String[] dataLine = new String[allIncludedKeys.size()];
        for (int i = 0; i < dataLine.length; i++) {
          final String currentKey = allIncludedKeys.get(i);
          final Optional<String> optionalValue = includedVariableKeys.contains(currentKey)
            ? getVariableValue(instanceDto, currentKey)
            : getDtoFieldValue(instanceDto, RawDataProcessInstanceDto.class, currentKey);
          dataLine[i] = optionalValue.orElse(null);
        }
        result.add(dataLine);
      }
      currentPosition = currentPosition + 1;
    }

    return result;
  }

  public static List<String[]> mapRawDecisionReportInstances(List<RawDataDecisionInstanceDto> rawData) {
    return mapRawDecisionReportInstances(rawData, null, null, Collections.emptySet());
  }

  public static List<String[]> mapRawDecisionReportInstances(List<RawDataDecisionInstanceDto> rawData,
                                                             Integer limit,
                                                             Integer offset,
                                                             Set<String> excludedColumns) {
    final List<String[]> result = new ArrayList<>();

    final List<String> includedDtoFields = extractAllDtoFieldKeys(RawDataDecisionInstanceDto.class);
    includedDtoFields.removeAll(excludedColumns);
    final List<String> includedInputVariableKeys = extractAllDecisionInputKeys(rawData)
      .stream().map(key -> INPUT_PREFIX + key).collect(Collectors.toList());
    includedInputVariableKeys.removeAll(excludedColumns);
    final List<String> includedOutputVariableKeys = extractAllDecisionOutputKeys(rawData)
      .stream().map(key -> OUTPUT_PREFIX + key).collect(Collectors.toList());
    includedOutputVariableKeys.removeAll(excludedColumns);

    final List<String> allIncludedKeys = union(
      includedDtoFields,
      includedInputVariableKeys,
      includedOutputVariableKeys
    );

    // header line
    result.add(allIncludedKeys.toArray(new String[0]));

    int currentPosition = 0;
    for (RawDataDecisionInstanceDto instanceDto : rawData) {
      boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      if ((offset == null && limitNotExceeded) || (isOffsetPassed(offset, currentPosition) && limitNotExceeded)) {
        final String[] dataLine = new String[allIncludedKeys.size()];
        for (int i = 0; i < dataLine.length; i++) {
          final String currentKey = allIncludedKeys.get(i);
          final Optional<String> optionalValue;
          if (includedInputVariableKeys.contains(currentKey)) {
            optionalValue = getInputVariableValue(instanceDto, currentKey);
          } else if (includedOutputVariableKeys.contains(currentKey)) {
            optionalValue = getOutputVariableValue(instanceDto, currentKey);
          } else {
            optionalValue = getDtoFieldValue(instanceDto, RawDataDecisionInstanceDto.class, currentKey);
          }
          dataLine[i] = optionalValue.orElse(null);
        }
        result.add(dataLine);
      }
      currentPosition = currentPosition + 1;
    }

    return result;
  }

  public static List<String[]> map(List<MapResultEntryDto<Long>> values, Integer limit, Integer offset) {
    List<String[]> result = new ArrayList<>();

    int currentPosition = 0;
    for (MapResultEntryDto<Long> value : values) {
      boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      boolean offsetPassed = isOffsetPassed(offset, currentPosition);
      if ((offset == null && limitNotExceeded) || (offsetPassed && limitNotExceeded)) {
        String[] line = new String[2];
        line[0] = value.getKey();
        line[1] = Optional.ofNullable(value.getValue()).map(Object::toString).orElse("");
        result.add(line);
      }
      currentPosition = currentPosition + 1;
    }
    return result;
  }


  public static String mapAggregationType(AggregationType aggregationType) {
    switch (aggregationType) {
      case MEDIAN:
        return "median";
      case AVERAGE:
        return "average";
      case MIN:
        return "minimum";
      case MAX:
        return "maximum";
      default:
        throw new IllegalStateException("Uncovered type: " + aggregationType);
    }
  }

  private static String stripOffPrefix(final String currentKey, final String prefix) {
    return currentKey.replace(prefix, "");
  }

  private static Set<String> stripOffPrefixes(Set<String> excludedColumns, String... prefixes) {
    final String prefixRegex = Arrays.stream(prefixes).map(Pattern::quote).collect(joining("|"));
    return excludedColumns.stream()
      .map(columnName -> columnName.replaceAll(prefixRegex, ""))
      .collect(Collectors.toSet());
  }

  @SafeVarargs
  private static List<String> union(List<String> baseList, List<String>... lists) {
    final List<String> unionList = new ArrayList<>(baseList);
    for (List<String> list : lists) {
      unionList.addAll(list);
    }
    return unionList;
  }

  private static List<String> extractAllDtoFieldKeys(Class<?> dtoClass) {
    final List<String> fieldKeys = new ArrayList<>();
    for (Field f : dtoClass.getDeclaredFields()) {
      if (!Map.class.getName().equals(f.getType().getName())) {
        fieldKeys.add(f.getName());
      }
    }
    return fieldKeys;
  }

  private static List<String> extractAllVariableKeys(List<RawDataProcessInstanceDto> rawData) {
    Set<String> variableKeys = new HashSet<>();
    for (RawDataProcessInstanceDto pi : rawData) {
      if (pi.getVariables() != null) {
        variableKeys.addAll(pi.getVariables().keySet());
      }
    }
    return new ArrayList<>(variableKeys);
  }

  private static List<String> extractAllDecisionInputKeys(List<RawDataDecisionInstanceDto> rawData) {
    Set<String> inputKeys = new HashSet<>();
    for (RawDataDecisionInstanceDto pi : rawData) {
      if (pi.getInputVariables() != null) {
        inputKeys.addAll(pi.getInputVariables().keySet());
      }
    }
    return new ArrayList<>(inputKeys);
  }

  private static List<String> extractAllDecisionOutputKeys(List<RawDataDecisionInstanceDto> rawData) {
    Set<String> outputKeys = new HashSet<>();
    for (RawDataDecisionInstanceDto pi : rawData) {
      if (pi.getOutputVariables() != null) {
        outputKeys.addAll(pi.getOutputVariables().keySet());
      }
    }
    return new ArrayList<>(outputKeys);
  }

  private static <T> Optional<String> getDtoFieldValue(final T instanceDto,
                                                       final Class<T> instanceClass,
                                                       final String fieldKey) {
    try {
      return Optional.of(new PropertyDescriptor(fieldKey, instanceClass))
        .map((descriptor) -> {
          Optional<Object> value = Optional.empty();
          try {
            value = Optional.ofNullable(descriptor.getReadMethod().invoke(instanceDto));
          } catch (Exception e) {
            log.error("can't read value of field", e);
          }
          return value.map(Object::toString).orElse(null);
        });
    } catch (IntrospectionException e) {
      // no field like that
      log.error(
        "Tried to access RawDataInstanceDto field that did not exist {} on class {}",
        fieldKey,
        instanceClass
      );
      return Optional.empty();
    }
  }

  private static Optional<String> getVariableValue(final RawDataProcessInstanceDto instanceDto, String variableKey) {
    return Optional.ofNullable(instanceDto.getVariables())
      .map(variables -> variables.get(variableKey))
      .map(Object::toString);
  }

  private static Optional<String> getOutputVariableValue(final RawDataDecisionInstanceDto instanceDto,
                                                         final String inputKey) {
    return Optional.ofNullable(instanceDto.getOutputVariables())
      .map(outputs -> outputs.get(stripOffPrefix(inputKey, OUTPUT_PREFIX)))
      .map(OutputVariableEntry::getValues)
      .map(values -> values.stream().map(Object::toString).collect(joining(",")));
  }

  private static Optional<String> getInputVariableValue(final RawDataDecisionInstanceDto instanceDto,
                                                        final String outputKey) {
    return Optional.ofNullable(instanceDto.getInputVariables())
      .map(inputs -> inputs.get(stripOffPrefix(outputKey, INPUT_PREFIX)))
      .map(InputVariableEntry::getValue)
      .map(Object::toString);
  }

  private static String[] constructRawProcessHeaderLine(List<String> fieldKeys, List<String> variableKeys) {
    List<String> headerLine = new ArrayList<>(fieldKeys);

    variableKeys.stream().map(key -> VARIABLE_PREFIX + key).forEach(headerLine::add);

    return headerLine.toArray(new String[variableKeys.size()]);
  }

  private static boolean isOffsetPassed(Integer offset, int currentPosition) {
    return offset != null && currentPosition >= offset;
  }

  private static boolean isLimitNotExceeded(Integer limit, List<String[]> result) {
    return limit == null || result.size() <= limit;
  }

}
