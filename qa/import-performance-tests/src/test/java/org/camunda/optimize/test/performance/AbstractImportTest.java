package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.util.NamedThreadFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/import-applicationContext.xml"})
public abstract class AbstractImportTest {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final Properties properties = getProperties();

  protected ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  protected EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  protected EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(properties);
  protected long maxImportDurationInMin;
  protected ConfigurationService configurationService;

  @Rule
  public RuleChain chain = RuleChain.outerRule(elasticSearchRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  abstract Properties getProperties();

  @Before
  public void setUp() {
    maxImportDurationInMin = Long.parseLong(properties.getProperty("import.test.max.duration.in.min", "240"));
    elasticSearchRule.disableCleanup();
    configurationService = embeddedOptimizeRule.getConfigurationService();
    configurationService.getCleanupServiceConfiguration().setEnabled(false);
  }

  protected void logStats() {
    try {
      logger.info(
        "The Camunda Platform contains {} process definitions. Optimize: {}",
        (engineDatabaseRule.countProcessDefinitions()),
        elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_DEF_TYPE)
      );
      logger.info(
        "The Camunda Platform contains {} historic process instances. Optimize: {}",
        engineDatabaseRule.countHistoricProcessInstances(),
        elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_INSTANCE_TYPE)
      );
      logger.info(
        "The Camunda Platform contains {} historic variable instances. Optimize: {}",
        engineDatabaseRule.countHistoricVariableInstances(),
        elasticSearchRule.getVariableInstanceCount(configurationService)
      );
      logger.info(
        "The Camunda Platform contains {} historic activity instances. Optimize: {}",
        engineDatabaseRule.countHistoricActivityInstances(),
        elasticSearchRule.getActivityCount(configurationService)
      );
    } catch (SQLException e) {
      logger.error("Failed producing stats", e);
    }
  }

  protected ScheduledExecutorService reportImportProgress() {
    ScheduledExecutorService exec =
      Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(this.getClass().getSimpleName()));
    exec.scheduleAtFixedRate(
      () -> logger.info("Progress of engine import: {}%", computeImportProgress()),
      0,
      5,
      TimeUnit.SECONDS
    );
    return exec;
  }

  private long computeImportProgress() {
    // assumption: we know how many process instances have been generated
    Integer processInstancesImported = elasticSearchRule.getDocumentCountOf(
      ElasticsearchConstants.PROC_INSTANCE_TYPE
    );
    Long totalInstances;
    try {
      totalInstances = Math.max(engineDatabaseRule.countHistoricProcessInstances(), 1L);
      return Math.round(processInstancesImported.doubleValue() / totalInstances.doubleValue() * 100);
    } catch (SQLException e) {
      e.printStackTrace();
      return 0L;
    }
  }

  protected void assertThatEngineAndElasticDataMatch() throws SQLException {
    assertThat(
      "processDefinitionsCount",
      elasticSearchRule.getDocumentCountOf(
        ElasticsearchConstants.PROC_DEF_TYPE
      ),
      is(engineDatabaseRule.countProcessDefinitions())
    );
    assertThat(
      "processInstanceTypeCount",
      elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_INSTANCE_TYPE),
      is(engineDatabaseRule.countHistoricProcessInstances())
    );
    assertThat(
      "variableInstanceCount",
      elasticSearchRule.getVariableInstanceCount(configurationService),
      is(engineDatabaseRule.countHistoricVariableInstances())
    );
    assertThat(
      "historicActivityInstanceCount",
      elasticSearchRule.getActivityCount(configurationService),
      is(engineDatabaseRule.countHistoricActivityInstances())
    );

    assertThat(
      "decisionDefinitionsCount",
      elasticSearchRule.getDocumentCountOf(DECISION_DEFINITION_TYPE),
      is(engineDatabaseRule.countDecisionDefinitions())
    );
    assertThat(
      "decisionInstancesCount",
      elasticSearchRule.getDocumentCountOf(DECISION_INSTANCE_TYPE),
      is(engineDatabaseRule.countHistoricDecisionInstances())
    );
  }
}
