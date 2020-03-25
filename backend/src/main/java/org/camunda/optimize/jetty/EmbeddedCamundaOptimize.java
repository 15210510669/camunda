/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import org.camunda.optimize.CamundaOptimize;
import org.camunda.optimize.jetty.util.LoggingConfigurationReader;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.importing.engine.EngineImportScheduler;
import org.camunda.optimize.service.importing.engine.EngineImportSchedulerFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.websocket.StatusWebSocket;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Jetty embedded server wrapping jersey servlet handler and loading properties from
 * service and environment property files.
 */
public class EmbeddedCamundaOptimize implements CamundaOptimize {

  private static final String PROTOCOL = "http/1.1";

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private static final Logger logger = LoggerFactory.getLogger(EmbeddedCamundaOptimize.class);

  private SpringAwareServletConfiguration jerseyCamundaOptimize;
  private Server jettyServer;

  public EmbeddedCamundaOptimize() {
    this(null);
  }

  public EmbeddedCamundaOptimize(String contextLocation) {
    jerseyCamundaOptimize = new SpringAwareServletConfiguration(contextLocation);
    jettyServer = setUpEmbeddedJetty(jerseyCamundaOptimize);
    disableServerSignature(jettyServer);
  }

  private void disableServerSignature(Server jettyServer) {
    for(Connector y : jettyServer.getConnectors()) {
      for(ConnectionFactory x  : y.getConnectionFactories()) {
        if(x instanceof HttpConnectionFactory) {
          ((HttpConnectionFactory)x).getHttpConfiguration().setSendServerVersion(false);
        }
      }
    }
  }

  private Server setUpEmbeddedJetty(SpringAwareServletConfiguration jerseyCamundaOptimize) {
    LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();

    ConfigurationService configurationService = constructConfigurationService();

    Server jettyServer = initServer(configurationService);

    ServletContextHandler context = jerseyCamundaOptimize.getServletContextHandler();

    jettyServer.setHandler(context);
    initWebSockets(context);

    jettyServer.setRequestLog(new CustomRequestLog(new Slf4jRequestLogWriter(),  CustomRequestLog.EXTENDED_NCSA_FORMAT));

    return jettyServer;
  }

  /**
   * Add javax.websocket support
   */
  private void initWebSockets(ServletContextHandler context) {
    WebSocketServerContainerInitializer.configure(context, (servletContext, serverContainer) -> {
      serverContainer.addEndpoint(StatusWebSocket.class);
    });
  }

  protected ConfigurationService constructConfigurationService() {
    return ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  private Server initServer(ConfigurationService configurationService) {
    String host = configurationService.getContainerHost();
    String keystorePass = configurationService.getContainerKeystorePassword();
    String keystoreLocation = configurationService.getContainerKeystoreLocation();
    Server server = new Server();

    List<Connector> connectors = new ArrayList<>();
    Optional<ServerConnector> connector = initHttpConnector(configurationService, host, server);
    connector.ifPresent(connectors::add);

    ServerConnector sslConnector =
      initHttpsConnector(configurationService, host, keystorePass, keystoreLocation, server);
    connectors.add(sslConnector);

    server.setConnectors(connectors.toArray(new Connector[]{}));

    return server;
  }

  private ServerConnector initHttpsConnector(ConfigurationService configurationService, String host,
                                             String keystorePass, String keystoreLocation, Server server) {
    HttpConfiguration https = new HttpConfiguration();
    https.setSendServerVersion(false);
    https.addCustomizer(new SecureRequestCustomizer());
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setKeyStorePath(keystoreLocation);
    sslContextFactory.setKeyStorePassword(keystorePass);
    sslContextFactory.setKeyManagerPassword(keystorePass);
    // not relevant for server setup but otherwise we get a warning on startup
    // see https://github.com/eclipse/jetty.project/issues/3049
    sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");

    ServerConnector sslConnector = new ServerConnector(
      server,
      new SslConnectionFactory(sslContextFactory, PROTOCOL),
      new HttpConnectionFactory(https)
    );
    sslConnector.setPort(configurationService.getContainerHttpsPort());
    sslConnector.setHost(host);
    return sslConnector;
  }

  private Optional<ServerConnector> initHttpConnector(ConfigurationService configurationService,
                                                      String host,
                                                      Server server) {
    return configurationService.getContainerHttpPort()
      .map(
        httpPort -> {
          ServerConnector connector = new ServerConnector(server);
          connector.setPort(httpPort);
          connector.setHost(host);
          return connector;
        }
      );
  }

  public void startOptimize() throws Exception {
    this.jettyServer.start();
  }

  public boolean isOptimizeStarted() {
    return jettyServer.isStarted();
  }

  public void join() throws InterruptedException {
    jettyServer.join();
  }

  public void destroyOptimize() throws Exception {
    jettyServer.stop();
    jettyServer.destroy();
  }

  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return jerseyCamundaOptimize.getApplicationContext().getBean(ElasticsearchImportJobExecutor.class);
  }

  @Override
  public void startEngineImportSchedulers() {
    boolean oneStarted = false;

    while (!oneStarted) {
      EngineImportSchedulerFactory importSchedulerFactory = getImportSchedulerFactory();
      if (importSchedulerFactory != null) {
        List<EngineImportScheduler> importSchedulers = importSchedulerFactory.getImportSchedulers();
        if (importSchedulers != null) {
          for (EngineImportScheduler scheduler : importSchedulers) {
            scheduler.startImportScheduling();
            oneStarted = true;
          }
        }
      }
    }
  }

  private EngineImportSchedulerFactory getImportSchedulerFactory() {
    return getOptimizeApplicationContext().getBean(EngineImportSchedulerFactory.class);
  }

  @Override
  public void disableEngineImportSchedulers() {
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler.stopImportScheduling();
    }
  }

  @Override
  public void enableEngineImportSchedulers() {
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler.startImportScheduling();
    }
  }

  protected ApplicationContext getOptimizeApplicationContext() {
    return jerseyCamundaOptimize.getApplicationContext();
  }
}
