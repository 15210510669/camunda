package org.camunda.optimize.dto.optimize.query.report;

import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.group.GroupByDto;

import java.util.ArrayList;
import java.util.List;

public class ReportDataDto {

  protected String processDefinitionKey;
  protected String processDefinitionVersion;
  protected List<FilterDto> filter = new ArrayList<>();
  protected ViewDto view;
  protected GroupByDto groupBy;
  protected String visualization;
  protected Object configuration;

  public List<FilterDto> getFilter() {
    return filter;
  }

  public void setFilter(List<FilterDto> filter) {
    this.filter = filter;
  }

  public ViewDto getView() {
    return view;
  }

  public void setView(ViewDto view) {
    this.view = view;
  }

  public GroupByDto getGroupBy() {
    return groupBy;
  }

  public void setGroupBy(GroupByDto groupBy) {
    this.groupBy = groupBy;
  }

  public String getVisualization() {
    return visualization;
  }

  public void setVisualization(String visualization) {
    this.visualization = visualization;
  }

  public Object getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Object configuration) {
    this.configuration = configuration;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }
}
