package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/status")
@Component
public class StatusRestService {

  @Autowired
  private StatusCheckingService statusCheckingService;
  /**
   * States if optimize is still importing
   * also includes connection status to Elasticsearch and the Engine
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public StatusWithProgressDto getImportStatus() {
    return statusCheckingService.getConnectionStatusWithProgress();
  }
}