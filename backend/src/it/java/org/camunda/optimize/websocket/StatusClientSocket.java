package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.util.concurrent.CountDownLatch;

import static org.camunda.optimize.rest.StatusRestServiceIT.ENGINE_ALIAS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Client class to test Web Socket implementation of status
 * report is working. This class will assert 2 properties:
 *
 * 1. import status is false
 * 2. more then one message is received
 */
@ClientEndpoint
public class StatusClientSocket {

  private CountDownLatch latch = new CountDownLatch(1);


  @OnMessage
  public void onText(String message, Session session) throws Exception {
    System.out.println("Message received from server:" + message);
    ObjectMapper objectMapper = new ObjectMapper();
    StatusWithProgressDto dto = objectMapper.readValue(message, StatusWithProgressDto.class);
    assertThat(dto.getIsImporting(), is(notNullValue()));
    assertThat(dto.getIsImporting().get(ENGINE_ALIAS), is(false));
    latch.countDown();
  }

  public CountDownLatch getLatch() {
    return latch;
  }
}
