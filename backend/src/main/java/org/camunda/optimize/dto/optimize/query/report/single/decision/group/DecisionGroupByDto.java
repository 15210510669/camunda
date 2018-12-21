package org.camunda.optimize.dto.optimize.query.report.single.decision.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByValueDto;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;

import java.util.Objects;

import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_EVALUATION_DATE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_INPUT_VARIABLE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_OUTPUT_VARIABLE_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DecisionGroupByNoneDto.class, name = GROUP_BY_NONE_TYPE),
  @JsonSubTypes.Type(value = DecisionGroupByEvaluationDateTimeDto.class, name = GROUP_BY_EVALUATION_DATE_TYPE),
  @JsonSubTypes.Type(value = DecisionGroupByInputVariableDto.class, name = GROUP_BY_INPUT_VARIABLE_TYPE),
  @JsonSubTypes.Type(value = DecisionGroupByOutputVariableDto.class, name = GROUP_BY_OUTPUT_VARIABLE_TYPE)
}
)
public abstract class DecisionGroupByDto<VALUE extends DecisionGroupByValueDto> implements Combinable {

  @JsonProperty
  protected DecisionGroupByType type;
  protected VALUE value;

  public VALUE getValue() {
    return value;
  }

  public void setValue(VALUE value) {
    this.value = value;
  }

  public DecisionGroupByType getType() {
    return type;
  }

  public void setType(DecisionGroupByType type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return type.getId();
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionGroupByDto)) {
      return false;
    }
    DecisionGroupByDto<?> that = (DecisionGroupByDto<?>) o;
    return Objects.equals(type, that.type) && ReportUtil.isCombinable(value, that.value);
  }

  @JsonIgnore
  public String createCommandKey() {
    return type.getId();
  }


}
