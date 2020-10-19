/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe;

import com.google.common.util.concurrent.UncheckedExecutionException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigFactory;
import io.prometheus.client.exporter.HTTPServer;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.Topology;
import io.zeebe.config.AppCfg;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Function;
import me.dinowernli.grpc.prometheus.Configuration;
import me.dinowernli.grpc.prometheus.MonitoringClientInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class App implements Runnable {

  protected static MonitoringClientInterceptor monitoringInterceptor;
  private static final Logger LOG = LoggerFactory.getLogger(App.class);
  private static HTTPServer monitoringServer;

  static void createApp(Function<AppCfg, Runnable> appFactory) {
    final Config config = ConfigFactory.load().getConfig("app");
    LOG.info("Starting app with config: {}", config.root().render());
    final AppCfg appCfg = ConfigBeanFactory.create(config, AppCfg.class);
    startMonitoringServer(appCfg);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> stopMonitoringServer()));
    monitoringInterceptor = MonitoringClientInterceptor.create(Configuration.allMetrics());
    appFactory.apply(appCfg).run();
  }

  private static void startMonitoringServer(AppCfg appCfg) {
    try {
      monitoringServer = new HTTPServer(appCfg.getMonitoringPort());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected static void stopMonitoringServer() {
    if (monitoringServer != null) {
      monitoringServer.stop();
      monitoringServer = null;
    }
  }

  protected void printTopology(ZeebeClient client) {
    while (true) {
      try {
        final Topology topology = client.newTopologyRequest().send().join();
        topology
            .getBrokers()
            .forEach(
                b -> {
                  LOG.info("Broker {} - {}", b.getNodeId(), b.getAddress());
                  b.getPartitions()
                      .forEach(p -> LOG.info("{} - {}", p.getPartitionId(), p.getRole()));
                });
        break;
      } catch (Exception e) {
        // retry
      }
    }
  }

  protected String readVariables(final String payloadPath) {
    try {
      final var classLoader = App.class.getClassLoader();
      try (InputStream fromResource = classLoader.getResourceAsStream(payloadPath)) {
        if (fromResource != null) {
          return tryReadVariables(fromResource);
        }
        // unable to find from resources, try as regular file
        try (InputStream fromFile = new FileInputStream(payloadPath)) {
          return tryReadVariables(fromFile);
        }
      }
    } catch (IOException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  private String tryReadVariables(final InputStream inputStream) throws IOException {
    final StringBuilder stringBuilder = new StringBuilder();
    try (InputStreamReader reader = new InputStreamReader(inputStream)) {
      try (BufferedReader br = new BufferedReader(reader)) {
        String line;
        while ((line = br.readLine()) != null) {
          stringBuilder.append(line).append("\n");
        }
      }
    }
    return stringBuilder.toString();
  }
}
