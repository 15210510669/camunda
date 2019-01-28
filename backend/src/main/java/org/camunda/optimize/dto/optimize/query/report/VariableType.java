package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.util.ProcessVariableHelper.BOOLEAN_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.DATE_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.DOUBLE_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.INTEGER_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.LONG_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.SHORT_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.STRING_TYPE;

public enum VariableType {
  STRING(STRING_TYPE),
  SHORT(SHORT_TYPE),
  LONG(LONG_TYPE),
  DOUBLE(DOUBLE_TYPE),
  INTEGER(INTEGER_TYPE),
  BOOLEAN(BOOLEAN_TYPE),
  DATE(DATE_TYPE),
  ;

  private static final Set<VariableType> NUMERIC_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    INTEGER, SHORT, LONG, DOUBLE
  )));
  private static final Map<String, VariableType> BY_LOWER_CASE_ID_MAP = Stream.of(VariableType.values())
    .collect(toMap(type -> type.getId().toLowerCase(), type -> type));

  private final String id;

  VariableType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  public static VariableType getTypeForId(String id) {
    return BY_LOWER_CASE_ID_MAP.get(id.toLowerCase());
  }

  public static Set<VariableType> getNumericTypes() {
    return NUMERIC_TYPES;
  }
}