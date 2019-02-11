package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.util.concurrent.CountDownLatch;

import static org.camunda.optimize.websocket.StatusWebSocketIT.ENGINE_ALIAS;
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
  private static final Logger logger = LoggerFactory.getLogger(StatusClientSocket.class);

  private CountDownLatch latch = new CountDownLatch(1);
  private ObjectMapper objectMapper = new ObjectMapper();

  @OnMessage
  public void onText(String message, Session session) throws Exception {
    logger.info("Message received from server:" + message);

    StatusWithProgressDto dto = objectMapper.readValue(message, StatusWithProgressDto.class);

    assertThat(dto.getIsImporting(), is(notNullValue()));
    assertThat(dto.getIsImporting().get(ENGINE_ALIAS), is(true));
    latch.countDown();
  }

  public CountDownLatch getLatch() {
    return latch;
  }
}
