package org.camunda.operate.zeebeimport.util;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.camunda.operate.entities.WorkflowEntity;
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

  private static final Logger logger = LoggerFactory.getLogger(XMLUtil.class);

  @Bean
  public SAXParserFactory getSAXParserFactory() {
    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      saxParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return saxParserFactory;
    } catch (ParserConfigurationException | SAXException e) {
      logger.error("Error creating SAXParser", e);
      throw new RuntimeException(e);
    }
  }

  public Optional<WorkflowEntity> extractDiagramData(byte[] byteArray) {
    SAXParserFactory saxParserFactory = getSAXParserFactory();
    InputStream is = new ByteArrayInputStream(byteArray);
    BpmnXmlParserHandler handler = new BpmnXmlParserHandler();
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
      return Optional.of(handler.getWorkflowEntity());
    } catch (ParserConfigurationException | SAXException | IOException e) {
      logger.warn("Unable to parse diagram: " + e.getMessage(), e);
      return Optional.empty();
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
