/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.SettingsDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionTenantsRequest;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.GetVariableNamesForReportsRequestDto;
import org.camunda.optimize.dto.optimize.rest.OnboardingStateRestDto;
import org.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import org.camunda.optimize.dto.optimize.rest.sorting.EventCountSorter;
import org.camunda.optimize.dto.optimize.rest.sorting.Sorter;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.glassfish.jersey.client.ClientProperties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.IdentityRestService.CURRENT_USER_IDENTITY_SUB_PATH;
import static org.camunda.optimize.rest.IdentityRestService.IDENTITY_RESOURCE_PATH;
import static org.camunda.optimize.rest.IdentityRestService.IDENTITY_SEARCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH;
import static org.camunda.optimize.rest.IngestionRestService.EVENT_BATCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;

@Slf4j
public class OptimizeRequestExecutor {
  private static final int MAX_LOGGED_BODY_SIZE = 10_000;
  private static final String ALERT = "alert";

  @Getter
  private final WebTarget defaultWebTarget;
  private final String defaultUser;
  private final String defaultUserPassword;
  private final ObjectMapper objectMapper;
  private final Map<String, String> cookies = new HashMap<>();
  private final Map<String, String> requestHeaders = new HashMap<>();

  private String defaultAuthCookie;
  private String authCookie;
  private String path;
  private String method;
  private Entity<?> body;
  private String mediaType = MediaType.APPLICATION_JSON;
  private Map<String, Object> queryParams;

  public OptimizeRequestExecutor(final String defaultUser,
                                 final String defaultUserPassword,
                                 final String restEndpoint) {
    this.defaultUser = defaultUser;
    this.defaultUserPassword = defaultUserPassword;
    this.objectMapper = getDefaultObjectMapper();
    this.defaultWebTarget = createWebTarget(restEndpoint);
  }

  public OptimizeRequestExecutor initAuthCookie() {
    this.defaultAuthCookie = authenticateUserRequest(defaultUser, defaultUserPassword);
    this.authCookie = defaultAuthCookie;
    return this;
  }

  public OptimizeRequestExecutor addQueryParams(Map<String, Object> queryParams) {
    if (this.queryParams != null && queryParams.size() != 0) {
      this.queryParams.putAll(queryParams);
    } else {
      this.queryParams = queryParams;
    }
    return this;
  }

  public OptimizeRequestExecutor addSingleQueryParam(String key, Object value) {
    if (this.queryParams != null && queryParams.size() != 0) {
      this.queryParams.put(key, value);
    } else {
      HashMap<String, Object> params = new HashMap<>();
      params.put(key, value);
      this.queryParams = params;
    }
    return this;
  }

  public OptimizeRequestExecutor addSingleCookie(String key, String value) {
    cookies.put(key, value);
    return this;
  }

  public OptimizeRequestExecutor addSingleHeader(String key, String value) {
    requestHeaders.put(key, value);
    return this;
  }

  public OptimizeRequestExecutor withUserAuthentication(String username, String password) {
    this.authCookie = authenticateUserRequest(username, password);
    return this;
  }

  public OptimizeRequestExecutor withoutAuthentication() {
    this.authCookie = null;
    return this;
  }

  public OptimizeRequestExecutor withGivenAuthToken(String authToken) {
    this.authCookie = AuthCookieService.createOptimizeAuthCookieValue(authToken);
    return this;
  }

  public Response execute() {
    Invocation.Builder builder = prepareRequest();

    final Response response;
    switch (this.method) {
      case GET:
        response = builder.get();
        break;
      case POST:
        response = builder.post(body);
        break;
      case PUT:
        response = builder.put(body);
        break;
      case DELETE:
        response = builder.delete();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported http method: " + this.method);
    }

    resetBuilder();
    // consume the response entity so the server can write the response
    response.bufferEntity();
    return response;
  }

  private Invocation.Builder prepareRequest() {
    WebTarget webTarget = defaultWebTarget.path(this.path);

    if (queryParams != null && queryParams.size() != 0) {
      for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
        if (queryParam.getValue() instanceof List) {
          for (Object p : ((List) queryParam.getValue())) {
            if (p == null) {
              webTarget = webTarget.queryParam(queryParam.getKey(), "null");
            } else {
              webTarget = webTarget.queryParam(queryParam.getKey(), p);
            }
          }
        } else {
          webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
        }
      }
    }

    Invocation.Builder builder = webTarget.request();

    for (Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
      builder = builder.cookie(cookieEntry.getKey(), cookieEntry.getValue());
    }

    if (defaultAuthCookie == null) {
      initAuthCookie();
    }
    if (authCookie != null) {
      builder = builder.cookie(OPTIMIZE_AUTHORIZATION, this.authCookie);
    }

