package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.OptimizeVersionDto;
import org.camunda.optimize.service.metadata.MetadataService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class MetadataRestServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getOptimizeVersion() {
    // when
    OptimizeVersionDto optimizeVersionDto =
        embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetOptimizeVersionRequest()
            .execute(OptimizeVersionDto.class, 200);

    // then
    assertThat(optimizeVersionDto.getOptimizeVersion(), is(embeddedOptimizeRule.getApplicationContext().getBean(MetadataService.class).getVersion()));
  }
}
