/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.os;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import org.camunda.optimize.service.util.mapper.CustomAuthorizedReportDefinitionDeserializer;
import org.camunda.optimize.service.util.mapper.CustomCollectionEntityDeserializer;
import org.camunda.optimize.service.util.mapper.CustomDefinitionDeserializer;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeDeserializer;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeSerializer;
import org.camunda.optimize.service.util.mapper.CustomReportDefinitionDeserializer;
import org.elasticsearch.client.RequestOptions;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import javax.net.ssl.SSLContext;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

public class OpenSearchClientBuilder {

  private static final Logger log = LoggerFactory.getLogger(OpenSearchClientBuilder.class);

  public static OpenSearchClient buildOpenSearchClientFromConfig(final ConfigurationService configurationService) {
    return new OpenSearchClient(buildOpenSearchTransport(configurationService));
  }

  public static OpenSearchAsyncClient buildOpenSearchAsyncClientFromConfig(final ConfigurationService configurationService) {
    return new OpenSearchAsyncClient(buildOpenSearchTransport(configurationService));
  }

  private static OpenSearchTransport buildOpenSearchTransport(final ConfigurationService configurationService) {
    final HttpHost[] hosts = buildOpenSearchConnectionNodes(configurationService);
    final ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(hosts);

    builder.setHttpClientConfigCallback(
      httpClientBuilder -> {
        configureHttpClient(httpClientBuilder, configurationService);
        return httpClientBuilder;
      });

    builder.setRequestConfigCallback(
      requestConfigBuilder -> {
        setTimeouts(requestConfigBuilder, configurationService);
        return requestConfigBuilder;
      });

    if (StringUtils.isNotBlank(configurationService.getOpenSearchConfiguration().getPathPrefix())) {
      builder.setPathPrefix(configurationService.getOpenSearchConfiguration().getPathPrefix());
    }

    builder.setMapper(new JacksonJsonpMapper(buildObjectMapper()));
    return builder.build();
  }

  private static JavaTimeModule buildJavaTimeModule() {
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(
      OffsetDateTime.class,
      new CustomOffsetDateTimeSerializer(dateTimeFormatter)
    );

    javaTimeModule.addSerializer(Date.class, new DateSerializer(false, new StdDateFormat().withColonInTimeZone(false)));
    javaTimeModule.addDeserializer(
      OffsetDateTime.class,
      new CustomOffsetDateTimeDeserializer(dateTimeFormatter)
    );

    return javaTimeModule;
  }

  private static ObjectMapper buildObjectMapper() {
    ObjectMapper mapper = Jackson2ObjectMapperBuilder
      .json()
      .modules(new Jdk8Module(), buildJavaTimeModule())
      .featuresToDisable(
        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
        DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY,
        SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS,
        SerializationFeature.INDENT_OUTPUT,
        DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY
      )
      .featuresToEnable(
        JsonParser.Feature.ALLOW_COMMENTS,
        MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS
      )
      .build();

    SimpleModule module = new SimpleModule();
    module.addDeserializer(DefinitionOptimizeResponseDto.class, new CustomDefinitionDeserializer(mapper));
    module.addDeserializer(ReportDefinitionDto.class, new CustomReportDefinitionDeserializer(mapper));
    module.addDeserializer(
      AuthorizedReportDefinitionResponseDto.class,
      new CustomAuthorizedReportDefinitionDeserializer(mapper)
    );
    module.addDeserializer(CollectionEntity.class, new CustomCollectionEntityDeserializer(mapper));
    mapper.registerModule(module);

    return mapper;
  }

  public static String getCurrentOSVersion(final OpenSearchClient osClient) throws IOException {
    return osClient.info().version().number();
  }

  private static org.apache.hc.core5.http.HttpHost getHttpHost(DatabaseConnectionNodeConfiguration configuration) {
    String uriConfig = String.format("http://%s:%d", configuration.getHost(), configuration.getHttpPort());
    try {
      final URI uri = new URI(uriConfig);
      return new org.apache.hc.core5.http.HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    } catch (URISyntaxException e) {
      throw new OptimizeRuntimeException("Error in url: " + uriConfig, e);
    }
  }

