/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql;

import graphql.kickstart.execution.context.DefaultGraphQLContext;
import graphql.kickstart.execution.context.GraphQLContext;
import graphql.kickstart.servlet.context.DefaultGraphQLServletContext;
import graphql.kickstart.servlet.context.DefaultGraphQLWebSocketContext;
import graphql.kickstart.servlet.context.GraphQLServletContextBuilder;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import io.zeebe.tasklist.webapp.security.UserReader;
import java.util.concurrent.CompletableFuture;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TasklistGraphQLContextBuilder implements GraphQLServletContextBuilder {

  public static final String USER_DATA_LOADER = "userDataLoader";

  @Autowired private UserReader userReader;

  @Override
  public GraphQLContext build(HttpServletRequest req, HttpServletResponse response) {
    return DefaultGraphQLServletContext.createServletContext(buildDataLoaderRegistry(), null)
        .with(req)
        .with(response)
        .build();
  }

  @Override
  public GraphQLContext build(Session session, HandshakeRequest request) {
    return DefaultGraphQLWebSocketContext.createWebSocketContext(buildDataLoaderRegistry(), null)
        .with(session)
        .with(request)
        .build();
  }

  @Override
  public GraphQLContext build() {
    return new DefaultGraphQLContext(buildDataLoaderRegistry(), null);
  }

  private DataLoaderRegistry buildDataLoaderRegistry() {
    final DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
    dataLoaderRegistry.register(
        USER_DATA_LOADER,
        new DataLoader<String, UserDTO>(
            usernames ->
                CompletableFuture.supplyAsync(() -> userReader.getUsersByUsernames(usernames))));
    return dataLoaderRegistry;
  }
}
