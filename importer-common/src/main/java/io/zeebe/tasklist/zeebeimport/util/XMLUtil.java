/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.util;

import io.zeebe.tasklist.entities.WorkflowEntity;
import io.zeebe.tasklist.entities.WorkflowFlowNodeEntity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Component
@Configuration
public class XMLUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(XMLUtil.class);

  @Bean
  public SAXParserFactory getSAXParserFactory() {
    final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      saxParserFactory.setFeature(
          "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return saxParserFactory;
    } catch (ParserConfigurationException | SAXException e) {
      LOGGER.error("Error creating SAXParser", e);
      throw new RuntimeException(e);
    }
  }

  public Optional<WorkflowEntity> extractDiagramData(byte[] byteArray) {
    final SAXParserFactory saxParserFactory = getSAXParserFactory();
    final InputStream is = new ByteArrayInputStream(byteArray);
    final BpmnXmlParserHandler handler = new BpmnXmlParserHandler();
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
      return Optional.of(handler.getWorkflowEntity());
    } catch (ParserConfigurationException | SAXException | IOException e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
      return Optional.empty();
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    WorkflowEntity workflowEntity = new WorkflowEntity();

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
        throws SAXException {
      if ("process".equalsIgnoreCase(localName)) {
        if (attributes.getValue("name") != null) {
          workflowEntity.setName(attributes.getValue("name"));
        }
      } else if ("serviceTask".equalsIgnoreCase(localName)) {
        if (attributes.getValue("name") != null) {
          final WorkflowFlowNodeEntity flowNodeEntity =
              new WorkflowFlowNodeEntity(attributes.getValue("id"), attributes.getValue("name"));
          workflowEntity.getFlowNodes().add(flowNodeEntity);
        }
      }
    }

    public WorkflowEntity getWorkflowEntity() {
      return workflowEntity;
    }
  }
}