    for (Map.Entry<String, String> headerEntry : requestHeaders.entrySet()) {
      builder = builder.header(headerEntry.getKey(), headerEntry.getValue());
    }
    return builder;
  }

  public Response execute(int expectedResponseCode) {
    final Response response = execute();
    assertThat(response.getStatus()).isEqualTo(expectedResponseCode);
    return response;
  }

  public <T> T execute(TypeReference<T> classToExtractFromResponse) {
    try (final Response response = execute()) {
      assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
      String jsonString = response.readEntity(String.class);
      return objectMapper.readValue(jsonString, classToExtractFromResponse);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  public <T> T execute(Class<T> classToExtractFromResponse, int responseCode) {
    try (final Response response = execute()) {
      assertThat(response.getStatus()).isEqualTo(responseCode);
      return response.readEntity(classToExtractFromResponse);
    }
  }

  public <T> List<T> executeAndReturnList(Class<T> classToExtractFromResponse, int responseCode) {
    try (final Response response = execute()) {
      assertThat(response.getStatus()).isEqualTo(responseCode);
      String jsonString = response.readEntity(String.class);
      TypeFactory factory = objectMapper.getTypeFactory();
      JavaType listOfT = factory.constructCollectionType(List.class, classToExtractFromResponse);
      return objectMapper.readValue(jsonString, listOfT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  private void resetBuilder() {
    this.authCookie = defaultAuthCookie;
    this.body = null;
    this.path = null;
    this.method = null;
    this.queryParams = null;
    this.mediaType = MediaType.APPLICATION_JSON;
    this.cookies.clear();
    this.requestHeaders.clear();
  }

  public OptimizeRequestExecutor buildGenericRequest(final String method, final String path, final Object payload) {
    this.path = path;
    this.method = method;
    this.body = getBody(payload);
    return this;
  }

  public OptimizeRequestExecutor buildCreateAlertRequest(AlertCreationDto alert) {
    this.body = getBody(alert);
    this.path = ALERT;
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateAlertRequest(String id, AlertCreationDto alert) {
    this.body = getBody(alert);
    this.path = ALERT + "/" + id;
    this.method = PUT;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteAlertRequest(String id) {
    this.path = ALERT + "/" + id;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateSingleReportRequest(String id,
                                                                ReportDefinitionDto entity) {
    switch (entity.getReportType()) {
      default:
      case PROCESS:
        return buildUpdateSingleProcessReportRequest(id, entity, null);
      case DECISION:
        return buildUpdateSingleDecisionReportRequest(id, entity, null);
    }
  }

  public OptimizeRequestExecutor buildUpdateSingleProcessReportRequest(String id,
                                                                       ReportDefinitionDto entity) {
    return buildUpdateSingleProcessReportRequest(id, entity, null);
  }

  public OptimizeRequestExecutor buildUpdateSingleProcessReportRequest(String id,
                                                                       ReportDefinitionDto entity,
                                                                       Boolean force) {
    this.path = "report/process/single/" + id;
    this.body = getBody(entity);
    this.method = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateSingleDecisionReportRequest(String id,
                                                                        ReportDefinitionDto entity,
                                                                        Boolean force) {
    this.path = "report/decision/single/" + id;
    this.body = getBody(entity);
    this.method = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildUpdateCombinedProcessReportRequest(String id,
                                                                         ReportDefinitionDto entity) {
    return buildUpdateCombinedProcessReportRequest(id, entity, null);
  }

  public OptimizeRequestExecutor buildUpdateCombinedProcessReportRequest(String id,
                                                                         ReportDefinitionDto entity,
                                                                         Boolean force) {
    this.path = "report/process/combined/" + id;
    this.body = getBody(entity);
    this.method = PUT;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildCreateSingleProcessReportRequest() {
    return buildCreateSingleProcessReportRequest(null);
  }

  public OptimizeRequestExecutor buildCreateSingleProcessReportRequest(final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    this.path = "report/process/single";
    Optional.ofNullable(singleProcessReportDefinitionDto)
      .ifPresent(definitionDto -> this.body = getBody(definitionDto));
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCreateSingleDecisionReportRequest(final SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto) {
    this.path = "report/decision/single";
    Optional.ofNullable(singleDecisionReportDefinitionDto)
      .ifPresent(definitionDto -> this.body = getBody(definitionDto));
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCreateCombinedReportRequest() {
    return buildCreateCombinedReportRequest(null);
  }

  public OptimizeRequestExecutor buildCreateCombinedReportRequest(final CombinedReportDefinitionDto combinedReportDefinitionDto) {
    this.path = "report/process/combined";
    Optional.ofNullable(combinedReportDefinitionDto).ifPresent(definitionDto -> this.body = getBody(definitionDto));
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportRequest(String id) {
    this.path = "report/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportDeleteConflictsRequest(String id) {
    this.path = "report/" + id + "/delete-conflicts";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportRequest(String id, Boolean force) {
    this.path = "report/" + id;
    this.method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportRequest(String id) {
    return buildDeleteReportRequest(id, null);
  }

  public OptimizeRequestExecutor buildGetAllPrivateReportsRequest() {
    this.method = GET;
    this.path = "/report";
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(String reportId) {
    return buildEvaluateSavedReportRequest(reportId, null, null);
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(String reportId,
                                                                 PaginationRequestDto paginationRequestDto) {
    return buildEvaluateSavedReportRequest(reportId, null, paginationRequestDto);
  }

  public OptimizeRequestExecutor buildEvaluateSavedReportRequest(String reportId,
                                                                 AdditionalProcessReportEvaluationFilterDto filters) {
    return buildEvaluateSavedReportRequest(reportId, filters, null);
  }

  private OptimizeRequestExecutor buildEvaluateSavedReportRequest(String reportId,
                                                                  AdditionalProcessReportEvaluationFilterDto filters,
                                                                  PaginationRequestDto paginationRequestDto) {
    this.path = "/report/" + reportId + "/evaluate";
    this.method = POST;
    Optional.ofNullable(filters).ifPresent(filterDto -> this.body = getBody(filterDto));
    Optional.ofNullable(paginationRequestDto).ifPresent(pagination -> addQueryParams(extractPagination(pagination)));
    return this;
  }

  public <T extends SingleReportDataDto> OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequestWithPagination(
    T entity,
    PaginationRequestDto paginationDto) {
    buildEvaluateSingleUnsavedReportRequest(entity);
    addQueryParams(extractPagination(paginationDto));
    return this;
  }

  public <T extends SingleReportDataDto> OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequest(T entity) {
    this.path = "report/evaluate";
    if (entity instanceof ProcessReportDataDto) {
      ProcessReportDataDto dataDto = (ProcessReportDataDto) entity;
      SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
      definitionDto.setData(dataDto);
      this.body = getBody(definitionDto);
    } else if (entity instanceof DecisionReportDataDto) {
      DecisionReportDataDto dataDto = (DecisionReportDataDto) entity;
      SingleDecisionReportDefinitionDto definitionDto = new SingleDecisionReportDefinitionDto();
      definitionDto.setData(dataDto);
      this.body = getBody(definitionDto);
    } else if (entity == null) {
      this.body = getBody(null);
    } else {
      throw new OptimizeIntegrationTestException("Unknown report data type!");
    }
    this.method = POST;
    return this;
  }

  public <T extends SingleReportDefinitionDto> OptimizeRequestExecutor buildEvaluateSingleUnsavedReportRequest(T definitionDto) {
    this.path = "report/evaluate";
    if (definitionDto instanceof SingleProcessReportDefinitionDto) {
      this.body = getBody(definitionDto);
    } else if (definitionDto instanceof SingleDecisionReportDefinitionDto) {
      this.body = getBody(definitionDto);
    } else if (definitionDto == null) {
      this.body = getBody(null);
    } else {
      throw new OptimizeIntegrationTestException("Unknown report definition type!");
    }
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateCombinedUnsavedReportRequest(CombinedReportDataDto combinedReportData) {
    this.path = "report/evaluate";
    this.method = POST;
    this.body = getBody(new CombinedReportDefinitionDto(combinedReportData));
    return this;
  }

  public OptimizeRequestExecutor buildCreateDashboardRequest() {
    return buildCreateDashboardRequest(new DashboardDefinitionDto());
  }

  public OptimizeRequestExecutor buildCreateDashboardRequest(DashboardDefinitionDto dashboardDefinitionDto) {
    this.method = POST;
    this.body = Optional.ofNullable(dashboardDefinitionDto)
      .map(definitionDto -> getBody(dashboardDefinitionDto))
      .orElseGet(() -> Entity.json(""));
    this.path = "dashboard";
    return this;
  }

  public OptimizeRequestExecutor buildCreateCollectionRequest() {
    return buildCreateCollectionRequestWithPartialDefinition(null);
  }

  public OptimizeRequestExecutor buildCreateCollectionRequestWithPartialDefinition(PartialCollectionDefinitionDto partialCollectionDefinitionDto) {
    this.method = POST;
    this.body = Optional.ofNullable(partialCollectionDefinitionDto)
      .map(definitionDto -> getBody(partialCollectionDefinitionDto))
      .orElseGet(() -> Entity.json(""));
    this.path = "collection";
    return this;
  }

  public OptimizeRequestExecutor buildUpdateDashboardRequest(String id, DashboardDefinitionDto entity) {
    this.path = "dashboard/" + id;
    this.method = PUT;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildUpdatePartialCollectionRequest(String id,
                                                                     PartialCollectionDefinitionDto updateDto) {
    this.path = "collection/" + id;
    this.method = PUT;
    this.body = getBody(updateDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetRolesToCollectionRequest(final String id) {
    this.path = "collection/" + id + "/role/";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAddRoleToCollectionRequest(final String collectionId,
                                                                 final CollectionRoleDto roleDto) {
    this.path = "collection/" + collectionId + "/role/";
    this.method = POST;
    this.body = getBody(roleDto);
    return this;
  }

  public OptimizeRequestExecutor buildUpdateRoleToCollectionRequest(final String id,
                                                                    final String roleEntryId,
                                                                    final CollectionRoleUpdateDto updateDto) {
    this.path = "collection/" + id + "/role/" + roleEntryId;
    this.method = PUT;
    this.body = getBody(updateDto);
    return this;
  }


  public OptimizeRequestExecutor buildDeleteRoleToCollectionRequest(final String id,
                                                                    final String roleEntryId) {
    this.path = "collection/" + id + "/role/" + roleEntryId;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildGetReportsForCollectionRequest(String id) {
    this.path = "collection/" + id + "/reports/";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDashboardRequest(String id) {
    this.path = "dashboard/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionRequest(String id) {
    this.path = "collection/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionEntitiesRequest(String id) {
    return buildGetCollectionEntitiesRequest(id, null);
  }

  public OptimizeRequestExecutor buildGetCollectionEntitiesRequest(String id, EntitySorter sorter) {
    this.path = "collection/" + id + "/entities";
    this.method = GET;
    Optional.ofNullable(sorter).ifPresent(sortParams -> addQueryParams(extractSortParams(sorter)));
    return this;
  }

  public OptimizeRequestExecutor buildGetAlertsForCollectionRequest(String id) {
    this.path = "collection/" + id + "/alerts/";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetCollectionDeleteConflictsRequest(String id) {
    this.path = "collection/" + id + "/delete-conflicts";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetAllEntitiesRequest() {
    return buildGetAllEntitiesRequest(null);
  }

  public OptimizeRequestExecutor buildGetAllEntitiesRequest(EntitySorter sorter) {
    this.path = "entities/";
    this.method = GET;
    Optional.ofNullable(sorter).ifPresent(sortParams -> addQueryParams(extractSortParams(sorter)));
    return this;
  }

  public OptimizeRequestExecutor buildGetEntityNamesRequest(EntityNameRequestDto requestDto) {
    this.path = "entities/names";
    this.method = GET;
    this.addSingleQueryParam(EntityNameRequestDto.Fields.collectionId.name(), requestDto.getCollectionId());
    this.addSingleQueryParam(EntityNameRequestDto.Fields.dashboardId.name(), requestDto.getDashboardId());
    this.addSingleQueryParam(EntityNameRequestDto.Fields.reportId.name(), requestDto.getReportId());
    this.addSingleQueryParam(
      EntityNameRequestDto.Fields.eventBasedProcessId.name(),
      requestDto.getEventBasedProcessId()
    );
    return this;
  }

  public OptimizeRequestExecutor buildDeleteDashboardRequest(String id) {
    return buildDeleteDashboardRequest(id, false);
  }

  public OptimizeRequestExecutor buildDeleteDashboardRequest(String id, Boolean force) {
    this.path = "dashboard/" + id;
    this.method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildDeleteCollectionRequest(String id) {
    return buildDeleteCollectionRequest(id, false);
  }

  public OptimizeRequestExecutor buildDeleteCollectionRequest(String id, Boolean force) {
    this.path = "collection/" + id;
    this.method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildFindShareForReportRequest(String id) {
    this.path = "share/report/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildFindShareForDashboardRequest(String id) {
    this.path = "share/dashboard/" + id;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildShareDashboardRequest(DashboardShareDto share) {
    this.path = "share/dashboard";
    this.body = getBody(share);
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildShareReportRequest(ReportShareDto share) {
    this.path = "share/report";
    this.body = getBody(share);
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedReportRequest(String shareId) {
    return buildEvaluateSharedReportRequest(shareId, null);
  }

  public OptimizeRequestExecutor buildEvaluateSharedReportRequest(String shareId,
                                                                  PaginationRequestDto paginationRequestDto) {
    this.path = "share/report/" + shareId + "/evaluate";
    this.method = GET;
    Optional.ofNullable(paginationRequestDto).ifPresent(pagination -> addQueryParams(extractPagination(pagination)));
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardReportRequest(String dashboardShareId, String reportId) {
    return buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId, null, null);
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardReportRequest(String dashboardShareId,
                                                                           String reportId,
                                                                           PaginationRequestDto paginationRequestDto,
                                                                           AdditionalProcessReportEvaluationFilterDto filterDto) {
    this.path = "share/dashboard/" + dashboardShareId + "/report/" + reportId + "/evaluate";
    this.method = POST;
    Optional.ofNullable(paginationRequestDto).ifPresent(pagination -> addQueryParams(extractPagination(pagination)));
    Optional.ofNullable(filterDto).ifPresent(filters -> this.body = getBody(filters));
    return this;
  }

  public OptimizeRequestExecutor buildEvaluateSharedDashboardRequest(String shareId) {
    this.path = "share/dashboard/" + shareId + "/evaluate";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCheckSharingStatusRequest(ShareSearchDto shareSearchDto) {
    this.path = "share/status";
    this.method = POST;
    this.body = getBody(shareSearchDto);
    return this;
  }

  public OptimizeRequestExecutor buildCheckImportStatusRequest() {
    this.path = "/status";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetReadinessRequest() {
    this.path = "/readyz";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetUIConfigurationRequest() {
    this.path = "/ui-configuration";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteReportShareRequest(String id) {
    this.path = "share/report/" + id;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteDashboardShareRequest(String id) {
    this.path = "share/dashboard/" + id;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildDashboardShareAuthorizationCheck(String id) {
    this.path = "share/dashboard/" + id + "/isAuthorizedToShare";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionsRequest() {
    this.path = "definition/process";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionByKeyRequest(String key) {
    this.path = "definition/process/" + key;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionXmlRequest(String key, Object version) {
    return buildGetProcessDefinitionXmlRequest(key, version, null);
  }

  public OptimizeRequestExecutor buildGetProcessDefinitionXmlRequest(String key, Object version, String tenantId) {
    this.path = "definition/process/xml";
    this.addSingleQueryParam("key", key);
    this.addSingleQueryParam("version", version);
    this.addSingleQueryParam("tenantId", tenantId);
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildProcessDefinitionCorrelation(BranchAnalysisQueryDto entity) {
    this.path = "analysis/correlation";
    this.method = POST;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableNamesForReportsRequest(List<String> reportIds) {
    GetVariableNamesForReportsRequestDto requestDto = new GetVariableNamesForReportsRequestDto();
    requestDto.setReportIds(reportIds);
    this.path = "variables/reports";
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableNamesRequest(ProcessVariableNameRequestDto variableRequestDto) {
    this.path = "variables/";
    this.method = POST;
    this.body = getBody(variableRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesForReportsRequest(ProcessVariableReportValuesRequestDto valuesRequestDto) {
    this.path = "variables/values/reports";
    this.method = POST;
    this.body = getBody(valuesRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesRequest(ProcessVariableValueRequestDto valueRequestDto) {
    this.path = "variables/values";
    this.method = POST;
    this.body = getBody(valueRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionInputVariableValuesRequest(DecisionVariableValueRequestDto requestDto) {
    this.path = "decision-variables/inputs/values";
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionInputVariableNamesRequest(DecisionVariableNameRequestDto variableRequestDto) {
    this.path = "decision-variables/inputs/names";
    this.method = POST;
    this.body = getBody(variableRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableValuesRequest(DecisionVariableValueRequestDto requestDto) {
    this.path = "decision-variables/outputs/values";
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildDecisionOutputVariableNamesRequest(DecisionVariableNameRequestDto variableRequestDto) {
    this.path = "decision-variables/outputs/names";
    this.method = POST;
    this.body = getBody(variableRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetAssigneesRequest(AssigneeRequestDto requestDto) {
    this.path = "/assignee/values";
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetCandidateGroupsRequest(AssigneeRequestDto requestDto) {
    this.path = "/candidateGroup/values";
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildGetFlowNodeNames(FlowNodeIdsToNamesRequestDto entity) {
    this.path = "flow-node/flowNodeNames";
    this.method = POST;
    this.body = getBody(entity);
    return this;
  }

  public OptimizeRequestExecutor buildCsvExportRequest(String reportId, String fileName) {
    this.path = "export/csv/" + reportId + "/" + fileName;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDynamicRawProcessCsvExportRequest(final ProcessRawDataCsvExportRequestDto request,
                                                                        final String fileName) {
    this.path = "export/csv/process/rawData/" + fileName;
    this.method = POST;
    this.body = getBody(request);
    return this;
  }

  public OptimizeRequestExecutor buildLogOutRequest() {
    this.path = "authentication/logout";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAuthTestRequest() {
    this.path = "authentication/test";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildValidateAndStoreLicenseRequest(String license) {
    this.path = "license/validate-and-store";
    this.method = POST;
    this.body = Entity.entity(license, MediaType.TEXT_PLAIN);
    return this;
  }

  public OptimizeRequestExecutor buildValidateLicenseRequest() {
    this.path = "license/validate";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionByTypeAndKeyRequest(final String type, final String key) {
    this.path = "/definition/" + type + "/" + key;
    this.method = GET;
    return this;
  }


  public OptimizeRequestExecutor buildGetDefinitionVersionsByTypeAndKeyRequest(final String type,
                                                                               final String key) {
    return buildGetDefinitionVersionsByTypeAndKeyRequest(type, key, null);
  }

  public OptimizeRequestExecutor buildGetDefinitionVersionsByTypeAndKeyRequest(final String type,
                                                                               final String key,
                                                                               final String filterByCollectionScope) {
    this.path = "/definition/" + type + "/" + key + "/versions";
    this.method = GET;
    addSingleQueryParam("filterByCollectionScope", filterByCollectionScope);
    return this;
  }

  public OptimizeRequestExecutor buildResolveDefinitionTenantsByTypeKeyAndVersionsRequest(final String type,
                                                                                          final String key,
                                                                                          final List<String> versions) {
    return buildResolveDefinitionTenantsByTypeKeyAndVersionsRequest(type, key, versions, null);
  }

  public OptimizeRequestExecutor buildResolveDefinitionTenantsByTypeKeyAndVersionsRequest(final String type,
                                                                                          final String key,
                                                                                          final List<String> versions,
                                                                                          final String filterByCollectionScope) {
    this.path = "/definition/" + type + "/" + key + "/_resolveTenantsForVersions";
    this.method = POST;
    this.body = getBody(
      DefinitionTenantsRequest.builder()
        .versions(versions)
        .filterByCollectionScope(filterByCollectionScope)
        .build()
    );
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitions() {
    this.path = "/definition";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionKeysByType(final String type) {
    return buildGetDefinitionKeysByType(type, null, null);
  }

  public OptimizeRequestExecutor buildGetDefinitionKeysByType(final String type,
                                                              final String filterByCollectionScope) {
    return buildGetDefinitionKeysByType(type, filterByCollectionScope, null);
  }

  public OptimizeRequestExecutor buildGetDefinitionKeysByType(final String type,
                                                              final String filterByCollectionScope,
                                                              final Boolean camundaEventImportedOnly) {
    this.path = "/definition/" + type + "/keys";
    this.method = GET;
    addSingleQueryParam("filterByCollectionScope", filterByCollectionScope);
    addSingleQueryParam("camundaEventImportedOnly", camundaEventImportedOnly);
    return this;
  }

  public OptimizeRequestExecutor buildGetDefinitionsGroupedByTenant() {
    this.path = "/definition/_groupByTenant";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionsRequest() {
    this.path = "definition/decision";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionXmlRequest(String key, Object version) {
    return buildGetDecisionDefinitionXmlRequest(key, version, null);
  }

  public OptimizeRequestExecutor buildGetDecisionDefinitionXmlRequest(String key, Object version, String tenantId) {
    this.path = "definition/decision/xml";
    this.addSingleQueryParam("key", key);
    this.addSingleQueryParam("version", version);
    this.addSingleQueryParam("tenantId", tenantId);
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetLocalizationRequest(final String localeCode) {
    this.path = "localization";
    this.method = GET;
    this.addSingleQueryParam("localeCode", localeCode);
    return this;
  }

  public OptimizeRequestExecutor buildGetLocalizedWhatsNewMarkdownRequest(final String localeCode) {
    this.path = "localization/whatsnew";
    this.method = GET;
    this.addSingleQueryParam("localeCode", localeCode);
    return this;
  }

  public OptimizeRequestExecutor buildFlowNodeOutliersRequest(String key,
                                                              List<String> version,
                                                              List<String> tenantIds) {
    this.path = "analysis/flowNodeOutliers";
    this.method = GET;
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersions", version);
    this.addSingleQueryParam("tenantIds", tenantIds);
    return this;
  }

  public OptimizeRequestExecutor buildFlowNodeDurationChartRequest(String key,
                                                                   List<String> version,
                                                                   List<String> tenantIds,
                                                                   String flowNodeId) {
    return buildFlowNodeDurationChartRequest(key, version, flowNodeId, tenantIds, null, null);
  }

  public OptimizeRequestExecutor buildFlowNodeDurationChartRequest(String key,
                                                                   List<String> version,
                                                                   String flowNodeId,
                                                                   List<String> tenantIds,
                                                                   Long lowerOutlierBound,
                                                                   Long higherOutlierBound) {
    this.path = "analysis/durationChart";
    this.method = GET;
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersions", version);
    this.addSingleQueryParam("flowNodeId", flowNodeId);
    this.addSingleQueryParam("tenantIds", tenantIds);
    this.addSingleQueryParam("lowerOutlierBound", lowerOutlierBound);
    this.addSingleQueryParam("higherOutlierBound", higherOutlierBound);
    return this;
  }

  public OptimizeRequestExecutor buildSignificantOutlierVariableTermsRequest(String key,
                                                                             List<String> version,
                                                                             List<String> tenantIds,
                                                                             String flowNodeId,
                                                                             Long lowerOutlierBound,
                                                                             Long higherOutlierBound) {
    this.path = "analysis/significantOutlierVariableTerms";
    this.method = GET;
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersions", version);
    this.addSingleQueryParam("flowNodeId", flowNodeId);
    this.addSingleQueryParam("tenantIds", tenantIds);
    this.addSingleQueryParam("lowerOutlierBound", lowerOutlierBound);
    this.addSingleQueryParam("higherOutlierBound", higherOutlierBound);
    return this;
  }

  public OptimizeRequestExecutor buildSignificantOutlierVariableTermsInstanceIdsRequest(String key,
                                                                                        List<String> version,
                                                                                        List<String> tenantIds,
                                                                                        String flowNodeId,
                                                                                        Long lowerOutlierBound,
                                                                                        Long higherOutlierBound,
                                                                                        String variableName,
                                                                                        String variableTerm) {
    this.path = "analysis/significantOutlierVariableTerms/processInstanceIdsExport";
    this.method = GET;
    this.addSingleQueryParam("processDefinitionKey", key);
    this.addSingleQueryParam("processDefinitionVersions", version);
    this.addSingleQueryParam("flowNodeId", flowNodeId);
    this.addSingleQueryParam("tenantIds", tenantIds);
    this.addSingleQueryParam("lowerOutlierBound", lowerOutlierBound);
    this.addSingleQueryParam("higherOutlierBound", higherOutlierBound);
    this.addSingleQueryParam("variableName", variableName);
    this.addSingleQueryParam("variableTerm", variableTerm);
    return this;
  }

  public OptimizeRequestExecutor buildCopyReportRequest(String id, String collectionId) {
    this.path = "report/" + id + "/copy";
    this.method = POST;
    Optional.ofNullable(collectionId).ifPresent(value -> addSingleQueryParam("collectionId", value));
    return this;
  }

  public OptimizeRequestExecutor buildCopyDashboardRequest(String id) {
    return buildCopyDashboardRequest(id, null);
  }

  public OptimizeRequestExecutor buildCopyDashboardRequest(String id, String collectionId) {
    this.path = "dashboard/" + id + "/copy";
    this.method = POST;
    Optional.ofNullable(collectionId).ifPresent(value -> addSingleQueryParam("collectionId", value));
    return this;
  }

  public OptimizeRequestExecutor buildGetIsEventProcessEnabledRequest() {
    this.path = "eventBasedProcess/isEnabled";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCreateEventProcessMappingRequest(EventProcessMappingCreateRequestDto eventProcessMappingCreateRequestDto) {
    this.path = "eventBasedProcess/";
    this.body = getBody(eventProcessMappingCreateRequestDto);
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildGetAllEventProcessMappingsRequests() {
    this.path = "eventBasedProcess";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateEventProcessMappingRequest(String eventProcessId,
                                                                       EventProcessMappingDto eventProcessMappingDto) {
    this.path = "eventBasedProcess/" + eventProcessId;
    this.body = getBody(eventProcessMappingDto);
    this.method = PUT;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateEventProcessRolesRequest(String eventProcessId,
                                                                     List<EventProcessRoleDto<IdentityDto>> roleRestDtos) {
    this.path = "eventBasedProcess/" + eventProcessId + "/role";
    this.method = PUT;
    this.body = getBody(roleRestDtos);
    return this;
  }

  public OptimizeRequestExecutor buildPublishEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId + "/_publish";
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildCancelPublishEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId + "/_cancelPublish";
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetDeleteConflictsForEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId + "/delete-conflicts";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildDeleteEventProcessMappingRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId;
    this.method = DELETE;
    return this;
  }

  public OptimizeRequestExecutor buildGetEventProcessMappingRolesRequest(String eventProcessId) {
    this.path = "eventBasedProcess/" + eventProcessId + "/role";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCleanupEventProcessMappingRequest(EventMappingCleanupRequestDto cleanupRequestDto) {
    this.path = "eventBasedProcess/_mappingCleanup";
    this.body = getBody(cleanupRequestDto);
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildGetScopeForCollectionRequest(final String collectionId) {
    this.path = "collection/" + collectionId + "/scope";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildAddScopeEntryToCollectionRequest(String collectionId,
                                                                       CollectionScopeEntryDto entryDto) {
    return buildAddScopeEntriesToCollectionRequest(collectionId, Collections.singletonList(entryDto));
  }

  public OptimizeRequestExecutor buildAddScopeEntriesToCollectionRequest(String collectionId,
                                                                         List<CollectionScopeEntryDto> entryDto) {
    this.path = "collection/" + collectionId + "/scope";
    this.method = PUT;
    this.body = getBody(entryDto);
    return this;
  }

  public OptimizeRequestExecutor buildDeleteScopeEntryFromCollectionRequest(String collectionId,
                                                                            String scopeEntryId) {
    return buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntryId, false);
  }

  public OptimizeRequestExecutor buildDeleteScopeEntryFromCollectionRequest(String collectionId,
                                                                            String scopeEntryId,
                                                                            Boolean force) {
    this.path = "collection/" + collectionId + "/scope/" + scopeEntryId;
    this.method = DELETE;
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildGetScopeDeletionConflictsRequest(final String collectionId,
                                                                       final String scopeEntryId) {
    this.path = "collection/" + collectionId + "/scope/" + scopeEntryId + "/delete-conflicts";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildUpdateCollectionScopeEntryRequest(String collectionId,
                                                                        String scopeEntryId,
                                                                        CollectionScopeEntryUpdateDto entryDto) {
    return buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntryId, entryDto, false);
  }

  public OptimizeRequestExecutor buildUpdateCollectionScopeEntryRequest(String collectionId,
                                                                        String scopeEntryId,
                                                                        CollectionScopeEntryUpdateDto entryDto,
                                                                        Boolean force) {
    this.path = "collection/" + collectionId + "/scope/" + scopeEntryId;
    this.method = PUT;
    this.body = getBody(entryDto);
    Optional.ofNullable(force).ifPresent(value -> addSingleQueryParam("force", value));
    return this;
  }

  public OptimizeRequestExecutor buildGetIdentityById(final String identityId) {
    this.path = IDENTITY_RESOURCE_PATH + "/" + identityId;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildCurrentUserIdentity() {
    this.path = IDENTITY_RESOURCE_PATH + CURRENT_USER_IDENTITY_SUB_PATH;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildSearchForIdentities(final String searchTerms) {
    return buildSearchForIdentities(searchTerms, null);
  }

  public OptimizeRequestExecutor buildSearchForIdentities(final String searchTerms, final Integer limit) {
    this.path = IDENTITY_RESOURCE_PATH + IDENTITY_SEARCH_SUB_PATH;
    this.method = GET;
    addSingleQueryParam("terms", searchTerms);
    Optional.ofNullable(limit).ifPresent(limitValue -> addSingleQueryParam("limit", limitValue));
    return this;
  }

  public OptimizeRequestExecutor buildCopyCollectionRequest(String collectionId) {
    this.path = "/collection/" + collectionId + "/copy";
    this.method = POST;
    return this;
  }

  public OptimizeRequestExecutor buildIngestEventBatch(final List<CloudEventDto> eventDtos, final String secret) {
    return buildIngestEventBatchWithMediaType(eventDtos, secret, CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH);
  }

  public OptimizeRequestExecutor buildIngestEventBatchWithMediaType(final List<CloudEventDto> eventDtos,
                                                                    final String secret,
                                                                    final String mediaType) {
    this.path = INGESTION_PATH + EVENT_BATCH_SUB_PATH;
    this.method = POST;
    addSingleHeader(HttpHeaders.AUTHORIZATION, secret);
    this.mediaType = mediaType;
    this.body = getBody(eventDtos);
    return this;
  }

  public OptimizeRequestExecutor buildIngestEventWithBody(final String bodyJson,
                                                          final String secret) {
    this.path = INGESTION_PATH + EVENT_BATCH_SUB_PATH;
    this.method = POST;
    addSingleHeader(HttpHeaders.AUTHORIZATION, secret);
    this.mediaType = CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH;
    this.body = Entity.json(bodyJson);
    return this;
  }

  public OptimizeRequestExecutor buildGetOnboardingStateForKey(final String key) {
    this.path = "onboarding/" + key;
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildSetOnboardingStateForKey(final String key, final boolean seen) {
    this.path = "onboarding/" + key;
    this.method = PUT;
    this.body = getBody(new OnboardingStateRestDto(seen));
    return this;
  }

  public OptimizeRequestExecutor buildPostEventCountRequest(final EventCountRequestDto eventCountRequestDto) {
    return buildPostEventCountRequest(null, null, eventCountRequestDto);
  }

  public OptimizeRequestExecutor buildPostEventCountRequest(final EventCountSorter eventCountSorter,
                                                            final String searchTerm,
                                                            final EventCountRequestDto eventCountRequestDto) {
    this.path = "event/count";
    this.method = POST;
    Optional.ofNullable(searchTerm).ifPresent(term -> addSingleQueryParam("searchTerm", term));
    Optional.ofNullable(eventCountSorter)
      .ifPresent(sorter -> {
        sorter.getSortBy().ifPresent(sortBy -> addSingleQueryParam("sortBy", sortBy));
        sorter.getSortOrder().ifPresent(sortOrder -> addSingleQueryParam("sortOrder", sortOrder));
      });
    this.body = Optional.ofNullable(eventCountRequestDto).map(this::getBody).orElse(null);
    return this;
  }

  public OptimizeRequestExecutor buildGetSettingsRequest() {
    this.path = "settings/";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildSetSettingsRequest(final SettingsDto settingsDto) {
    this.path = "settings/";
    this.method = PUT;
    this.body = getBody(settingsDto);
    return this;
  }

  private Entity getBody(Object entity) {
    try {
      return entity == null
        ? Entity.entity("", mediaType)
        : Entity.entity(objectMapper.writeValueAsString(entity), mediaType);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Couldn't serialize request" + e.getMessage(), e);
    }
  }

  private String authenticateUserRequest(String username, String password) {
    final CredentialsDto entity = new CredentialsDto(username, password);
    final Response response = defaultWebTarget.path("authentication")
      .request()
      .post(Entity.json(entity));
    return AuthCookieService.createOptimizeAuthCookieValue(response.readEntity(String.class));
  }

  private Map<String, Object> extractSortParams(EntitySorter sorter) {
    Map<String, Object> params = new HashMap<>();
    sorter.getSortBy().ifPresent(sortBy -> params.put(Sorter.SORT_BY, sortBy));
    sorter.getSortOrder().ifPresent(sortOrder -> params.put(Sorter.SORT_ORDER, sortOrder.toString().toLowerCase()));
    return params;
  }

  public WebTarget createWebTarget(final String targetUrl) {
    return createClient().target(targetUrl);
  }

  private Client createClient() {
    // register the default object provider for serialization/deserialization ob objects
    OptimizeObjectMapperContextResolver provider = new OptimizeObjectMapperContextResolver(objectMapper);

    Client client = ClientBuilder.newClient()
      .register(provider);
    client.register((ClientRequestFilter) requestContext -> log.debug(
      "EmbeddedTestClient request {} {}", requestContext.getMethod(), requestContext.getUri()
    ));
    client.register((ClientResponseFilter) (requestContext, responseContext) -> {
      if (responseContext.hasEntity()) {
        responseContext.setEntityStream(wrapEntityStreamIfNecessary(responseContext.getEntityStream()));
      }
      log.debug(
        "EmbeddedTestClient response for {} {}: {}",
        requestContext.getMethod(),
        requestContext.getUri(),
        responseContext.hasEntity() ? serializeBodyCappedToMaxSize(responseContext.getEntityStream()) : ""
      );
    });
    client.property(ClientProperties.CONNECT_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(ClientProperties.READ_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE);

    acceptSelfSignedCertificates(client);
    return client;
  }

  private void acceptSelfSignedCertificates(final Client client) {
    try {
      // @formatter:off
      client.getSslContext().init(null, new TrustManager[]{new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] arg0, String arg1) {}
        public void checkServerTrusted(X509Certificate[] arg0, String arg1) {}
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }}, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
      // @formatter:on
    } catch (KeyManagementException e) {
      throw new OptimizeIntegrationTestException(
        "Was not able to configure jersey client to accept all certificates",
        e
      );
    }
  }

  private InputStream wrapEntityStreamIfNecessary(final InputStream originalEntityStream) {
    return !originalEntityStream.markSupported() ? new BufferedInputStream(originalEntityStream) : originalEntityStream;
  }

  private String serializeBodyCappedToMaxSize(final InputStream entityStream) throws IOException {
    entityStream.mark(MAX_LOGGED_BODY_SIZE + 1);

    final byte[] entity = new byte[MAX_LOGGED_BODY_SIZE + 1];
    final int entitySize = entityStream.read(entity);
    final StringBuilder stringBuilder = new StringBuilder(
      new String(entity, 0, Math.min(entitySize, MAX_LOGGED_BODY_SIZE), StandardCharsets.UTF_8)
    );
    if (entitySize > MAX_LOGGED_BODY_SIZE) {
      stringBuilder.append("...");
    }
    stringBuilder.append('\n');

    entityStream.reset();
    return stringBuilder.toString();
  }

  private Map<String, Object> extractPagination(final PaginationRequestDto pagination) {
    Map<String, Object> params = new HashMap<>();
    Optional.ofNullable(pagination.getLimit()).ifPresent(limit -> params.put(PaginationRequestDto.LIMIT_PARAM, limit));
    Optional.ofNullable(pagination.getOffset())
      .ifPresent(offset -> params.put(PaginationRequestDto.OFFSET_PARAM, offset));
    return params;
  }

  private static ObjectMapper getDefaultObjectMapper() {
    final ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    return new ObjectMapperFactory(
      new OptimizeDateTimeFormatterFactory().getObject(), configurationService
    ).createOptimizeMapper();
  }

}
