package org.camunda.optimize.dto.optimize.query.report.single.process.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FilterDataDto;

/**
 * Abstract class that contains a hidden "type" field to distinguish, which
 * filter type the jackson object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StartDateFilterDto.class, name = "startDate"),
    @JsonSubTypes.Type(value = EndDateFilterDto.class, name = "endDate"),
    @JsonSubTypes.Type(value = DurationFilterDto.class, name = "processInstanceDuration"),
    @JsonSubTypes.Type(value = VariableFilterDto.class, name = "variable"),
    @JsonSubTypes.Type(value = ExecutedFlowNodeFilterDto.class, name = "executedFlowNodes"),
    @JsonSubTypes.Type(value = RunningInstancesOnlyFilterDto.class, name = "runningInstancesOnly"),
    @JsonSubTypes.Type(value = CompletedInstancesOnlyFilterDto.class, name = "completedInstancesOnly"),
    @JsonSubTypes.Type(value = CanceledInstancesOnlyFilterDto.class, name = "canceledInstancesOnly"),
}
)
public abstract class ProcessFilterDto<DATA extends FilterDataDto> {
  protected DATA data;

  public DATA getData() {
    return data;
  }

  public void setData(DATA data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "Filter=" + getClass().getSimpleName();
  }
}
