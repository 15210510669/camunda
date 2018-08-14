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
package io.zeebe.gateway;

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.zeebe.gateway.util.RecordingStreamObserver;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

public class HealthCheckEndpointTest {

  @Rule public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

  @Mock private ResponseMapper responseMapper;

  @Mock private ClusterClient clusterClient;

  private EndpointManager endpointManager;

  private HealthRequest request = HealthRequest.getDefaultInstance();
  private HealthResponse response;
  private RecordingStreamObserver<HealthResponse> streamObserver = new RecordingStreamObserver<>();

  @Before
  public void setUp() {
    initMocks(this);

    endpointManager = new EndpointManager(responseMapper, clusterClient, actorSchedulerRule.get());

    final Partition partition =
        Partition.newBuilder()
            .setPartitionId(5)
            .setRole(PartitionBrokerRole.LEADER)
            .setTopicName(DEFAULT_TOPIC)
            .build();

    this.response =
        HealthResponse.newBuilder()
            .addBrokers(
                BrokerInfo.newBuilder()
                    .setPort(51015)
                    .setHost("localhost")
                    .addPartitions(partition)
                    .build())
            .build();
    when(responseMapper.toResponse(any())).thenReturn(response);
  }

  @Test
  public void healthCheckShouldCheckCorrectInvocation() {
    // given
    final ActorFuture<Topology> responseFuture = CompletableActorFuture.completed(null);
    when(clusterClient.sendRequest(any())).thenReturn(responseFuture);

    // when
    sendRequest();

    // then
    streamObserver.assertValues(response);
  }

  @Test
  public void healthCheckShouldProduceException() {
    // given
    final RuntimeException exception = new RuntimeException("test");
    when(clusterClient.sendRequest(any()))
        .thenReturn(CompletableActorFuture.completedExceptionally(exception));

    // when
    sendRequest();

    // then
    streamObserver.assertErrors(exception);
  }

  private void sendRequest() {
    endpointManager.health(this.request, streamObserver);
    actorSchedulerRule.workUntilDone();
  }
}
