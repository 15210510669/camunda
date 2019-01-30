package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.dto.optimize.query.report.configuration.ReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.configuration.heatmap_target_value.HeatmapTargetValueEntryDto;
import org.camunda.optimize.dto.optimize.query.report.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.type.report.AbstractReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpgradeSingleProcessReportSettingsStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.getDefaultReportConfigurationAsMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class UpgradeSingleProcessReportConfigurationDataIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "2.3.0";
  private static final String TO_VERSION = "2.4.0";

  private static final String DEFAULT_COLOR = "#1991c8";
  private static final String CUSTOM_COLOR = "#DB3E00";
  private static final AbstractReportType SINGLE_PROCESS_REPORT_TYPE = new SingleProcessReportType();

  // @formatter:off
  private static final String EMPTY_REPORT_220_ID = "0c62ccd7-70ce-44f4-996c-39189e12f0ad";
  private static final String EMPTY_REPORT_230_ID = "c07c84e1-88b0-438a-baad-0808b9d7e1d1";

  // report_{CONFIG_VERSION}_{MIGRATION CASE ID}_{DESCRIPTION}
  private static final String REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_TRUE = "fc22f894-cc3a-4f16-a10c-6958415396ae";
  private static final String REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_FALSE = "76e76e2a-6420-44c2-8ac9-c5e9511e217f";

  private static final String REPORT_230_1_ID_COLOR = "e8cb089a-eba6-43cc-9c78-282f09d192fe";
  private static final String REPORT_230_21_ID_HEAT_RELATIVE_ABSOLUTE = "bdbd4672-7625-445a-bef1-9ffd0ee4529f";
  private static final String REPORT_220_211_ID_HEAT_FLOWNODE_DURATION_TARGETVALUE = "5f2069eb-76f1-47ac-9446-6e760478423c";
  private static final String REPORT_230_211_ID_HEAT_FLOWNODE_DURATION_TARGETVALUE = "0b201336-bdf0-4989-aa74-aa3ad1aec343";
  private static final String REPORT_220_221_ID_LINE_FREQUENCY_TARGETVALUE = "ae00f62b-7e9b-4e24-a913-b2c3946324e8";
  private static final String REPORT_230_221_ID_LINE_FREQUENCY_TARGETVALUE = "8b7addf5-a64f-472c-bcf5-c20a89bbcb60";
  private static final String REPORT_220_221_ID_BAR_FREQUENCY_TARGETVALUE = "3cc33186-35d4-4255-a280-d7e3454249e0";
  private static final String REPORT_230_221_ID_BAR_FREQUENCY_TARGETVALUE = "aa7da17b-56cc-4ffa-a6ad-b0715545b340";
  private static final String REPORT_220_222_ID_LINE_DURATION_TARGETVALUE = "351a088e-b726-4827-bd56-4cea13afa5d1";
  private static final String REPORT_230_222_ID_LINE_DURATION_TARGETVALUE = "b2ae68e5-d0e0-4be2-82fb-a870e6f54145";
  private static final String REPORT_220_222_ID_BAR_DURATION_TARGETVALUE = "ad85c26a-3e26-4cbb-a5a8-c6ce1117f210";
  private static final String REPORT_230_222_ID_BAR_DURATION_TARGETVALUE = "52dd1490-500f-43e1-b0af-c649b1b9f64b";
  private static final String REPORT_220_23_COUNT_PI_FREQUENCY_GROUP_BY_NONE_TARGETVALUE = "004ca65e-c6ef-46e8-979b-b9ef0b9c06de";
  private static final String REPORT_230_23_COUNT_PI_FREQUENCY_GROUP_BY_NONE_TARGETVALUE = "cde03d27-7c50-4a4f-a8b8-528c7aefc928";
  private static final String REPORT_220_24_PI_DURATION_GROUP_BY_NONE_TARGETVALUE = "2e0a3dda-2dce-4943-a468-1cfad680a9b8";
  private static final String REPORT_230_24_PI_DURATION_GROUP_BY_NONE_TARGETVALUE = "6afd9f54-de24-4499-b9d8-c90010ebf3ca";
  // @formatter:on

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(METADATA_TYPE, SINGLE_PROCESS_REPORT_TYPE));

    addVersionToElasticsearch(FROM_VERSION);

    executeBulk("steps/configuration_upgrade/23-single-process-report-bulk");
    executeBulk("steps/configuration_upgrade/22-single-process-report-bulk");
  }

  @Test
  public void xmlStillPresent() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<SingleProcessReportDefinitionDto> reports = getAllSingleProcessReportDefinitionDtos();
    assertThat(reports.size(), is(20));
    reports.forEach(singleProcessReportDefinitionDto -> {
      if (!singleProcessReportDefinitionDto.getId().equals(EMPTY_REPORT_230_ID)
        && !singleProcessReportDefinitionDto.getId().equals(EMPTY_REPORT_220_ID)) {
        assertThat(singleProcessReportDefinitionDto.getData().getConfiguration().getXml(), is(notNullValue()));
      } else {
        assertThat(singleProcessReportDefinitionDto.getData().getConfiguration().getXml(), is(nullValue()));
      }
    });
  }

  @Test
  public void colorFieldMigration() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_1_ID_COLOR).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_21_ID_HEAT_RELATIVE_ABSOLUTE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_211_ID_HEAT_FLOWNODE_DURATION_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_211_ID_HEAT_FLOWNODE_DURATION_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_221_ID_LINE_FREQUENCY_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_221_ID_LINE_FREQUENCY_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_221_ID_BAR_FREQUENCY_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_221_ID_BAR_FREQUENCY_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_222_ID_LINE_DURATION_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_222_ID_LINE_DURATION_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_222_ID_BAR_DURATION_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_222_ID_BAR_DURATION_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_23_COUNT_PI_FREQUENCY_GROUP_BY_NONE_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_23_COUNT_PI_FREQUENCY_GROUP_BY_NONE_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_24_PI_DURATION_GROUP_BY_NONE_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_24_PI_DURATION_GROUP_BY_NONE_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
  }

  @Test
  public void heatMapRelativeAbsoluteMigration22() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    // #3 migration (from 2.2 properties)
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_TRUE).getHideAbsoluteValue(),
      is(false)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_TRUE).getHideRelativeValue(),
      is(false)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_FALSE).getHideAbsoluteValue(),
      is(true)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_FALSE).getHideRelativeValue(),
      is(true)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_TRUE).getAlwaysShowAbsolute(),
      is(true)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_TRUE).getAlwaysShowRelative(),
      is(true)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_FALSE).getAlwaysShowAbsolute(),
      is(false)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_220_3_HEATMAP_FLOWNODE_ALWAYS_SHOW_TOOLTIP_FALSE).getAlwaysShowRelative(),
      is(false)
    );
  }

  @Test
  public void heatMapRelativeAbsoluteMigration23() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_21_ID_HEAT_RELATIVE_ABSOLUTE).getAlwaysShowAbsolute(),
      is(true)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_230_21_ID_HEAT_RELATIVE_ABSOLUTE).getAlwaysShowRelative(),
      is(true)
    );
  }

  @Test
  @Parameters({REPORT_220_211_ID_HEAT_FLOWNODE_DURATION_TARGETVALUE,
    REPORT_230_211_ID_HEAT_FLOWNODE_DURATION_TARGETVALUE})
  public void heatMapTargetValueMigration22(String reportId) throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(reportId);

    assertThat(configuration.getTargetValue(), is(getDefaultReportConfiguration().getTargetValue()));
    assertThat(configuration.getHeatmapTargetValue().getActive(), is(true));
    assertThat(configuration.getHeatmapTargetValue().getValues().size(), is(1));
    final HeatmapTargetValueEntryDto approveInvoiceTargetValue = configuration.getHeatmapTargetValue()
      .getValues()
      .get("approveInvoice");
    assertThat(approveInvoiceTargetValue.getUnit(), is(TargetValueUnit.WEEKS));
    assertThat(approveInvoiceTargetValue.getValue(), is("1"));
  }

  @Test
  @Parameters({REPORT_220_221_ID_LINE_FREQUENCY_TARGETVALUE, REPORT_230_221_ID_LINE_FREQUENCY_TARGETVALUE})
  public void lineFrequencyTargetValue(String reportId) throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(reportId);

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is("1.5"));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(false));
  }

  @Test
  @Parameters({REPORT_220_221_ID_BAR_FREQUENCY_TARGETVALUE, REPORT_230_221_ID_BAR_FREQUENCY_TARGETVALUE})
  public void barFrequencyTargetValue(String reportId) throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(reportId);

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is("1.5"));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(false));

  }

  @Test
  @Parameters({REPORT_220_222_ID_LINE_DURATION_TARGETVALUE, REPORT_230_222_ID_LINE_DURATION_TARGETVALUE})
  public void lineDurationTargetValue(String reportId) throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(reportId);

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is("1"));
    assertThat(configuration.getTargetValue().getDurationChart().getBelow(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getUnit(), is(TargetValueUnit.SECONDS));
  }

  @Test
  @Parameters({REPORT_220_222_ID_BAR_DURATION_TARGETVALUE, REPORT_230_222_ID_BAR_DURATION_TARGETVALUE})
  public void barDurationTargetValue(String reportId) throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(reportId);

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is("1"));
    assertThat(configuration.getTargetValue().getDurationChart().getBelow(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getUnit(), is(TargetValueUnit.SECONDS));
  }

  @Test
  @Parameters({REPORT_220_23_COUNT_PI_FREQUENCY_GROUP_BY_NONE_TARGETVALUE,
    REPORT_230_23_COUNT_PI_FREQUENCY_GROUP_BY_NONE_TARGETVALUE})
  public void countPiFrequencyGroupByNoneTargetValue(String reportId) throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(reportId);

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountProgress().getBaseline(), is("1"));
    assertThat(configuration.getTargetValue().getCountProgress().getTarget(), is("101"));
  }

  @Test
  @Parameters({REPORT_220_24_PI_DURATION_GROUP_BY_NONE_TARGETVALUE,
    REPORT_230_24_PI_DURATION_GROUP_BY_NONE_TARGETVALUE})
  public void piDurationGroupByNoneTargetValue(String reportId) throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(reportId);

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationProgress().getBaseline().getValue(), is("1"));
    assertThat(
      configuration.getTargetValue().getDurationProgress().getBaseline().getUnit(),
      is(TargetValueUnit.SECONDS)
    );
    assertThat(configuration.getTargetValue().getDurationProgress().getTarget().getValue(), is("5"));
    assertThat(configuration.getTargetValue().getDurationProgress().getTarget().getUnit(), is(TargetValueUnit.SECONDS));
  }

  private ReportConfigurationDto getDefaultReportConfiguration() {
    String pathToMapping = "upgrade/main/UpgradeFrom23To24/default-report-configuration.json";
    String reportConfigurationStructureAsJson = SchemaUpgradeUtil.readClasspathFileAsString(pathToMapping);
    ReportConfigurationDto reportConfigurationAsMap;
    try {
      reportConfigurationAsMap = objectMapper.readValue(
        reportConfigurationStructureAsJson,
        ReportConfigurationDto.class
      );
    } catch (IOException e) {
      throw new UpgradeRuntimeException("Could not deserialize default report configuration structure as json!");
    }
    return reportConfigurationAsMap;
  }

  private String getReportIndexAlias() {
    return getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE.getType());
  }

  private UpgradePlan getReportConfigurationUpgradePlan() throws Exception {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new UpgradeSingleProcessReportSettingsStep(getDefaultReportConfigurationAsMap()))
      .build();
  }

  private ReportConfigurationDto getSingleProcessReportDefinitionConfigurationById(final String id) throws IOException {
    final GetResponse reportResponse = restClient.get(
      new GetRequest(getReportIndexAlias(), SINGLE_PROCESS_REPORT_TYPE.getType(), id), RequestOptions.DEFAULT
    );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), SingleProcessReportDefinitionDto.class
    ).getData().getConfiguration();
  }

  private List<SingleProcessReportDefinitionDto> getAllSingleProcessReportDefinitionDtos() throws IOException {
    final SearchResponse searchResponse = restClient.search(
      new SearchRequest(getReportIndexAlias()).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), SingleProcessReportDefinitionDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.toList());
  }


}
