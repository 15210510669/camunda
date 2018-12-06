package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DecisionDefinitionGroupOptimizeDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getRequestUser;


@Secured
@Path("/decision-definition")
@Component
public class DecisionDefinitionRestService {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionRestService.class);

  private final DecisionDefinitionReader decisionDefinitionReader;

  @Autowired
  public DecisionDefinitionRestService(final DecisionDefinitionReader decisionDefinitionReader) {
    this.decisionDefinitionReader = decisionDefinitionReader;
  }

  /**
   * Retrieves all decision definition stored in Optimize.
   *
   * @param includeXml A parameter saying if the decision definition xml should be included to the response.
   * @return A collection of decision definitions.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Collection<DecisionDefinitionOptimizeDto> getDecisionDefinitions(@Context ContainerRequestContext requestContext,
                                                                          @QueryParam("includeXml") boolean includeXml) {

    String userId = getRequestUser(requestContext);
    return decisionDefinitionReader.getDecisionDefinitions(userId, includeXml);
  }

  /**
   * Retrieves all decision definition stored in Optimize and groups them by key.
   *
   * @return A collection of grouped decision definitions.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/groupedByKey")
  public List<DecisionDefinitionGroupOptimizeDto> getDecisionDefinitionsGroupedByKey(@Context ContainerRequestContext requestContext) {
    String userId = getRequestUser(requestContext);
    return decisionDefinitionReader.getDecisionDefinitionsGroupedByKey(userId);
  }

  /**
   * Get the decision definition xml to a given decision definition key and version.
   * If the version is set to "ALL", the xml of the latest version is returned.
   *
   * @param decisionDefinitionKey     The decision definition key of the desired decision definition xml.
   * @param decisionDefinitionVersion The decision definition version of the desired decision definition xml.
   * @return the decision definition xml requested or json error structure on failure
   */
  @GET
  @Produces(value = {MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @Path("/xml")
  public String getDecisionDefinitionXml(
    @QueryParam("key") String decisionDefinitionKey,
    @QueryParam("version") String decisionDefinitionVersion) {
    final Optional<String> decisionDefinitionXml = decisionDefinitionReader.getDecisionDefinitionXml(
      decisionDefinitionKey, decisionDefinitionVersion
    );
    if (!decisionDefinitionXml.isPresent()) {
      String notFoundErrorMessage = "Could not find xml for decision definition with key [" + decisionDefinitionKey +
        "] and version [" + decisionDefinitionVersion + "]. It is possible that is hasn't been imported yet.";
      logger.error(notFoundErrorMessage);
      throw new NotFoundException(notFoundErrorMessage);
    }
    return decisionDefinitionXml.get();
  }
}
