/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.EngineDefinitionAuthorizationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;


@RequiredArgsConstructor
@Component
@Slf4j
public class BranchAnalysisReader {

  private final OptimizeElasticsearchClient esClient;
  private final DefinitionService definitionService;
  private final EngineDefinitionAuthorizationService definitionAuthorizationService;
  private final ProcessQueryFilterEnhancer queryFilterEnhancer;
  private final ProcessDefinitionReader processDefinitionReader;

  public BranchAnalysisDto branchAnalysis(final String userId,
                                          final BranchAnalysisQueryDto request,
                                          final ZoneId timezone) {
    ValidationHelper.validate(request);
    if (!definitionAuthorizationService.isAuthorizedToSeeProcessDefinition(
      userId, IdentityType.USER, request.getProcessDefinitionKey(), request.getTenantIds()
    )) {
      throw new ForbiddenException(
        "Current user is not authorized to access data of the provided process definition and tenant combination");
    }

    log.debug(
      "Performing branch analysis on process definition with key [{}] and versions [{}]",
      request.getProcessDefinitionKey(),
      request.getProcessDefinitionVersions()
    );

    final BranchAnalysisDto result = new BranchAnalysisDto();
    getBpmnModelInstance(
      request.getProcessDefinitionKey(),
      request.getProcessDefinitionVersions(),
      request.getTenantIds()
    ).ifPresent(bpmnModelInstance -> {
      final List<FlowNode> gatewayOutcomes = fetchGatewayOutcomes(bpmnModelInstance, request.getGateway());
      final Set<String> activityIdsWithMultipleIncomingSequenceFlows =
        extractFlowNodesWithMultipleIncomingSequenceFlows(bpmnModelInstance);
      final FlowNode gateway = bpmnModelInstance.getModelElementById(request.getGateway());
      final FlowNode end = bpmnModelInstance.getModelElementById(request.getEnd());
      final boolean canReachEndFromGateway = isPathPossible(gateway, end, Sets.newHashSet());

      for (FlowNode activity : gatewayOutcomes) {
        final Set<String> activitiesToExcludeFromBranchAnalysis = extractActivitiesToExclude(
          gatewayOutcomes, activityIdsWithMultipleIncomingSequenceFlows, activity.getId(), request.getEnd()
        );
        BranchAnalysisOutcomeDto branchAnalysis = new BranchAnalysisOutcomeDto();
        if (canReachEndFromGateway) {
          branchAnalysis = branchAnalysis(
            activity, request, activitiesToExcludeFromBranchAnalysis, timezone
          );
        } else {
          branchAnalysis.setActivityId(activity.getId());
          branchAnalysis.setActivitiesReached(0L); // End event cannot be reached from gateway
          branchAnalysis.setActivityCount((calculateActivityCount(
            activity.getId(),
            request,
            activitiesToExcludeFromBranchAnalysis,
            timezone
          )));
        }
        result.getFollowingNodes().put(branchAnalysis.getActivityId(), branchAnalysis);
      }

      result.setEndEvent(request.getEnd());
      result.setTotal(calculateActivityCount(request.getEnd(), request, Collections.emptySet(), timezone));
    });

    return result;
  }

  private boolean isPathPossible(final FlowNode currentNode, final FlowNode targetNode,
                                 final Set<FlowNode> visitedNodes) {
    visitedNodes.add(currentNode);
    final List<FlowNode> succeedingNodes = currentNode.getSucceedingNodes().list();
    boolean pathFound = false;
    for (FlowNode succeedingNode : succeedingNodes) {
      if (visitedNodes.contains(succeedingNode)) {
        continue;
      }
      pathFound = succeedingNode.equals(targetNode) || isPathPossible(succeedingNode, targetNode, visitedNodes);
      if (pathFound) {
        break;
      }
    }
    return pathFound;
  }

  private Set<String> extractActivitiesToExclude(final List<FlowNode> gatewayOutcomes,
                                                 final Set<String> activityIdsWithMultipleIncomingSequenceFlows,
                                                 final String currentActivityId,
                                                 final String endEventActivityId) {
    Set<String> activitiesToExcludeFromBranchAnalysis = new HashSet<>();
    for (FlowNode gatewayOutgoingNode : gatewayOutcomes) {
      String activityId = gatewayOutgoingNode.getId();
      if (!activityIdsWithMultipleIncomingSequenceFlows.contains(activityId)) {
        activitiesToExcludeFromBranchAnalysis.add(gatewayOutgoingNode.getId());
      }
    }
    activitiesToExcludeFromBranchAnalysis.remove(currentActivityId);
    activitiesToExcludeFromBranchAnalysis.remove(endEventActivityId);
    return activitiesToExcludeFromBranchAnalysis;
  }

  private BranchAnalysisOutcomeDto branchAnalysis(final FlowNode flowNode,
                                                  final BranchAnalysisQueryDto request,
                                                  final Set<String> activitiesToExclude,
                                                  final ZoneId timezone) {

    BranchAnalysisOutcomeDto result = new BranchAnalysisOutcomeDto();
    result.setActivityId(flowNode.getId());
    result.setActivityCount(calculateActivityCount(flowNode.getId(), request, activitiesToExclude, timezone));
    result.setActivitiesReached(calculateReachedEndEventActivityCount(
      flowNode.getId(),
      request,
      activitiesToExclude,
      timezone
    ));

    return result;
  }

