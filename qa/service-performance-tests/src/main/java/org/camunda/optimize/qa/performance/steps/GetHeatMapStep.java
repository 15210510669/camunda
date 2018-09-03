package org.camunda.optimize.qa.performance.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapSingleReportResultDto;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStep;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.util.PerfTestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

abstract class GetHeatMapStep extends PerfTestStep {

  private Logger logger = LoggerFactory.getLogger(this.getClass());

  private List<FilterDto> filter;

  GetHeatMapStep(List<FilterDto> filter) {
    this.filter = filter;
  }

  @Override
  public PerfTestStepResult<MapSingleReportResultDto> execute(PerfTestContext context) {
    objectMapper = initMapper(context);
    CloseableHttpClient client = HttpClientBuilder.create().build();
    try {
      return getHeatmap(context, client);
    } catch (IOException | RuntimeException e) {
      throw new PerfTestException("Something went wrong while trying to fetch the heatmap!", e);
    } finally {
      try {
        client.close();
      } catch (IOException e) {
        logger.error("Could not close http client!", e);
      }
    }
  }

  private PerfTestStepResult<MapSingleReportResultDto> getHeatmap(PerfTestContext context, CloseableHttpClient client) throws IOException {

    HttpPost post = setupPostRequest(context);

    CloseableHttpResponse response = client.execute(post);
    if(response.getStatusLine().getStatusCode() != 200 ) {
      logger.error(response.getStatusLine().toString());
      throw new PerfTestException("Post request was not successful!");
    }
    String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

    PerfTestStepResult<MapSingleReportResultDto> result = new PerfTestStepResult<>();
    MapSingleReportResultDto responseDto = objectMapper.readValue(responseString, MapSingleReportResultDto.class);
    result.setResult(responseDto);
    this.getClass().getSimpleName();

    return result;
  }

  private HttpPost setupPostRequest(PerfTestContext context) throws UnsupportedEncodingException, JsonProcessingException {
    HttpPost post = new HttpPost(getRestEndpoint(context));
    post.addHeader("content-type", "application/json");
    post.addHeader("Authorization", "Bearer " + context.getConfiguration().getAuthorizationToken());

    String processDefinitionKey = (String) context.getParameter("processDefinitionKey");
    String processDefinitionVersion = (String) context.getParameter("processDefinitionVersion");
    SingleReportDataDto reportData = createRequest(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(filter);

    post.setEntity(new StringEntity(objectMapper.writeValueAsString(reportData)));
    return post;
  }

  protected abstract SingleReportDataDto createRequest(String processDefinitionKey, String processDefinitionVersion);

  abstract String getRestEndpoint(PerfTestContext context);
}
