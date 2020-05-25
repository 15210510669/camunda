/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableSet;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.camunda.bpm.engine.rest.dto.identity.UserDto;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.test.util.client.dto.MessageCorrelationDto;
import org.camunda.optimize.test.util.client.dto.TaskDto;
import org.camunda.optimize.test.util.client.dto.VariableValue;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.mapper.CustomDeserializer;
import org.camunda.optimize.service.util.mapper.CustomSerializer;
import org.elasticsearch.common.io.Streams;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_USER;

@Slf4j
public class SimpleEngineClient {
  // @formatter:off
  private static final TypeReference<List<TaskDto>> TASK_LIST_TYPE_REFERENCE = new TypeReference<List<TaskDto>>() {};
  private static final String ENGINE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  // @formatter:on
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(ENGINE_DATE_FORMAT);
  private static final int MAX_CONNECTIONS = 150;
  private static final Set<String> STANDARD_USERS = ImmutableSet.of("mary", "john", "peter");
  private static final Set<String> STANDARD_GROUPS = ImmutableSet.of("accounting", "management", "sales");
  public static final String DELAY_VARIABLE_NAME = "delay";

  private CloseableHttpClient client;
  private String engineRestEndpoint;
  private ObjectMapper objectMapper = new ObjectMapper();

  public SimpleEngineClient(String engineRestEndpoint) {
    this.engineRestEndpoint = engineRestEndpoint;
    client = HttpClientBuilder.create()
      .setMaxConnPerRoute(MAX_CONNECTIONS)
      .setMaxConnTotal(MAX_CONNECTIONS)
      .build();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    SimpleDateFormat df = new SimpleDateFormat(ENGINE_DATE_FORMAT);
    objectMapper.setDateFormat(df);
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    objectMapper.registerModule(javaTimeModule);
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(DATE_TIME_FORMATTER));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(DATE_TIME_FORMATTER));
  }

  @SneakyThrows
  public void initializeStandardUserAndGroupAuthorizations() {
    STANDARD_USERS.forEach(this::grantUserOptimizeAllDefinitionAndTenantsAndIdentitiesAuthorization);
    STANDARD_GROUPS.forEach(this::grantGroupOptimizeAllDefinitionAndAllTenantsAuthorization);
  }


  @SneakyThrows
  public void createTenant(final String tenantId) {
    final HttpPost createTenantPost = new HttpPost(engineRestEndpoint + "/tenant/create");
    createTenantPost.setEntity(new StringEntity("{\"id\": \"" + tenantId + "\", \"name\": \"" + tenantId + "\"}"));
    createTenantPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse tenantCreatedResponse = client.execute(createTenantPost)) {
      log.info("Created tenant {}.", tenantId);
    }
  }

  public List<String> deployProcesses(BpmnModelInstance modelInstance, int nVersions, List<String> tenants) {
    List<String> result = new ArrayList<>();
    IntStream.rangeClosed(1, nVersions)
      .mapToObj(n -> deployProcessAndGetIds(modelInstance, tenants))
      .collect(Collectors.toList()).forEach(result::addAll);
    return result;
  }

  public void close() {
    try {
      client.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @SneakyThrows
  public void createUser(final UserDto userDto) {
    HttpPost createUserRequest = new HttpPost(engineRestEndpoint + "/user/create");
    createUserRequest.addHeader("Content-Type", "application/json");
    createUserRequest.setEntity(new StringEntity(objectMapper.writeValueAsString(userDto), StandardCharsets.UTF_8));

    try (CloseableHttpResponse createResponse = client.execute(createUserRequest)) {
      if (createResponse.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        log.warn("Failed to create user with id {}", userDto.getProfile().getId());
      }
    }
  }

  public void grantGroupOptimizeAllDefinitionAndAllTenantsAuthorization(final String groupId) {
    createGrantAllOfTypeGroupAuthorization(RESOURCE_TYPE_APPLICATION, groupId);
    createGrantAllOfTypeGroupAuthorization(RESOURCE_TYPE_PROCESS_DEFINITION, groupId);
    createGrantAllOfTypeGroupAuthorization(RESOURCE_TYPE_DECISION_DEFINITION, groupId);
    createGrantAllOfTypeGroupAuthorization(RESOURCE_TYPE_TENANT, groupId);
  }

  public void grantUserOptimizeAllDefinitionAndTenantsAndIdentitiesAuthorization(final String userId) {
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_APPLICATION, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_PROCESS_DEFINITION, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_DECISION_DEFINITION, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_TENANT, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_USER, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_GROUP, userId);
  }

  public void cleanUpDeployments() {
    log.info("Starting deployments clean up");
    HttpGet get = new HttpGet(getDeploymentUri());
    String responseString;
    List<DeploymentDto> result = null;
    try (CloseableHttpResponse response = client.execute(get)) {
      responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<DeploymentDto>>() {
        }
      );
      log.info("Fetched " + result.size() + " deployments");
    } catch (IOException e) {
      log.error("Could fetch deployments from the Engine");
    }
    if (result != null) {
      result.forEach((deployment) -> {
        HttpDelete delete = new HttpDelete(getDeploymentUri() + deployment.getId());
        try {
          URI uri = new URIBuilder(delete.getURI())
            .addParameter("cascade", "true")
            .build();
          delete.setURI(uri);
          client.execute(delete).close();
          log.info("Deleted deployment with id " + deployment.getId());
        } catch (IOException | URISyntaxException e) {
          log.error("Could not delete deployment");
        }
      });
    }
    log.info("Deployment clean up finished");
  }

  public Optional<Boolean> getProcessInstanceDelayVariable(String procInstId) {
    HttpGet get =
      new HttpGet(engineRestEndpoint + "/process-instance/" + procInstId + "/variables/" + DELAY_VARIABLE_NAME);
    VariableValue variable = new VariableValue();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      variable = objectMapper.readValue(responseString, VariableValue.class);
    } catch (IOException e) {
      log.error("Error while trying to fetch the variable!!", e);
    }
    return Optional.ofNullable(variable.getValue()).map(Object::toString).map(Boolean::parseBoolean);
  }

  public ProcessInstanceEngineDto startProcessInstance(String procDefId, Map<String, Object> variables,
                                                       String businessKey) {
    HttpPost post = new HttpPost(getStartProcessInstanceUri(procDefId));
    post.addHeader("content-type", "application/json");
    final String jsonEntity = convertVariableMapAndBusinessKeyToJsonString(variables, businessKey);
    post.setEntity(new StringEntity(jsonEntity, StandardCharsets.UTF_8));
    try (CloseableHttpResponse response = client.execute(post)) {
      post.setURI(new URI(post.getURI().toString()));
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        throw new RuntimeException("Could not start the process definition " + procDefId +
                                     ". Reason: " + response.getStatusLine().getReasonPhrase());
      }
      final String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      return objectMapper.readValue(
        responseString,
        ProcessInstanceEngineDto.class
      );
    } catch (Exception e) {
      log.error("Error during start of process instance!");
      throw new RuntimeException(e);
    }
  }

  public void startDecisionInstance(String decisionDefinitionId,
                                    Map<String, Object> variables) {
    final HttpPost post = new HttpPost(getStartDecisionInstanceUri(decisionDefinitionId));
    post.addHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
    post.setEntity(new StringEntity(convertVariableMapToJsonString(variables), StandardCharsets.UTF_8));
    try {
      try (final CloseableHttpResponse response = client.execute(post)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          String body = "";
          if (response.getEntity() != null) {
            body = EntityUtils.toString(response.getEntity());
          }
          throw new RuntimeException(
            "Could not start the decision definition. " +
              "Request: [" + post.toString() + "]. " +
              "Response: [" + body + "]"
          );
        }
      }
    } catch (IOException e) {
      final String message = "Could not start the given decision model!";
      log.error(message, e);
      throw new RuntimeException(message, e);
    }
  }

  public void suspendProcessInstance(final String processInstanceId) {
    HttpPut suspendRequest = new HttpPut(getSuspendProcessInstanceUri(processInstanceId));
    suspendRequest.setHeader("Content-type", "application/json");
    suspendRequest.setEntity(new StringEntity(
      "{\n" +
        "\"suspended\": true\n" +
        "}",
      StandardCharsets.UTF_8
    ));
    try (CloseableHttpResponse response = client.execute(suspendRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new RuntimeException(
          "Could not suspend process instance. Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    } catch (Exception e) {
      log.error("Error while trying to suspend process instance!");
      throw new RuntimeException(e);
    }
  }

  public void correlateMessage(String messageName) {
    HttpPost post = new HttpPost(engineRestEndpoint + "/message/");
    post.setHeader("Content-type", "application/json");
    MessageCorrelationDto message = new MessageCorrelationDto();
    message.setAll(true);
    message.setMessageName(messageName);
    StringEntity content;
    try {
      content = new StringEntity(objectMapper.writeValueAsString(message), StandardCharsets.UTF_8);
      post.setEntity(content);
      try (CloseableHttpResponse response = client.execute(post)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != Response.Status.NO_CONTENT.getStatusCode()) {
          log.warn("Response code for correlating message should be 204, got " + statusCode + " instead");
          final String reponseBody = Streams.copyToString(new InputStreamReader(
            response.getEntity().getContent(), StandardCharsets.UTF_8
          ));
          log.warn("Response body was: " + reponseBody);
        }
      }
    } catch (Exception e) {
      log.warn("Error while trying to correlate message for name {}!", messageName);
    }
  }

  public List<TaskDto> getActiveTasksCreatedAfter(final String processDefinitionId,
                                                  final OffsetDateTime afterDateTime, final int limit) {
    HttpGet get = new HttpGet(getTaskListCreatedAfterUri(processDefinitionId, limit, afterDateTime));
    List<TaskDto> tasks = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      tasks = objectMapper.readValue(responseString, TASK_LIST_TYPE_REFERENCE);
    } catch (IOException e) {
      log.error("Error while trying to fetch the user task!!", e);
    }
    return tasks;
  }

  public List<TaskDto> getActiveTasksCreatedOn(final String processDefinitionId,
                                               final OffsetDateTime creationDateTime) {
    HttpGet get = new HttpGet(getTaskListCreatedOnUri(processDefinitionId, creationDateTime));
    List<TaskDto> tasks = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      tasks = objectMapper.readValue(responseString, TASK_LIST_TYPE_REFERENCE);
    } catch (IOException e) {
      log.error("Error while trying to fetch the user task!!", e);
    }
    return tasks;
  }

  public void claimTask(TaskDto task) throws IOException {
    HttpPost claimPost = new HttpPost(getClaimTaskUri(task.getId()));
    claimPost.setEntity(new StringEntity("{ \"userId\" : " + "\"demo\"" + "}"));
    claimPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse claimResponse = client.execute(claimPost)) {
      if (claimResponse.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new RuntimeException("Wrong error code when claiming user tasks!");
      }
    }
  }

  public void addOrRemoveIdentityLinks(TaskDto task) throws IOException {
    HttpGet identityLinksGet = new HttpGet(getTaskIdentityLinksUri(task.getId()));
    try (CloseableHttpResponse getLinksResponse = client.execute(identityLinksGet)) {
      String content = EntityUtils.toString(getLinksResponse.getEntity());
      List<JsonNode> links = objectMapper.readValue(content, new TypeReference<List<JsonNode>>() {
      });

      if (links.size() == 0) {
        HttpPost candidatePost = new HttpPost(getTaskIdentityLinksUri(task.getId()));
        candidatePost.setEntity(
          new StringEntity("{\"userId\":\"demo\", \"type\":\"candidate\"}")
        );
        candidatePost.addHeader("Content-Type", "application/json");
        client.execute(candidatePost).close();
      } else {
        HttpPost candidateDeletePost = new HttpPost(getTaskIdentityLinksUri(task.getId()) + "/delete");
        candidateDeletePost.addHeader("Content-Type", "application/json");
        candidateDeletePost.setEntity(new StringEntity(objectMapper.writeValueAsString(links.get(0))));
        client.execute(candidateDeletePost).close();
      }
    }
  }

  public void completeUserTask(TaskDto task) {
    HttpPost completePost = new HttpPost(getCompleteTaskUri(task.getId()));
    completePost.setEntity(new StringEntity("{}", StandardCharsets.UTF_8));
    completePost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = client.execute(completePost)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new RuntimeException("Wrong error code when completing user tasks!");
      }
    } catch (Exception e) {
      log.error("Could not complete user task!", e);
    }
  }

  public List<String> deployDecisions(DmnModelInstance modelInstance, int nVersions, List<String> tenants) {
    List<String> result = new ArrayList<>();
    IntStream.rangeClosed(1, nVersions)
      .mapToObj(n -> deployDecisionAndGetIds(modelInstance, tenants))
      .collect(Collectors.toList()).forEach(result::addAll);
    return result;
  }

  private List<String> deployDecisionAndGetIds(DmnModelInstance modelInstance, List<String> tenants) {
    List<DeploymentDto> deploymentDto = deployDecisionDefinition(modelInstance, tenants);
    return deploymentDto.stream().map(this::getDecisionDefinitionId).collect(Collectors.toList());
  }

  public List<DeploymentDto> deployDecisionDefinition(DmnModelInstance dmnModelInstance, List<String> tenants) {
    String decision = Dmn.convertToString(dmnModelInstance);
    List<HttpPost> deploymentRequest = createDecisionDeploymentRequest(decision, tenants);
    return deploymentRequest.stream().map(d -> {
      DeploymentDto deployment = new DeploymentDto();
      try (CloseableHttpResponse response = client.execute(d)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new RuntimeException("Something really bad happened during deployment, " +
                                       "could not create a deployment!");
        }
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        deployment = objectMapper.readValue(responseString, DeploymentDto.class);
      } catch (IOException e) {
        log.error("Error during deployment request! Could not deploy the given dmn model!", e);
      }
      return deployment;
    }).collect(Collectors.toList());
  }

  private List<String> deployProcessAndGetIds(BpmnModelInstance modelInstance, List<String> tenants) {
    List<DeploymentDto> deploymentDto = deployProcess(modelInstance, tenants);
    return deploymentDto.stream().map(this::getProcessDefinitionId).collect(Collectors.toList());
  }

  private String getDecisionDefinitionId(DeploymentDto deployment) {
    List<DecisionDefinitionEngineDto> decisionDefinitions = getAllDecisionDefinitions(deployment);
    if (decisionDefinitions.size() != 1) {
      log.warn("Deployment should contain only one decision definition!");
    }
    return decisionDefinitions.get(0).getId();
  }

  private List<DecisionDefinitionEngineDto> getAllDecisionDefinitions(DeploymentDto deployment) {
    HttpRequestBase get = new HttpGet(getDecisionDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("deploymentId", deployment.getId())
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    List<DecisionDefinitionEngineDto> result = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<DecisionDefinitionEngineDto>>() {
        }
      );
    } catch (Exception e) {
      log.error("Could not fetch all decision definitions for given deployment!", e);
    }

    return result;
  }


  private String getProcessDefinitionId(DeploymentDto deployment) {
    List<ProcessDefinitionEngineDto> processDefinitions = getAllProcessDefinitions(deployment);
    if (processDefinitions.size() != 1) {
      log.warn("Deployment should contain only one process definition!");
    }
    return processDefinitions.get(0).getId();
  }

  private List<ProcessDefinitionEngineDto> getAllProcessDefinitions(DeploymentDto deployment) {
    HttpRequestBase get = new HttpGet(getProcessDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("deploymentId", deployment.getId())
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    List<ProcessDefinitionEngineDto> result = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<ProcessDefinitionEngineDto>>() {
        }
      );
    } catch (Exception e) {
      log.error("Could not fetch all process definitions for given deployment!", e);
    }

    return result;
  }

  public List<ProcessDefinitionEngineDto> getLatestProcessDefinitions() {
    HttpRequestBase get = new HttpGet(getProcessDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("latestVersion", "true")
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    List<ProcessDefinitionEngineDto> result = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<ProcessDefinitionEngineDto>>() {
        }
      );
    } catch (Exception e) {
      log.error("Could not fetch all process definitions for given deployment!", e);
    }

    return result;
  }

  public List<DecisionDefinitionEngineDto> getLatestDecisionDefinitions() {
    HttpRequestBase get = new HttpGet(getDecisionDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("latestVersion", "true")
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    List<DecisionDefinitionEngineDto> result = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<DecisionDefinitionEngineDto>>() {
        }
      );
    } catch (Exception e) {
      log.error("Could not fetch all decision definitions for given deployment!", e);
    }

    return result;
  }

  public ProcessDefinitionXmlEngineDto getProcessDefinitionXml(String processDefinitionId) {
    HttpRequestBase get = new HttpGet(getProcessDefinitionXmlUri(processDefinitionId));
    CloseableHttpResponse response;
    try {
      response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      ProcessDefinitionXmlEngineDto xml =
        objectMapper.readValue(responseString, ProcessDefinitionXmlEngineDto.class);
      response.close();
      return xml;
    } catch (IOException e) {
      String errorMessage =
        String.format("Could not fetch the process definition xml for id [%s]!", processDefinitionId);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }


  private List<DeploymentDto> deployProcess(BpmnModelInstance bpmnModelInstance, List<String> tenants) {
    String process = Bpmn.convertToString(bpmnModelInstance);
    List<HttpPost> deploymentRequest = createProcessDeploymentRequest(process, tenants);
    return deploymentRequest.stream().map(d -> {
      DeploymentDto deployment = new DeploymentDto();
      try (CloseableHttpResponse response = client.execute(d)) {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new RuntimeException("Something really bad happened during deployment, " +
                                       "could not create a deployment!\n" +
                                       responseString);
        }
        deployment = objectMapper.readValue(responseString, DeploymentDto.class);
      } catch (IOException e) {
        log.error("Error during deployment request! Could not deploy the given process model!", e);
      }
      return deployment;
    }).collect(Collectors.toList());
  }

  @SneakyThrows
  private String convertVariableMapToJsonString(Map<String, Object> plainVariables) {
    return convertVariableMapAndBusinessKeyToJsonString(plainVariables, null);
  }

  @SneakyThrows
  private String convertVariableMapAndBusinessKeyToJsonString(Map<String, Object> plainVariables,
                                                              String businessKey) {
    Map<String, Object> jsonWrapper = new HashMap<>();
    jsonWrapper.put("variables", extractVariablesJsonMap(plainVariables));
    Optional.ofNullable(businessKey).ifPresent(key -> jsonWrapper.put("businessKey", businessKey));
    return objectMapper.writeValueAsString(jsonWrapper);
  }

  private Map<String, Object> extractVariablesJsonMap(Map<String, Object> plainVariables) {
    Map<String, Object> variables = new HashMap<>();
    for (Map.Entry<String, Object> nameToValue : plainVariables.entrySet()) {
      Object value = nameToValue.getValue();
      if (value instanceof ComplexVariableDto) {
        variables.put(nameToValue.getKey(), value);
      } else {
        Map<String, Object> fields = new HashMap<>();
        fields.put("value", nameToValue.getValue());
        fields.put("type", nameToValue.getValue().getClass().getSimpleName());
        variables.put(nameToValue.getKey(), fields);
      }
    }
    return variables;
  }

  private List<HttpPost> createProcessDeploymentRequest(String process, List<String> tenants) {
    return tenants.stream().map(t -> {
      HttpPost post = new HttpPost(getCreateDeploymentUri());
      MultipartEntityBuilder builder = MultipartEntityBuilder
        .create()
        .addTextBody("deployment-name", "deployment")
        .addTextBody("enable-duplicate-filtering", "false")
        .addTextBody("deployment-source", "process application");

      if (t != null) {
        builder.addTextBody("tenant-id", t);
      }

      HttpEntity entity = builder.addBinaryBody(
        "data",
        process.getBytes(StandardCharsets.UTF_8),
        ContentType.APPLICATION_OCTET_STREAM,
        "some_process.bpmn"
      ).build();
      post.setEntity(entity);
      return post;
    }).collect(Collectors.toList());
  }

  private List<HttpPost> createDecisionDeploymentRequest(String decision, List<String> tenants) {
    return tenants.stream().map(t -> {
      HttpPost post = new HttpPost(getCreateDeploymentUri());
      MultipartEntityBuilder builder = MultipartEntityBuilder
        .create()
        .addTextBody("deployment-name", "deployment")
        .addTextBody("enable-duplicate-filtering", "false")
        .addTextBody("deployment-source", "process application");

      if (t != null) {
        builder.addTextBody("tenant-id", t);
      }

      HttpEntity entity = builder.addBinaryBody(
        "data",
        decision.getBytes(StandardCharsets.UTF_8),
        ContentType.APPLICATION_OCTET_STREAM,
        "decision.dmn"
      ).build();

      post.setEntity(entity);
      return post;
    }).collect(Collectors.toList());
  }

  @SneakyThrows
  private String serializeDateTimeToUrlEncodedString(final OffsetDateTime createdAfter) {
    return URLEncoder.encode(DATE_TIME_FORMATTER.format(createdAfter), StandardCharsets.UTF_8.name());
  }

  private String getTaskIdentityLinksUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/identity-links";
  }

  public void unclaimTask(TaskDto task) throws IOException {
    HttpPost unclaimPost = new HttpPost(getUnclaimTaskUri(task.getId()));
    client.execute(unclaimPost).close();
  }

  private void createGrantAllOfTypeUserAuthorization(final int resourceType, final String userId) {
    createGrantAllOfTypeAuthorization(resourceType, userId, null);
  }

  private void createGrantAllOfTypeGroupAuthorization(final int resourceType, final String groupId) {
    createGrantAllOfTypeAuthorization(resourceType, null, groupId);
  }

  @SneakyThrows
  private void createGrantAllOfTypeAuthorization(final int resourceType, final String userId, final String groupId) {
    final HttpPost authPost = new HttpPost(engineRestEndpoint + "/authorization/create");
    final AuthorizationDto globalAppAuth = new AuthorizationDto(
      null, 1, Collections.singletonList("ALL"), userId, groupId, resourceType, "*"
    );
    authPost.setEntity(new StringEntity(objectMapper.writeValueAsString(globalAppAuth)));
    authPost.addHeader("Content-Type", "application/json");

    try (CloseableHttpResponse createUserResponse = client.execute(authPost)) {
      log.info(
        "Response Status Code {} when granting ALL authorization for resource type {} to user {}.",
        createUserResponse.getStatusLine().getStatusCode(),
        resourceType,
        userId
      );
      if (createUserResponse.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        log.warn(IOUtils.toString(createUserResponse.getEntity().getContent(), StandardCharsets.UTF_8));
      }
    }
  }

  private String getStartProcessInstanceUri(String procDefId) {
    return engineRestEndpoint + "/process-definition/" + procDefId + "/start";
  }

  private String getStartDecisionInstanceUri(final String decisionDefinitionId) {
    return engineRestEndpoint + "/decision-definition/" + decisionDefinitionId + "/evaluate";
  }

  private String getSuspendProcessInstanceUri(final String processInstanceId) {
    return engineRestEndpoint + "/process-instance/" + processInstanceId + "/suspended";
  }

  private String getTaskListCreatedAfterUri(final String processDefinitionId, long limit,
                                            final OffsetDateTime createdAfter) {
    return engineRestEndpoint + "/task?active=true&sortBy=created&sortOrder=asc" +
      "&processDefinitionId=" + processDefinitionId +
      "&maxResults=" + limit +
      "&createdAfter=" + serializeDateTimeToUrlEncodedString(createdAfter);
  }

  private String getTaskListCreatedOnUri(final String processDefinitionId, final OffsetDateTime createdOn) {
    return engineRestEndpoint + "/task?active=true" +
      "&processDefinitionId=" + processDefinitionId +
      "&createdOn=" + serializeDateTimeToUrlEncodedString(createdOn);
  }

  private String getProcessDefinitionUri() {
    return engineRestEndpoint + "/process-definition";
  }

  private String getDecisionDefinitionUri() {
    return engineRestEndpoint + "/decision-definition";
  }

  private String getCreateDeploymentUri() {
    return getDeploymentUri() + "create";
  }

  private String getDeploymentUri() {
    return engineRestEndpoint + "/deployment/";
  }

  private String getClaimTaskUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/claim";
  }

  private String getUnclaimTaskUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/unclaim";
  }

  private String getCompleteTaskUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/complete";
  }

  private String getProcessDefinitionXmlUri(String processDefinitionId) {
    return getProcessDefinitionUri() + "/" + processDefinitionId + "/xml";
  }

}
