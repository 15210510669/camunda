package org.camunda.optimize.rest.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

@Provider
@Produces(MediaType.APPLICATION_JSON)
@Component
public class OptimizeObjectMapperContextResolver implements ContextResolver<ObjectMapper> {
  private ObjectMapper optimizeObjectMapper;

  @Autowired
  public OptimizeObjectMapperContextResolver(final ObjectMapper objectMapper) {
    this.optimizeObjectMapper = objectMapper;
  }

  public ObjectMapper getContext(Class<?> type) {
    return optimizeObjectMapper;
  }

}
