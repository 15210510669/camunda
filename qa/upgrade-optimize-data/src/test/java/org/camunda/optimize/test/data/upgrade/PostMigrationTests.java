package org.camunda.optimize.test.data.upgrade;

import org.camunda.optimize.dto.engine.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class PostMigrationTests {
  private static Client client;
  private static String authHeader;

  @BeforeClass
  public static void init() {
    ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("optimizeDataUpgradeContext.xml");

    OptimizeObjectMapperProvider provider = ctx.getBean(OptimizeObjectMapperProvider.class);

    client = ClientBuilder.newClient().register(provider);
    authenticateDemo();
  }


  @Test
  public void retrieveAllReports() {
    Response response = client.target("http://localhost:8090/api/report")
      .request()
      .cookie("X-Optimize-Authorization", authHeader)
      .get();

    List<Object> objects = response.readEntity(new GenericType<List<Object>>() {
    });
    assertThat(objects.size(), is(8));
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void retrieveDashboards() {
    Response response = client.target("http://localhost:8090/api/dashboard")
      .request()
      .cookie("X-Optimize-Authorization", authHeader)
      .get();

    List<Object> objects = response.readEntity(new GenericType<List<Object>>() {
    });
    assertThat(objects.size(), is(2));
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void retrieveAlerts() {
    Response response = client.target("http://localhost:8090/api/alert")
      .request()
      .cookie("X-Optimize-Authorization", authHeader)
      .get();

    List<Object> objects = response.readEntity(new GenericType<List<Object>>() {
    });
    assertThat(objects.size(), is(1));
    assertThat(response.getStatus(), is(200));
  }

  private static void authenticateDemo() {
    CredentialsDto credentials = new CredentialsDto();
    credentials.setUsername("demo");
    credentials.setPassword("demo");

    Response response = client.target("http://localhost:8090/api/authentication")
      .request().post(Entity.json(credentials));

    authHeader = "Bearer " + response.readEntity(String.class);
  }
}
