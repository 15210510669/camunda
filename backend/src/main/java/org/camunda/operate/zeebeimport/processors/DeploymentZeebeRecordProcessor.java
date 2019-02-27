/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.processors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.record.value.DeploymentRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.deployment.DeployedWorkflow;
import io.zeebe.exporter.record.value.deployment.DeploymentResource;
import io.zeebe.exporter.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;

@Component
public class DeploymentZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentZeebeRecordProcessor.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private final static Set<String> STATES = new HashSet<>();
  static {
    STATES.add(DeploymentIntent.CREATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private WorkflowIndex workflowIndex;

  public void processDeploymentRecord(Record record, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    final String intentStr = record.getMetadata().getIntent().name();

    if (STATES.contains(intentStr)) {
      DeploymentRecordValueImpl recordValue = (DeploymentRecordValueImpl)record.getValue();
      Map<String, DeploymentResource> resources = resourceToMap(recordValue.getResources());
      for (DeployedWorkflow workflow : recordValue.getDeployedWorkflows()) {
        persistWorkflow(workflow, resources, record, bulkRequestBuilder);
      }
    }

  }

  private void persistWorkflow(DeployedWorkflow workflow, Map<String, DeploymentResource> resources, Record record, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    String resourceName = workflow.getResourceName();
    DeploymentResource resource = resources.get(resourceName);

    final WorkflowEntity workflowEntity = createEntity(workflow, resource);
    workflowEntity.setKey(record.getKey());
    logger.debug("Workflow: id {}, bpmnProcessId {}", workflowEntity.getId(), workflowEntity.getBpmnProcessId());

    try {

      //find workflow instances with empty workflow name and version
      //FIXME
      //        final List<String> workflowInstanceIds = workflowInstanceReader.queryWorkflowInstancesWithEmptyWorkflowVersion(entity.getKey());
      //        for (String workflowInstanceId : workflowInstanceIds) {
      //          Map<String, Object> updateFields = new HashMap<>();
      //          updateFields.put(IncidentTemplate.WORKFLOW_NAME, entity.getName());
      //          updateFields.put(IncidentTemplate.WORKFLOW_VERSION, entity.getVersion());
      //          bulkRequestBuilder.add(esClient
      //            .prepareUpdate(workflowInstanceTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, workflowInstanceId)
      //            .setDoc(updateFields));
      //        }

      bulkRequestBuilder.add(
        esClient
          .prepareIndex(workflowIndex.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, workflowEntity.getId())
          .setSource(objectMapper.writeValueAsString(workflowEntity), XContentType.JSON)
      );
    } catch (JsonProcessingException e) {
      logger.error("Error preparing the query to insert workflow", e);
      throw new PersistenceException(String.format("Error preparing the query to insert workflow [%s]", workflowEntity.getId()), e);
    }
  }

  private WorkflowEntity createEntity(DeployedWorkflow workflow, DeploymentResource resource) {
    WorkflowEntity workflowEntity = new WorkflowEntity();

    workflowEntity.setId(String.valueOf(workflow.getWorkflowKey()));
    workflowEntity.setBpmnProcessId(workflow.getBpmnProcessId());
    workflowEntity.setVersion(workflow.getVersion());

    ResourceType resourceType = resource.getResourceType();
    if (resourceType != null && resourceType.equals(ResourceType.BPMN_XML)) {
      byte[] byteArray = resource.getResource();

      String bpmn = new String(byteArray, CHARSET);
      workflowEntity.setBpmnXml(bpmn);

      String resourceName = resource.getResourceName();
      workflowEntity.setResourceName(resourceName);

      InputStream is = new ByteArrayInputStream(byteArray);
      final WorkflowEntity diagramData = extractDiagramData(is);
      workflowEntity.setName(diagramData.getName());
    }

    return workflowEntity;
  }

  private Map<String, DeploymentResource> resourceToMap(List<DeploymentResource> resources) {
    return resources.stream().collect(Collectors.toMap(DeploymentResource::getResourceName, Function.identity()));
  }

  public WorkflowEntity extractDiagramData(InputStream xmlInputStream) {
    SAXParser saxParser = getSAXParser();
    BpmnXmlParserHandler handler = new BpmnXmlParserHandler();

    try {
      saxParser.parse(xmlInputStream, handler);
      return handler.getWorkflowEntity();
    } catch (SAXException | IOException e) {
      // just return null
    }

    return null;
  }

  @Bean
  public SAXParser getSAXParser() {
    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      return saxParserFactory.newSAXParser();
    } catch (ParserConfigurationException | SAXException e) {
      logger.error("Error creating SAXParser", e);
      throw new RuntimeException(e);
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    WorkflowEntity workflowEntity = new WorkflowEntity();

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (localName.equalsIgnoreCase("process")) {
        if (attributes.getValue("name") != null) {
          workflowEntity.setName(attributes.getValue("name"));
        }
      }
    }

    public WorkflowEntity getWorkflowEntity() {
      return workflowEntity;
    }
  }
}
