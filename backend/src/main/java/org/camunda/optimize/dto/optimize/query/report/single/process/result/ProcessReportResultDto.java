package org.camunda.optimize.dto.optimize.query.report.single.process.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import static org.camunda.optimize.dto.optimize.ReportConstants.DURATION_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DURATION_NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.FREQUENCY_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.FREQUENCY_NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "resultType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ProcessReportMapResultDto.class, name = FREQUENCY_MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = ProcessDurationReportMapResultDto.class, name = DURATION_MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = ProcessReportNumberResultDto.class, name = FREQUENCY_NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = ProcessDurationReportNumberResultDto.class, name = DURATION_NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = RawDataProcessReportResultDto.class, name = RAW_RESULT_TYPE),
})
public abstract class ProcessReportResultDto extends SingleProcessReportDefinitionDto implements ReportResultDto {

  protected long processInstanceCount;

  public long getProcessInstanceCount() {
    return processInstanceCount;
  }

  public void setProcessInstanceCount(long processInstanceCount) {
    this.processInstanceCount = processInstanceCount;
  }

  public abstract ResultType getResultType();

}
