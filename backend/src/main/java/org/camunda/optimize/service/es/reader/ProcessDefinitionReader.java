package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.VERSION;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.PROCESS_DEFINITION_VERSION;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ProcessDefinitionReader {
  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionReader.class);

  @Autowired
  private Client esclient;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private SessionService sessionService;

  private List<ExtendedProcessDefinitionOptimizeDto> getProcessDefinitions(String userId) {
    return this.getProcessDefinitions(userId, false);
  }

  public List<ExtendedProcessDefinitionOptimizeDto> getProcessDefinitions(String userId,
                                                                          boolean withXml) {
    logger.debug("Fetching process definitions");
    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();

    ArrayList<String> types = new ArrayList<>();
    types.add(configurationService.getProcessDefinitionType());
    if (withXml) {
      types.add(configurationService.getProcessDefinitionXmlType());
    }

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(types))
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(20)
        .get();

    HashMap<String, ExtendedProcessDefinitionOptimizeDto> definitionsResult = new HashMap<>();
    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        if (configurationService.getProcessDefinitionType().equals(hit.getType())) {
          addFullDefinition(definitionsResult, hit);
        } else if (configurationService.getProcessDefinitionXmlType().equals(hit.getType())) {
          addPartialDefinition(definitionsResult, hit);
        } else {
          throw new OptimizeRuntimeException("Unknown type returned as process definition");
        }
      }
      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);

    List<ExtendedProcessDefinitionOptimizeDto> result = new ArrayList<>(definitionsResult.values());
    result = filterAuthorizedProcessDefinitions(userId, result);
    return result;
  }

  private List<ExtendedProcessDefinitionOptimizeDto>
                filterAuthorizedProcessDefinitions(String userId,
                                                   List<ExtendedProcessDefinitionOptimizeDto> result) {
    result = result
      .stream()
      .filter(def -> sessionService.isAuthorizedToSeeDefinition(userId, def.getKey()))
      .collect(Collectors.toList());
    return result;
  }

  private void addFullDefinition(HashMap<String, ExtendedProcessDefinitionOptimizeDto> definitionsResult, SearchHit hit) {
    ExtendedProcessDefinitionOptimizeDto mapped = mapSearchToProcessDefinition(hit);
    if (definitionsResult.containsKey(mapped.getId())) {
      mapped.setBpmn20Xml(definitionsResult.get(mapped.getId()).getBpmn20Xml());
    }
    definitionsResult.put(mapped.getId(), mapped);
  }

  private void addPartialDefinition(HashMap<String, ExtendedProcessDefinitionOptimizeDto> definitionsResult, SearchHit hit) {
    String id = hit.getSourceAsMap().get(ProcessDefinitionXmlType.PROCESS_DEFINITION_ID).toString();
    String xml = hit.getSourceAsMap().get(ProcessDefinitionXmlType.BPMN_20_XML).toString();
    if (definitionsResult.containsKey(id)) {
      definitionsResult.get(id).setBpmn20Xml(xml);
    } else {
      ExtendedProcessDefinitionOptimizeDto toAdd = new ExtendedProcessDefinitionOptimizeDto();
      toAdd.setId(id);
      toAdd.setBpmn20Xml(xml);
      definitionsResult.put(toAdd.getId(), toAdd);
    }
  }

  private ExtendedProcessDefinitionOptimizeDto mapSearchToProcessDefinition(SearchHit hit) {
    String content = hit.getSourceAsString();
    ExtendedProcessDefinitionOptimizeDto processDefinition = null;
    try {
      processDefinition = objectMapper.readValue(content, ExtendedProcessDefinitionOptimizeDto.class);
    } catch (IOException e) {
      logger.error("Error while reading process definition from elastic search!", e);
    }
    return processDefinition;
  }

  public String getProcessDefinitionXml(String processDefinitionKey, String processDefinitionVersion) {
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto =
      getProcessDefinitionXmlDto(processDefinitionKey, processDefinitionVersion);
    if( processDefinitionXmlDto != null && processDefinitionXmlDto.getBpmn20Xml() != null ){
      return processDefinitionXmlDto.getBpmn20Xml();
    } else {
      String notFoundErrorMessage = "Could not find xml for process definition with key [" + processDefinitionKey +
        "] and version [" + processDefinitionVersion + "]. It is possible that is hasn't been imported yet.";
      logger.error(notFoundErrorMessage);
      throw new NotFoundException(notFoundErrorMessage);
    }
  }

  private String convertToValidVersion(String processDefinitionKey, String processDefinitionVersion) {
    if (ReportConstants.ALL_VERSIONS.equals(processDefinitionVersion)) {
      return getLatestVersionToKey(processDefinitionKey);
    } else {
      return processDefinitionVersion;
    }
  }

  private ProcessDefinitionXmlOptimizeDto getProcessDefinitionXmlDto(String processDefinitionKey, String processDefinitionVersion) {
    processDefinitionVersion = convertToValidVersion(processDefinitionKey, processDefinitionVersion);
    SearchResponse response = esclient.prepareSearch(
        configurationService.getOptimizeIndex(configurationService.getProcessDefinitionXmlType()))
        .setQuery(
          QueryBuilders.boolQuery()
            .must(termQuery(PROCESS_DEFINITION_KEY, processDefinitionKey))
            .must(termQuery(PROCESS_DEFINITION_VERSION, processDefinitionVersion))
        )
        .setSize(1)
        .get();

    ProcessDefinitionXmlOptimizeDto xml = null;
    if (response.getHits().getTotalHits() > 0L) {
      xml = getProcessDefinitionXmlOptimizeDto(response.getHits().getAt(0));
    } else {
      logger.warn("Could not find process definition xml with key [{}] and version [{}]",
        processDefinitionKey,
        processDefinitionVersion
      );
    }
    return xml;
  }

  private static ProcessDefinitionXmlOptimizeDto getProcessDefinitionXmlOptimizeDto(SearchHit response) {
    ProcessDefinitionXmlOptimizeDto xml;
    xml = new ProcessDefinitionXmlOptimizeDto ();

    Map<String, Object> xmlResponse = response.getSourceAsMap();
    xml.setProcessDefinitionId(xmlResponse.get(ProcessDefinitionXmlType.PROCESS_DEFINITION_ID).toString());
    if (xmlResponse.get(ProcessDefinitionXmlType.BPMN_20_XML) != null) {
      xml.setBpmn20Xml(xmlResponse.get(ProcessDefinitionXmlType.BPMN_20_XML).toString());
    }
    if (xmlResponse.get(ProcessDefinitionXmlType.ENGINE) != null) {
      xml.setEngine(xmlResponse.get(ProcessDefinitionXmlType.ENGINE).toString());
    }
    xml.setFlowNodeNames((Map<String, String>) xmlResponse.get(ProcessDefinitionXmlType.FLOW_NODE_NAMES));
    return xml;
  }


  public List<ProcessDefinitionGroupOptimizeDto> getProcessDefinitionsGroupedByKey(String userId) {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = getKeyToProcessDefinitionMap(userId);
    return new ArrayList<>(resultMap.values());
  }

  private String getLatestVersionToKey(String key) {
    SearchResponse response = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessDefinitionType()))
        .setTypes(configurationService.getProcessDefinitionType())
        .setQuery(termQuery(DEFINITION_KEY, key))
        .addSort(VERSION, SortOrder.DESC)
        .setSize(1)
        .get();

    if (response.getHits().getHits().length == 1) {
      Map<String, Object> sourceAsMap = response.getHits().getAt(0).getSourceAsMap();
      if (sourceAsMap.containsKey(VERSION)) {
        return ((Integer) sourceAsMap.get(VERSION)).toString();
      }

    }
    throw new OptimizeRuntimeException("Unable to retrieve latest version for process definition key: " + key);
  }

  private Map<String, ProcessDefinitionGroupOptimizeDto> getKeyToProcessDefinitionMap(String userId) {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = new HashMap<>();
    List<ExtendedProcessDefinitionOptimizeDto> allDefinitions = getProcessDefinitions(userId);
    for (ExtendedProcessDefinitionOptimizeDto process : allDefinitions) {
      String key = process.getKey();
      if (!resultMap.containsKey(key)) {
        resultMap.put(key, constructGroup(process));
      }
      resultMap.get(key).getVersions().add(process);
    }
    resultMap.values().forEach(ProcessDefinitionGroupOptimizeDto::sort);
    return resultMap;
  }


  private ProcessDefinitionGroupOptimizeDto constructGroup(ExtendedProcessDefinitionOptimizeDto process) {
    ProcessDefinitionGroupOptimizeDto result = new ProcessDefinitionGroupOptimizeDto();
    result.setKey(process.getKey());
    return result;
  }

  public FlowNodeNamesResponseDto getFlowNodeNames(FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto) {
    FlowNodeNamesResponseDto result = new FlowNodeNamesResponseDto();
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto =
      getProcessDefinitionXmlDto(
        flowNodeIdsToNamesRequestDto.getProcessDefinitionKey(),
        flowNodeIdsToNamesRequestDto.getProcessDefinitionVersion());
    List<String> nodeIds = flowNodeIdsToNamesRequestDto.getNodeIds();
    if (nodeIds != null && !nodeIds.isEmpty()) {
      for (String id : nodeIds) {
        result.getFlowNodeNames().put(id, processDefinitionXmlDto.getFlowNodeNames().get(id));
      }
    } else {
      result.setFlowNodeNames(processDefinitionXmlDto.getFlowNodeNames());
    }
    return result;
  }
}
