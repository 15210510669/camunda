package org.camunda.optimize.dto.optimize.query.report.configuration.target_value;

import java.util.Objects;

public class BaseLineDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private Double value = 0.0;

  public TargetValueUnit getUnit() {
    return unit;
  }

  public void setUnit(TargetValueUnit unit) {
    this.unit = unit;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BaseLineDto)) {
      return false;
    }
    BaseLineDto that = (BaseLineDto) o;
    return unit == that.unit &&
      Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(unit, value);
  }
}