  private static HttpAsyncClientBuilder setupAuthentication(
    final HttpAsyncClientBuilder builder, ConfigurationService configurationService) {
    String username = configurationService.getOpenSearchConfiguration().getSecurityUsername();
    String password = configurationService.getOpenSearchConfiguration().getSecurityPassword();
    if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
      log.warn("Username and/or password for are empty. Basic authentication for OpenSearch is not used.");
      return builder;
    }

    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
      new AuthScope(getHttpHost(configurationService.getOpenSearchConfiguration().getFirstConnectionNode())),
      new UsernamePasswordCredentials(
        username, password.toCharArray())
    );

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
  }

  private static void setupSSLContext(
    HttpAsyncClientBuilder httpAsyncClientBuilder, ConfigurationService configurationService) {
    try {
      final ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create();
      tlsStrategyBuilder.setSslContext(getSSLContext(configurationService));
      if (Boolean.TRUE.equals(configurationService.getOpenSearchConfiguration().getSkipHostnameVerification())) {
        tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }

      final TlsStrategy tlsStrategy = tlsStrategyBuilder.build();
      final PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

      httpAsyncClientBuilder.setConnectionManager(connectionManager);

    } catch (Exception e) {
      log.error("Error in setting up SSLContext", e);
    }
  }

  private static SSLContext getSSLContext(ConfigurationService configurationService)

    throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    final KeyStore truststore = loadCustomTrustStore(configurationService);
    final org.apache.http.ssl.TrustStrategy trustStrategy =
      Boolean.TRUE.equals(configurationService.getOpenSearchConfiguration()
                            .getSecuritySslSelfSigned()) ? new TrustSelfSignedStrategy() : null;
    if (truststore.size() > 0) {
      return SSLContexts.custom().loadTrustMaterial(truststore, trustStrategy).build();
    } else {
      // default if custom truststore is empty
      return SSLContext.getDefault();
    }
  }

  private static KeyStore loadCustomTrustStore(ConfigurationService configurationService) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);
      // load custom es server certificate if configured
      setCertificateInTrustStore(trustStore, configurationService);

      return trustStore;
    } catch (Exception e) {
      final String message =
        "Could not create certificate trustStore for the secured OpenSearch Connection!";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static void setCertificateInTrustStore(final KeyStore trustStore,
                                                 final ConfigurationService configurationService) {
    final String serverCertificate = configurationService.getOpenSearchConfiguration().getSecuritySSLCertificate();
    if (serverCertificate != null) {
      try {
        final Certificate cert = loadCertificateFromPath(serverCertificate);
        trustStore.setCertificateEntry("opensearch-host", cert);
      } catch (Exception e) {
        final String message =
          "Could not load configured server certificate for the secured OpenSearch Connection!";
        throw new OptimizeRuntimeException(message, e);
      }
    }

    // load trusted CA certificates
    int caCertificateCounter = 0;
    for (String caCertificatePath : configurationService.getOpenSearchConfiguration().getSecuritySSLCertificateAuthorities()) {
      try {
        Certificate cert = loadCertificateFromPath(caCertificatePath);
        trustStore.setCertificateEntry("custom-opensearch-ca-" + caCertificateCounter, cert);
        caCertificateCounter++;
      } catch (Exception e) {
        String message = "Could not load CA authority certificate for the secured OpenSearch Connection!";
        throw new OptimizeConfigurationException(message, e);
      }
    }
  }

  private static Certificate loadCertificateFromPath(final String certificatePath)
    throws IOException, CertificateException {
    final Certificate cert;
    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(certificatePath))) {
      final CertificateFactory cf = CertificateFactory.getInstance("X.509");

      if (bis.available() > 0) {
        cert = cf.generateCertificate(bis);
        log.debug("Found certificate: {}", cert);
      } else {
        throw new OptimizeRuntimeException(
          "Could not load certificate from file, file is empty. File: " + certificatePath);
      }
    }
    return cert;
  }
  private static org.apache.hc.core5.http.HttpHost[] buildOpenSearchConnectionNodes(ConfigurationService configurationService) {
    return configurationService.getOpenSearchConfiguration().getConnectionNodes()
      .stream()
      .map(OpenSearchClientBuilder::getHttpHost)
      .toArray(HttpHost[]::new);
  }

  private static HttpAsyncClientBuilder configureHttpClient(HttpAsyncClientBuilder httpAsyncClientBuilder,
                                                            ConfigurationService configurationService) {
    setupAuthentication(httpAsyncClientBuilder, configurationService);
    if (Boolean.TRUE.equals(configurationService.getOpenSearchConfiguration().getSecuritySSLEnabled())) {
      setupSSLContext(httpAsyncClientBuilder, configurationService);
    }
    return httpAsyncClientBuilder;
  }

  private static RequestConfig.Builder setTimeouts(
    final RequestConfig.Builder builder, final ConfigurationService configurationService) {
    builder.setResponseTimeout(Timeout.ofMilliseconds(0));
    builder.setConnectionRequestTimeout(Timeout.ofMilliseconds(
      configurationService.getOpenSearchConfiguration().getConnectionTimeout()));
    builder.setConnectTimeout(Timeout.ofMilliseconds(
      configurationService.getOpenSearchConfiguration().getConnectionTimeout()));
    return builder;
  }

}