  private long calculateReachedEndEventActivityCount(final String activityId,
                                                     final BranchAnalysisQueryDto request,
                                                     final Set<String> activitiesToExclude,
                                                     final ZoneId timezone) {
    final BoolQueryBuilder query = buildBaseQuery(request, activitiesToExclude)
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId))
      .must(createMustMatchActivityIdQuery(request.getEnd()));
    return executeQuery(request, query, timezone);
  }

  private long calculateActivityCount(final String activityId,
                                      final BranchAnalysisQueryDto request,
                                      final Set<String> activitiesToExclude,
                                      final ZoneId timezone) {
    final BoolQueryBuilder query = buildBaseQuery(request, activitiesToExclude)
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId));
    return executeQuery(request, query, timezone);
  }

  private BoolQueryBuilder buildBaseQuery(final BranchAnalysisQueryDto request, final Set<String> activitiesToExclude) {
    final BoolQueryBuilder query = createDefinitionQuery(
      request.getProcessDefinitionKey(),
      request.getProcessDefinitionVersions(),
      request.getTenantIds(),
      new ProcessInstanceIndex(),
      processDefinitionReader::getLatestVersionToKey
    );
    excludeActivities(activitiesToExclude, query);
    return query;
  }

  private void excludeActivities(final Set<String> activitiesToExclude,
                                 final BoolQueryBuilder query) {
    for (String excludeActivityId : activitiesToExclude) {
      query.mustNot(createMustMatchActivityIdQuery(excludeActivityId));
    }
  }

  private NestedQueryBuilder createMustMatchActivityIdQuery(final String activityId) {
    return nestedQuery(
      ProcessInstanceIndex.EVENTS,
      termQuery("events.activityId", activityId),
      ScoreMode.None
    );
  }

  private long executeQuery(final BranchAnalysisQueryDto request,
                            final BoolQueryBuilder query,
                            final ZoneId timezone) {
    queryFilterEnhancer.addFilterToQuery(query, request.getFilter(), timezone);

    final CountRequest searchRequest = new CountRequest(PROCESS_INSTANCE_INDEX_NAME)
      .source(new SearchSourceBuilder().query(query));
    try {
      final CountResponse countResponse = esClient.count(searchRequest, RequestOptions.DEFAULT);
      return countResponse.getCount();
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to perform branch analysis on process definition with key [%s] and versions [%s}]",
        request.getProcessDefinitionKey(),
        request.getProcessDefinitionVersions()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private List<FlowNode> fetchGatewayOutcomes(final BpmnModelInstance bpmnModelInstance,
                                              final String gatewayActivityId) {
    List<FlowNode> result = new ArrayList<>();
    FlowNode flowNode = bpmnModelInstance.getModelElementById(gatewayActivityId);
    for (SequenceFlow sequence : flowNode.getOutgoing()) {
      result.add(sequence.getTarget());
    }
    return result;
  }

  private Optional<BpmnModelInstance> getBpmnModelInstance(final String definitionKey,
                                                           final List<String> definitionVersions,
                                                           final List<String> tenantIds) {
    final Optional<String> processDefinitionXml = tenantIds.stream()
      .map(tenantId -> getDefinitionXml(definitionKey, definitionVersions, Collections.singletonList(tenantId)))
      .filter(Optional::isPresent)
      .findFirst()
      .orElseGet(() -> getDefinitionXml(definitionKey, definitionVersions, ReportConstants.DEFAULT_TENANT_IDS));

    return processDefinitionXml
      .map(xml -> Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes())));
  }

  private Optional<String> getDefinitionXml(final String definitionKey,
                                            final List<String> definitionVersions,
                                            final List<String> tenants) {
    final Optional<ProcessDefinitionOptimizeDto> definitionWithXmlAsService =
      definitionService.getDefinitionWithXmlAsService(
        DefinitionType.PROCESS,
        definitionKey,
        definitionVersions,
        tenants
      );
    return definitionWithXmlAsService
      .map(ProcessDefinitionOptimizeDto::getBpmn20Xml);
  }

  private Set<String> extractFlowNodesWithMultipleIncomingSequenceFlows(final BpmnModelInstance bpmnModelInstance) {
    Collection<SequenceFlow> sequenceFlowCollection = bpmnModelInstance.getModelElementsByType(SequenceFlow.class);
    Set<String> activitiesWithOneIncomingSequenceFlow = new HashSet<>();
    Set<String> activityIdsWithMultipleIncomingSequenceFlows = new HashSet<>();
    for (SequenceFlow sequenceFlow : sequenceFlowCollection) {
      String targetActivityId = sequenceFlow.getTarget().getId();
      if (activitiesWithOneIncomingSequenceFlow.contains(targetActivityId)) {
        activityIdsWithMultipleIncomingSequenceFlows.add(targetActivityId);
      } else {
        activitiesWithOneIncomingSequenceFlow.add(targetActivityId);
      }
    }
    return activityIdsWithMultipleIncomingSequenceFlows;
  }
}
