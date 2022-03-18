/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ProxyConfiguration;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Optional;

public class ElasticsearchHighLevelRestClientBuilder {

  private static final String HTTP = "http";
  private static final String HTTPS = "https";

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchHighLevelRestClientBuilder.class);

  public static RestHighLevelClient build(ConfigurationService configurationService) {
    if (configurationService.getElasticsearchSecuritySSLEnabled()) {
      return buildHttpsRestClient(configurationService);
    }
    return buildHttpRestClient(configurationService);
  }

  private static RestHighLevelClient buildHttpRestClient(ConfigurationService configurationService) {
    final RestClientBuilder builder = buildDefaultRestClient(configurationService, HTTP);
    return new RestHighLevelClient(builder);
  }

  private static RestHighLevelClient buildHttpsRestClient(ConfigurationService configurationService) {
    try {
      final RestClientBuilder builder = buildDefaultRestClient(configurationService, HTTPS);

      final SSLContext sslContext;
      final KeyStore truststore = loadCustomTrustStore(configurationService);
      if (truststore.size() > 0) {
        sslContext = SSLContexts.custom().loadTrustMaterial(truststore, null).build();
      } else {
        // default if custom truststore is empty
        sslContext = SSLContext.getDefault();
      }

      builder.setHttpClientConfigCallback(createHttpClientConfigCallback(configurationService, sslContext));

      return new RestHighLevelClient(builder);
    } catch (Exception e) {
      String message = "Could not build secured Elasticsearch client.";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static RestClientBuilder.HttpClientConfigCallback createHttpClientConfigCallback(
    final ConfigurationService configurationService) {
    return createHttpClientConfigCallback(configurationService, null);
  }

  /**
   * The clientConfigCallback can be set only once per builder.
   * This method cares about all aspects that need to be considered in its setup.
   *
   * @param configurationService configuration source for the client callback
   * @param sslContext           ssl setup to apply, might be <code>null</code> if there is no ssl setup
   * @return singleton config callback for the elasticsearch rest client
   */
  private static RestClientBuilder.HttpClientConfigCallback createHttpClientConfigCallback(
    final ConfigurationService configurationService,
    final SSLContext sslContext) {
    return httpClientBuilder -> {
      buildCredentialsProviderIfConfigured(configurationService)
        .ifPresent(httpClientBuilder::setDefaultCredentialsProvider);

      httpClientBuilder.setSSLContext(sslContext);


      final ProxyConfiguration proxyConfig = configurationService.getElasticSearchProxyConfig();
      if (proxyConfig.isEnabled()) {
        httpClientBuilder.setProxy(new HttpHost(
          proxyConfig.getHost(), proxyConfig.getPort(), proxyConfig.isSslEnabled() ? "https" : "http"
        ));
      }

      return httpClientBuilder;
    };
  }

  private static RestClientBuilder buildDefaultRestClient(ConfigurationService configurationService, String protocol) {
    final RestClientBuilder restClientBuilder = RestClient.builder(
      buildElasticsearchConnectionNodes(configurationService, protocol))
      .setRequestConfigCallback(
        requestConfigBuilder -> requestConfigBuilder
          .setConnectTimeout(configurationService.getElasticsearchConnectionTimeout())
          .setSocketTimeout(0)
      );

    restClientBuilder.setHttpClientConfigCallback(createHttpClientConfigCallback(configurationService));

    return restClientBuilder;
  }

  private static HttpHost[] buildElasticsearchConnectionNodes(ConfigurationService configurationService,
                                                              String protocol) {
    return configurationService.getElasticsearchConnectionNodes()
      .stream()
      .map(conf -> new HttpHost(
             conf.getHost(),
             conf.getHttpPort(),
             protocol
           )
      )
      .toArray(HttpHost[]::new);
  }

  private static Optional<CredentialsProvider> buildCredentialsProviderIfConfigured(
    final ConfigurationService configurationService) {
    CredentialsProvider credentialsProvider = null;
    if (configurationService.getElasticsearchSecurityUsername() != null
      && configurationService.getElasticsearchSecurityPassword() != null) {
      credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
          configurationService.getElasticsearchSecurityUsername(),
          configurationService.getElasticsearchSecurityPassword()
        )
      );
    } else {
      logger.debug("Elasticsearch username and password not provided, skipping connection credential setup.");
    }
    return Optional.ofNullable(credentialsProvider);
  }

  private static KeyStore loadCustomTrustStore(ConfigurationService configurationService) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);

      // load custom es server certificate if configured
      final String serverCertificate = configurationService.getElasticsearchSecuritySSLCertificate();
      if (serverCertificate != null) {
        try {
          Certificate cert = loadCertificateFromPath(serverCertificate);
          trustStore.setCertificateEntry("elasticsearch-host", cert);
        } catch (Exception e) {
          String message = "Could not load configured server certificate for the secured Elasticsearch Connection!";
          throw new OptimizeConfigurationException(message, e);
        }
      }

      // load trusted CA certificates
      int caCertificateCounter = 0;
      for (String caCertificatePath : configurationService.getElasticsearchSecuritySSLCertificateAuthorities()) {
        try {
          Certificate cert = loadCertificateFromPath(caCertificatePath);
          trustStore.setCertificateEntry("custom-elasticsearch-ca-" + caCertificateCounter, cert);
          caCertificateCounter++;
        } catch (Exception e) {
          String message = "Could not load CA authority certificate for the secured Elasticsearch Connection!";
          throw new OptimizeConfigurationException(message, e);
        }
      }

      return trustStore;
    } catch (Exception e) {
      String message = "Could not create certificate trustStore for the secured Elasticsearch Connection!";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static Certificate loadCertificateFromPath(final String certificatePath)
    throws IOException, CertificateException {
    Certificate cert;
    final FileInputStream fileInputStream = new FileInputStream(certificatePath);
    try (BufferedInputStream bis = new BufferedInputStream(fileInputStream)) {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");

      if (bis.available() > 0) {
        cert = cf.generateCertificate(bis);
        logger.debug("Found certificate: {}", cert);
      } else {
        throw new OptimizeConfigurationException(
          "Could not load certificate from file, file is empty. File: " + certificatePath
        );
      }
    }
    return cert;
  }

}
