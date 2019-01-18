package org.camunda.optimize.dto.optimize.query.report.configuration;

import org.camunda.optimize.dto.optimize.query.report.configuration.heatmap_target_value.HeatmapTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.configuration.target_value.TargetValueDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ReportConfigurationDto {

  public static final String DEFAULT_CONFIGURATION_COLOR = "#1991c8";

  private String color = DEFAULT_CONFIGURATION_COLOR;
  private List<String> reportColors = Collections.singletonList(DEFAULT_CONFIGURATION_COLOR);
  private Boolean showInstanceCount = false;
  private Boolean pointMarkers = true;
  private Integer precision = null;
  private Boolean hideRelativeValue = false;
  private Boolean hideAbsoluteValue = false;
  private String yLabel = "";
  private String xLabel = "";
  private Boolean alwaysShowRelative = false;
  private Boolean alwaysShowAbsolute = false;
  private String xml = null;
  private List<String> excludedColumns = new ArrayList<>();
  private ColumnOrderDto columnOrder = new ColumnOrderDto();
  private TargetValueDto targetValue = new TargetValueDto();
  private HeatmapTargetValueDto heatmapTargetValue = new HeatmapTargetValueDto();

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public List<String> getReportColors() {
    return reportColors;
  }

  public void setReportColors(List<String> reportColors) {
    this.reportColors = reportColors;
  }

  public Boolean getShowInstanceCount() {
    return showInstanceCount;
  }

  public void setShowInstanceCount(Boolean showInstanceCount) {
    this.showInstanceCount = showInstanceCount;
  }

  public Boolean getPointMarkers() {
    return pointMarkers;
  }

  public void setPointMarkers(Boolean pointMarkers) {
    this.pointMarkers = pointMarkers;
  }

  public Integer getPrecision() {
    return precision;
  }

  public void setPrecision(Integer precision) {
    this.precision = precision;
  }

  public Boolean getHideRelativeValue() {
    return hideRelativeValue;
  }

  public void setHideRelativeValue(Boolean hideRelativeValue) {
    this.hideRelativeValue = hideRelativeValue;
  }

  public Boolean getHideAbsoluteValue() {
    return hideAbsoluteValue;
  }

  public void setHideAbsoluteValue(Boolean hideAbsoluteValue) {
    this.hideAbsoluteValue = hideAbsoluteValue;
  }

  public String getyLabel() {
    return yLabel;
  }

  public void setyLabel(String yLabel) {
    this.yLabel = yLabel;
  }

  public String getxLabel() {
    return xLabel;
  }

  public void setxLabel(String xLabel) {
    this.xLabel = xLabel;
  }

  public Boolean getAlwaysShowRelative() {
    return alwaysShowRelative;
  }

  public void setAlwaysShowRelative(Boolean alwaysShowRelative) {
    this.alwaysShowRelative = alwaysShowRelative;
  }

  public Boolean getAlwaysShowAbsolute() {
    return alwaysShowAbsolute;
  }

  public void setAlwaysShowAbsolute(Boolean alwaysShowAbsolute) {
    this.alwaysShowAbsolute = alwaysShowAbsolute;
  }

  public String getXml() {
    return xml;
  }

  public void setXml(String xml) {
    this.xml = xml;
  }

  public List<String> getExcludedColumns() {
    return excludedColumns;
  }

  public void setExcludedColumns(List<String> excludedColumns) {
    this.excludedColumns = excludedColumns;
  }

  public ColumnOrderDto getColumnOrder() {
    return columnOrder;
  }

  public void setColumnOrder(ColumnOrderDto columnOrder) {
    this.columnOrder = columnOrder;
  }

  public TargetValueDto getTargetValue() {
    return targetValue;
  }

  public void setTargetValue(TargetValueDto targetValue) {
    this.targetValue = targetValue;
  }

  public HeatmapTargetValueDto getHeatmapTargetValue() {
    return heatmapTargetValue;
  }

  public void setHeatmapTargetValue(HeatmapTargetValueDto heatmapTargetValue) {
    this.heatmapTargetValue = heatmapTargetValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ReportConfigurationDto)) {
      return false;
    }
    ReportConfigurationDto that = (ReportConfigurationDto) o;
    return Objects.equals(color, that.color) &&
      Objects.equals(reportColors, that.reportColors) &&
      Objects.equals(showInstanceCount, that.showInstanceCount) &&
      Objects.equals(pointMarkers, that.pointMarkers) &&
      Objects.equals(precision, that.precision) &&
      Objects.equals(hideRelativeValue, that.hideRelativeValue) &&
      Objects.equals(hideAbsoluteValue, that.hideAbsoluteValue) &&
      Objects.equals(yLabel, that.yLabel) &&
      Objects.equals(xLabel, that.xLabel) &&
      Objects.equals(alwaysShowRelative, that.alwaysShowRelative) &&
      Objects.equals(alwaysShowAbsolute, that.alwaysShowAbsolute) &&
      Objects.equals(xml, that.xml) &&
      Objects.equals(excludedColumns, that.excludedColumns) &&
      Objects.equals(columnOrder, that.columnOrder) &&
      Objects.equals(targetValue, that.targetValue) &&
      Objects.equals(heatmapTargetValue, that.heatmapTargetValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      color,
      reportColors,
      showInstanceCount,
      pointMarkers,
      precision,
      hideRelativeValue,
      hideAbsoluteValue,
      yLabel,
      xLabel,
      alwaysShowRelative,
      alwaysShowAbsolute,
      xml,
      excludedColumns,
      columnOrder,
      targetValue,
      heatmapTargetValue
    );
  }
}
