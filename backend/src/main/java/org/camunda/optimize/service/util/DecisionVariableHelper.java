package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.report.VariableType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.INPUTS;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.MULTIVALUE_FIELD_DATE;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.MULTIVALUE_FIELD_DOUBLE;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.MULTIVALUE_FIELD_LONG;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.OUTPUTS;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.VARIABLE_CLAUSE_ID;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.VARIABLE_VALUE;

public class DecisionVariableHelper {
  private static final List<VariableType> MULTIVALUE_TYPE_FIELDS = Collections.unmodifiableList(Arrays.asList(
    VariableType.DATE, VariableType.DOUBLE, VariableType.LONG
  ));

  private DecisionVariableHelper() {
  }

  public static String getVariableValueField(final String variablePath) {
    return variablePath + "." + VARIABLE_VALUE;
  }

  public static String getInputVariableValueFieldForType(final VariableType type) {
    return getVariableValueFieldForType(INPUTS, type);
  }

  public static String getOutputVariableValueFieldForType(final VariableType type) {
    return getVariableValueFieldForType(OUTPUTS, type);
  }

  public static List<VariableType> getVariableMultivalueFields() {
    return MULTIVALUE_TYPE_FIELDS;
  }

  public static String getVariableStringValueField(final String variablePath) {
    return getVariableValueFieldForType(variablePath, VariableType.STRING);
  }

  public static String getVariableValueFieldForType(final String variablePath, final VariableType type) {
    switch (Optional.ofNullable(type).orElseThrow(() -> new IllegalArgumentException("No Type provided"))) {
      case BOOLEAN:
      case STRING:
        return getVariableValueField(variablePath);
      case DOUBLE:
        return getVariableValueField(variablePath) + "." + MULTIVALUE_FIELD_DOUBLE;
      case SHORT:
      case INTEGER:
      case LONG:
        return getVariableValueField(variablePath) + "." + MULTIVALUE_FIELD_LONG;
      case DATE:
        return getVariableValueField(variablePath) + "." + MULTIVALUE_FIELD_DATE;
      default:
        throw new IllegalArgumentException("Unhandled type: " + type);
    }
  }

  public static String getInputVariableIdField() {
    return getVariableIdField(INPUTS);
  }

  public static String getOutputVariableIdField() {
    return getVariableIdField(INPUTS);
  }

  public static String getVariableIdField(final String variablePath) {
    return variablePath + "." + VARIABLE_CLAUSE_ID;
  }

}
