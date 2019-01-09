package org.camunda.optimize.service.license;

import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.camunda.bpm.licensecheck.OptimizeLicenseKey;
import org.camunda.optimize.dto.optimize.query.LicenseInformationDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.LicenseType.LICENSE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LICENSE_TYPE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public class LicenseManager {

  private static final String OPTIMIZE_LICENSE_FILE = "OptimizeLicense.txt";
  private static final Logger logger = LoggerFactory.getLogger(LicenseManager.class);
  private final String licenseDocumentId = "license";
  private final RestHighLevelClient esClient;
  private LicenseKey licenseKey = new OptimizeLicenseKey();
  private String optimizeLicense;

  @Autowired
  public LicenseManager(RestHighLevelClient esClient) {
    this.esClient = esClient;
  }

  @PostConstruct
  public void init() {
    optimizeLicense = retrieveStoredOptimizeLicense();
    if (optimizeLicense == null) {
      try {
        optimizeLicense = readFileToString();
        storeLicense(optimizeLicense);
      } catch (Exception ignored) {
        // nothing to do here
      }
    }
  }

  private String readFileToString() throws IOException {
    InputStream inputStream = this.getClass()
      .getClassLoader()
      .getResourceAsStream(LicenseManager.OPTIMIZE_LICENSE_FILE);
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = inputStream.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return result.toString(StandardCharsets.UTF_8.name());
  }

  public void storeLicense(String licenseAsString) throws OptimizeException {
    XContentBuilder builder;
    try {
      builder = jsonBuilder()
        .startObject()
        .field(LICENSE, licenseAsString)
        .endObject();
    } catch (IOException exception) {
      throw new OptimizeException("Could not parse given license. Please check the encoding!");
    }

    IndexRequest request = new IndexRequest(
      getOptimizeIndexAliasForType(LICENSE_TYPE),
      LICENSE_TYPE,
      licenseDocumentId
    )
      .source(builder)
      .setRefreshPolicy(IMMEDIATE);

    IndexResponse indexResponse;
    try {
      indexResponse = esClient.index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = "Could not store license in Elasticsearch. Maybe Optimize is not connected to Elasticsearch?";
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    boolean licenseWasStored = indexResponse.getShardInfo().getFailed() == 0;
    if (licenseWasStored) {
      optimizeLicense = licenseAsString;
    } else {
      StringBuilder reason = new StringBuilder();
      for (ReplicationResponse.ShardInfo.Failure failure :
        indexResponse.getShardInfo().getFailures()) {
        reason.append(failure.reason()).append("\n");
      }
      String errorMessage = String.format("Could not store license to Elasticsearch. Reason: %s", reason.toString());
      logger.error(errorMessage);
      throw new OptimizeException(errorMessage);
    }
  }

  private String retrieveLicense() throws InvalidLicenseException {
    if (optimizeLicense == null) {
      throw new InvalidLicenseException("No license stored in Optimize. Please provide a valid Optimize license");
    }
    return optimizeLicense;
  }

  private String retrieveStoredOptimizeLicense() {
    logger.debug("Retrieving stored optimize license!");
    GetRequest getRequest =
      new GetRequest(getOptimizeIndexAliasForType(LICENSE_TYPE), LICENSE_TYPE, licenseDocumentId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = "Could not retrieve license from Elasticsearch.";
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    String licenseAsString = null;
    if (getResponse.isExists()) {
      licenseAsString = getResponse.getSource().get(LICENSE).toString();
    }
    return licenseAsString;
  }

  public LicenseInformationDto validateOptimizeLicense(String licenseAsString) throws InvalidLicenseException {
    if (licenseAsString == null) {
      throw new InvalidLicenseException("Could not validate given license. Please try to provide another license!");
    }
    licenseKey.createLicenseKey(licenseAsString);
    licenseKey.validate();
    return licenseKeyToDto(licenseKey);
  }

  private LicenseInformationDto licenseKeyToDto(LicenseKey licenseKey) {
    LicenseInformationDto dto = new LicenseInformationDto();
    dto.setCustomerId(licenseKey.getCustomerId());
    dto.setUnlimited(licenseKey.isUnlimited());
    if (!licenseKey.isUnlimited()) {
      dto.setValidUntil(OffsetDateTime.ofInstant(licenseKey.getValidUntil().toInstant(), ZoneId.systemDefault()));
    }
    return dto;
  }

  public LicenseInformationDto validateLicenseStoredInOptimize() throws InvalidLicenseException {
    String license = retrieveLicense();
    return validateOptimizeLicense(license);
  }

  public void setOptimizeLicense(String optimizeLicense) {
    this.optimizeLicense = optimizeLicense;
  }

  public void resetLicenseFromFile() {
    this.optimizeLicense = null;
  }

}
