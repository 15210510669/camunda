/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.security.InvalidParameterException;
import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.util.VariableHelper.isVariableTypeSupported;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.scriptQuery;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessVariableUpdateWriter {

  private static final String VARIABLE_UPDATES_FROM_ENGINE = "variableUpdatesFromEngine";

  private final OptimizeElasticsearchClient esClient;
  protected final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  public List<ImportRequestDto> generateVariableUpdateImports(List<ProcessVariableDto> variables) {
    String importItemName = "variables";
    log.debug("Creating imports for {} [{}].", variables.size(), importItemName);

    Map<String, List<OptimizeDto>> processInstanceIdToVariables = groupVariablesByProcessInstanceIds(variables);

    return processInstanceIdToVariables.entrySet().stream()
      .map(entry -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(createUpdateRequestForProcessInstanceVariables(entry))
        .build())
      .filter(update -> Objects.nonNull(update.getRequest()))
      .collect(Collectors.toList());
  }

  private UpdateRequest createUpdateRequestForProcessInstanceVariables(
    final Map.Entry<String, List<OptimizeDto>> processInstanceIdToVariables) {
    if (!(processInstanceIdToVariables.getValue().stream().allMatch(dto -> dto instanceof ProcessVariableDto))) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    final List<ProcessVariableDto> variablesWithAllInformation =
      (List<ProcessVariableDto>) (List<?>) processInstanceIdToVariables
        .getValue();
    final String processInstanceId = processInstanceIdToVariables.getKey();

    List<SimpleProcessVariableDto> variables = mapToSimpleVariables(variablesWithAllInformation);
    Map<String, Object> params = buildParameters(variables);

    final Script updateScript = createDefaultScript(createInlineUpdateScript(), params);

    if (variablesWithAllInformation.isEmpty()) {
      //all is lost, no variables to persist, should have crashed before.
      return null;
    }
    final ProcessVariableDto firstVariable = variablesWithAllInformation.get(0);
    String newEntryIfAbsent = null;
    try {
      newEntryIfAbsent = getNewProcessInstanceRecordString(
        processInstanceId,
        firstVariable.getEngineAlias(),
        firstVariable.getTenantId(),
        variables
      );
    } catch (JsonProcessingException e) {
      String reason = String.format(
        "Error while processing JSON for activity instances with ID [%s].",
        processInstanceId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (newEntryIfAbsent != null) {
      return new UpdateRequest()
        .index(PROCESS_INSTANCE_INDEX_NAME)
        .id(processInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    }
    return null;
  }

  public void deleteVariableDataByProcessInstanceIds(final List<String> processInstanceIds) {
    final BulkRequest bulkRequest = new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    log.debug("Deleting variable data on [{}] process instance documents with bulk request.", processInstanceIds.size());
    final Script variableClearScript = createVariableClearScript();
    processInstanceIds.forEach(
      id -> bulkRequest.add(new UpdateRequest(PROCESS_INSTANCE_INDEX_NAME, id).script(variableClearScript))
    );
    ElasticsearchWriterUtil.doBulkRequest(esClient, bulkRequest, PROCESS_INSTANCE_INDEX_NAME);
  }

  private Map<String, List<OptimizeDto>> groupVariablesByProcessInstanceIds(List<ProcessVariableDto> variableUpdates) {
    Map<String, List<OptimizeDto>> processInstanceIdToVariables = new HashMap<>();
    for (ProcessVariableDto variable : variableUpdates) {
      if (isVariableFromCaseDefinition(variable) || !isVariableTypeSupported(variable.getType())) {
        log.warn(
          "Variable [{}] is either a case definition variable or the type [{}] is not supported!",
          variable, variable.getType()
        );
        continue;
      }
      processInstanceIdToVariables.putIfAbsent(variable.getProcessInstanceId(), new ArrayList<>());
      processInstanceIdToVariables.get(variable.getProcessInstanceId()).add(variable);
    }
    return processInstanceIdToVariables;
  }

  private List<SimpleProcessVariableDto> mapToSimpleVariables(final List<ProcessVariableDto> variablesWitAllInformation) {
    return variablesWitAllInformation
      .stream()
      .map(var -> new SimpleProcessVariableDto(
        var.getId(),
        var.getName(),
        var.getType(),
        var.getValue(),
        var.getVersion()
      ))
      .collect(Collectors.toList());
  }

  private Map<String, Object> buildParameters(final List<SimpleProcessVariableDto> variables) {
    Map<String, Object> params = new HashMap<>();
    params.put(
      VARIABLE_UPDATES_FROM_ENGINE,
      objectMapper.convertValue(variables, new TypeReference<List<Map>>() {
      })
    );
    return params;
  }

  private String createInlineUpdateScript() {
    StringBuilder builder = new StringBuilder();
    Map<String, String> substitutions = new HashMap<>();
    substitutions.put("variables", VARIABLES);
    substitutions.put("variableUpdatesFromEngine", VARIABLE_UPDATES_FROM_ENGINE);
    final StringSubstitutor sub = new StringSubstitutor(substitutions);
    // @formatter:off
    String variableScript =
      "HashMap varIdToVar = new HashMap();" +
      "for (def var : ctx._source.${variables}) {" +
        "varIdToVar.put(var.id, var);" +
      "}" +
      "for (def var : params.${variableUpdatesFromEngine}) {" +
        "varIdToVar.compute(var.id, (k, v) -> { " +
        "  if (v == null) {" +
        "    return var;"   +
        "  } else {" +
        "    return v.version > var.version? v : var;" +
        "  }" +
        "});" +
      "}" +
      "ctx._source.${variables} = varIdToVar.values();\n";
    // @formatter:on
    String resolvedVariableScript = sub.replace(variableScript);
    builder.append(resolvedVariableScript);
    return builder.toString();
  }

  private String getNewProcessInstanceRecordString(final String processInstanceId,
                                                   final String engineAlias,
                                                   final String tenantId,
                                                   final List<SimpleProcessVariableDto> variables)
    throws JsonProcessingException {
    final ProcessInstanceDto procInst = new ProcessInstanceDto()
      .setProcessInstanceId(processInstanceId)
      .setEngine(engineAlias)
      .setTenantId(tenantId);
    procInst.getVariables().addAll(variables);

    return objectMapper.writeValueAsString(procInst);
  }

  private boolean isVariableFromCaseDefinition(ProcessVariableDto variable) {
    return variable.getProcessDefinitionId() == null;
  }

  private static Script createVariableClearScript() {
    String script = MessageFormat.format("ctx._source.{0} = new ArrayList();\n", VARIABLES);
    return new Script(script);
  }

  private static void addAtLeastOneVariableArrayNotEmptyNestedFilters(final BoolQueryBuilder queryBuilder) {
    queryBuilder.must(
      nestedQuery(
        VARIABLES,
        scriptQuery(new Script(MessageFormat.format("doc[''{0}.id''].length > 0", VARIABLES))),
        ScoreMode.None
      )
    );
  }

}
