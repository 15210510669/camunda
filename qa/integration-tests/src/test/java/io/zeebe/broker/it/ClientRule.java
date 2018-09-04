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
package io.zeebe.broker.it;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.junit.Assert.fail;

import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.ZeebeClientBuilder;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.clients.TopicClient;
import io.zeebe.gateway.api.clients.WorkflowClient;
import io.zeebe.gateway.api.commands.Partition;
import io.zeebe.gateway.api.commands.PartitionInfo;
import io.zeebe.gateway.api.commands.Topic;
import io.zeebe.gateway.api.commands.Topics;
import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.record.ValueType;
import io.zeebe.gateway.impl.ZeebeClientBuilderImpl;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.gateway.impl.event.WorkflowInstanceEventImpl;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource {

  private final Consumer<ZeebeClientBuilder> configurator;

  protected ZeebeClientImpl client;
  private ControlledActorClock actorClock = new ControlledActorClock();

  public ClientRule(EmbeddedBrokerRule brokerRule) {
    this(brokerRule, config -> {});
  }

  public ClientRule(EmbeddedBrokerRule brokerRule, Consumer<ZeebeClientBuilder> configurator) {
    this(
        config -> {
          config.brokerContactPoint(brokerRule.getClientAddress().toString());
          configurator.accept(config);
        });
  }

  public ClientRule(ClusteringRule clusteringRule) {
    this(config -> config.brokerContactPoint(clusteringRule.getClientAddress().toString()));
  }

  private ClientRule(final Consumer<ZeebeClientBuilder> configurator) {
    this.configurator = configurator;
  }

  @Override
  protected void before() {
    final ZeebeClientBuilderImpl builder = (ZeebeClientBuilderImpl) ZeebeClient.newClientBuilder();
    configurator.accept(builder);
    client = (ZeebeClientImpl) builder.setActorClock(actorClock).build();
  }

  @Override
  protected void after() {
    client.close();
  }

  public ZeebeClient getClient() {
    return client;
  }

  public void interruptBrokerConnections() {
    final ClientTransport transport = ((ZeebeClientImpl) client).getTransport();
    transport.interruptAllChannels();
  }

  public void waitUntilTopicsExists(final String... topicNames) {
    final List<String> expectedTopicNames = Arrays.asList(topicNames);

    doRepeatedly(this::topicsByName)
        .until(t -> t != null && t.keySet().containsAll(expectedTopicNames));
  }

  public void waitUntilDeploymentIsDone(long key) {
    final AtomicBoolean deploymentFound = new AtomicBoolean(false);

    client
        .topicClient()
        .newSubscription()
        .name("deployment-await")
        .recordHandler(
            record -> {
              if (record.getMetadata().getPartitionId() == 1
                  && record.getMetadata().getValueType() == ValueType.DEPLOYMENT
                  && record.getMetadata().getIntent().equals(DeploymentIntent.CREATED.name())
                  && record.getKey() == key) {
                deploymentFound.compareAndSet(false, true);
              }
            })
        .open();

    doRepeatedly(
            () -> {
              try {
                Thread.sleep(100);
              } catch (Exception ex) {
                fail();
              }
            })
        .until((v) -> deploymentFound.get());
  }

  public Map<String, List<Partition>> topicsByName() {
    final Topics topics = client.newTopicsRequest().send().join();
    return topics
        .getTopics()
        .stream()
        .collect(Collectors.toMap(Topic::getName, Topic::getPartitions));
  }

  public String getDefaultTopic() {
    return client.getConfiguration().getDefaultTopic();
  }

  public int getDefaultPartition() {
    final List<Integer> defaultPartitions =
        doRepeatedly(() -> getPartitions(getDefaultTopic())).until(p -> !p.isEmpty());
    return defaultPartitions.get(0);
  }

  private List<Integer> getPartitions(final String topic) {
    final Topology topology = client.newTopologyRequest().send().join();

    return topology
        .getBrokers()
        .stream()
        .flatMap(i -> i.getPartitions().stream())
        .filter(PartitionInfo::isLeader)
        .filter(p -> p.getTopicName().equals(topic))
        .map(PartitionInfo::getPartitionId)
        .collect(Collectors.toList());
  }

  public ControlledActorClock getActorClock() {
    return actorClock;
  }

  public WorkflowClient getWorkflowClient() {
    return getClient().topicClient().workflowClient();
  }

  public JobClient getJobClient() {
    return getClient().topicClient().jobClient();
  }

  public TopicClient getTopicClient() {
    return getClient().topicClient();
  }

  // Delete after https://github.com/zeebe-io/zeebe/issues/1242 is implemented
  public WorkflowInstanceEvent asWorkflowInstanceEvent(Record record) {
    final WorkflowInstanceEventImpl event = new WorkflowInstanceEventImpl(client.getObjectMapper());

    event.setKey(record.getKey());
    event.setPosition(record.getPosition());
    event.setTimestamp(record.getTimestamp());

    final RecordMetadata metadata = record.getMetadata();
    event.setPartitionId(metadata.getPartitionId());
    event.setIntent(metadata.getIntent());

    final WorkflowInstanceRecordValue value = (WorkflowInstanceRecordValue) record.getValue();
    event.setBpmnProcessId(value.getBpmnProcessId());
    event.setWorkflowKey(value.getWorkflowKey());
    event.setVersion(value.getVersion());
    event.setWorkflowInstanceKey(value.getWorkflowInstanceKey());
    event.setScopeInstanceKey(value.getScopeInstanceKey());
    event.setActivityId(value.getActivityId());
    event.setPayload(value.getPayload());

    return event;
  }
}
