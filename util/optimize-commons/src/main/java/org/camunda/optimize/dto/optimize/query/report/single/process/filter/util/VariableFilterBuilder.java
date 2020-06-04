/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DoubleVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.IntegerVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.LongVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.ShortVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class VariableFilterBuilder {
  private ProcessFilterBuilder filterBuilder;
  private VariableType type;
  private List<String> values = new ArrayList<>();
  private String operator;
  private DateFilterDataDto<?> dateFilterDataDto;
  private String name;
  // to be removed with OPT-3719
  @Deprecated
  private boolean filterForUndefined = false;
  // to be removed with OPT-3719
  @Deprecated
  private boolean excludeUndefined = false;

  private VariableFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static VariableFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new VariableFilterBuilder(filterBuilder);
  }

  public VariableFilterBuilder type(VariableType type) {
    this.type = type;
    return this;
  }

  public VariableFilterBuilder booleanType() {
    this.type = VariableType.BOOLEAN;
    return this;
  }

  public VariableFilterBuilder shortType() {
    this.type = VariableType.SHORT;
    return this;
  }

  public VariableFilterBuilder integerType() {
    this.type = VariableType.INTEGER;
    return this;
  }

  public VariableFilterBuilder longType() {
    this.type = VariableType.LONG;
    return this;
  }

  public VariableFilterBuilder doubleType() {
    this.type = VariableType.DOUBLE;
    return this;
  }

  public VariableFilterBuilder stringType() {
    this.type = VariableType.STRING;
    return this;
  }

  public VariableFilterBuilder dateType() {
    this.type = VariableType.DATE;
    return this;
  }


  public VariableFilterBuilder name(String name) {
    this.name = name;
    return this;
  }

  public VariableFilterBuilder booleanValues(final List<Boolean> values) {
    values(
      Optional.ofNullable(values)
        .map(theValues -> theValues.stream()
          .map(aBoolean -> aBoolean != null ? aBoolean.toString() : null)
          .collect(Collectors.toList())
        ).orElse(null)
    );
    return this;
  }

  public VariableFilterBuilder values(List<String> values) {
    if (values == null) {
      this.values = null;
      return this;
    }
    this.values.addAll(values);
    return this;
  }

  public VariableFilterBuilder filterForUndefined() {
    this.filterForUndefined = true;
    return this;
  }

  public VariableFilterBuilder excludeUndefined() {
    this.excludeUndefined = true;
    return this;
  }

  public VariableFilterBuilder booleanTrue() {
    this.type = VariableType.BOOLEAN;
    this.values.add("true");
    return this;
  }

  public VariableFilterBuilder booleanFalse() {
    this.type = VariableType.BOOLEAN;
    this.values.add("false");
    return this;
  }

  public VariableFilterBuilder operator(String operator) {
    this.operator = operator;
    return this;
  }

  public VariableFilterBuilder dateFilter(final DateFilterDataDto dateFilterDataDto) {
    this.dateFilterDataDto = dateFilterDataDto;
    return this;
  }

  public VariableFilterBuilder rollingDate(final Long value, final DateFilterUnit unit) {
    this.dateFilterDataDto = new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit));
    return this;
  }

  public VariableFilterBuilder relativeDate(final Long value, final DateFilterUnit unit) {
    this.dateFilterDataDto = new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(value, unit));
    return this;
  }

  public VariableFilterBuilder fixedDate(final OffsetDateTime start, final OffsetDateTime end) {
    this.dateFilterDataDto = new FixedDateFilterDataDto(start, end);
    return this;
  }

  public ProcessFilterBuilder add() {
    switch (type) {
      case BOOLEAN:
        return createBooleanVariableFilter();
      case DATE:
        return createVariableDateFilter();
      case LONG:
      case SHORT:
      case DOUBLE:
      case STRING:
      case INTEGER:
        return createOperatorMultipleValuesFilter();
      default:
        return filterBuilder;
    }
  }

  public ProcessFilterBuilder createBooleanVariableFilter() {
    final BooleanVariableFilterDataDto dataDto = new BooleanVariableFilterDataDto(
      name,
      Optional.ofNullable(values)
        .map(theValues -> theValues.stream()
          .map(value -> value != null ? Boolean.valueOf(value) : null)
          .collect(Collectors.toList()))
        .orElse(null)
    );
    dataDto.setFilterForUndefined(filterForUndefined);
    VariableFilterDto filter = new VariableFilterDto();
    filter.setData(dataDto);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

  public ProcessFilterBuilder createVariableDateFilter() {
    DateVariableFilterDataDto dateVariableFilterDataDto = new DateVariableFilterDataDto(
      name,
      dateFilterDataDto != null ? dateFilterDataDto : new FixedDateFilterDataDto(null, null)
    );
    dateVariableFilterDataDto.setFilterForUndefined(filterForUndefined);
    VariableFilterDto filter = new VariableFilterDto();
    filter.setData(dateVariableFilterDataDto);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

  public ProcessFilterBuilder createOperatorMultipleValuesFilter() {
    VariableFilterDto filter = new VariableFilterDto();
    switch (type) {
      case INTEGER:
        filter.setData(new IntegerVariableFilterDataDto(name, operator, values));
        break;
      case STRING:
        filter.setData(new StringVariableFilterDataDto(name, operator, values));
        break;
      case DOUBLE:
        filter.setData(new DoubleVariableFilterDataDto(name, operator, values));
        break;
      case SHORT:
        filter.setData(new ShortVariableFilterDataDto(name, operator, values));
        break;
      case LONG:
        filter.setData(new LongVariableFilterDataDto(name, operator, values));
        break;
      default:
        break;
    }
    filter.getData().setFilterForUndefined(filterForUndefined);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }
}
