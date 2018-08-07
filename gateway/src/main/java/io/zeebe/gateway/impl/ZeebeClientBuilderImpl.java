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
package io.zeebe.gateway.impl;

import static io.zeebe.gateway.ClientProperties.DEFAULT_JOB_TIMEOUT;
import static io.zeebe.gateway.ClientProperties.DEFAULT_JOB_WORKER_NAME;
import static io.zeebe.gateway.ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE;
import static io.zeebe.gateway.ClientProperties.DEFAULT_TOPIC;
import static io.zeebe.gateway.ClientProperties.REQUEST_BLOCKTIME_MILLIS;
import static io.zeebe.gateway.ClientProperties.SENDBUFFER_SIZE;

import io.zeebe.gateway.ClientProperties;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.ZeebeClientBuilder;
import io.zeebe.gateway.ZeebeClientConfiguration;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import java.util.Properties;

public class ZeebeClientBuilderImpl implements ZeebeClientBuilder, ZeebeClientConfiguration {
  private String brokerContactPoint = "127.0.0.1:26501";
  private Duration requestTimeout = Duration.ofSeconds(15);
  private Duration requestBlocktime = Duration.ofSeconds(15);
  private int sendBufferSize = 2;
  private int numManagementThreads = 1;
  private int numSubscriptionExecutionThreads = 1;
  private int topicSubscriptionBufferSize = 1024;
  private int jobSubscriptionBufferSize = 32;
  private Duration tcpChannelKeepAlivePeriod;
  private ActorClock actorClock;
  private String defaultJobWorkerName = "default";
  private Duration defaultJobTimeout = Duration.ofMinutes(5);
  private Duration defaultMessageTimeToLive = Duration.ofHours(1);
  private String defaultTopic = Protocol.DEFAULT_TOPIC;

  @Override
  public String getBrokerContactPoint() {
    return brokerContactPoint;
  }

  @Override
  public ZeebeClientBuilder brokerContactPoint(String contactPoint) {
    this.brokerContactPoint = contactPoint;
    return this;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  @Override
  public ZeebeClientBuilder requestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public Duration getRequestBlocktime() {
    return requestBlocktime;
  }

  @Override
  public ZeebeClientBuilder requestBlocktime(Duration requestBlockTime) {
    this.requestBlocktime = requestBlockTime;
    return this;
  }

  @Override
  public int getSendBufferSize() {
    return sendBufferSize;
  }

  @Override
  public ZeebeClientBuilder sendBufferSize(int sendBufferSize) {
    this.sendBufferSize = sendBufferSize;
    return this;
  }

  @Override
  public int getNumManagementThreads() {
    return numManagementThreads;
  }

  @Override
  public ZeebeClientBuilder numManagementThreads(int numManagementThreads) {
    this.numManagementThreads = numManagementThreads;
    return this;
  }

  @Override
  public int getNumSubscriptionExecutionThreads() {
    return numSubscriptionExecutionThreads;
  }

  @Override
  public ZeebeClientBuilder numSubscriptionExecutionThreads(int numSubscriptionThreads) {
    this.numSubscriptionExecutionThreads = numSubscriptionThreads;
    return this;
  }

  @Override
  public int getDefaultTopicSubscriptionBufferSize() {
    return topicSubscriptionBufferSize;
  }

  @Override
  public ZeebeClientBuilder defaultTopicSubscriptionBufferSize(int numberOfRecords) {
    this.topicSubscriptionBufferSize = numberOfRecords;
    return this;
  }

  @Override
  public int getDefaultJobSubscriptionBufferSize() {
    return jobSubscriptionBufferSize;
  }

  @Override
  public ZeebeClientBuilder defaultJobSubscriptionBufferSize(int numberOfJobs) {
    this.jobSubscriptionBufferSize = numberOfJobs;
    return this;
  }

  @Override
  public Duration getTcpChannelKeepAlivePeriod() {
    return tcpChannelKeepAlivePeriod;
  }

  @Override
  public ZeebeClientBuilder tcpChannelKeepAlivePeriod(Duration tcpChannelKeepAlivePeriod) {
    this.tcpChannelKeepAlivePeriod = tcpChannelKeepAlivePeriod;
    return this;
  }

  public ActorClock getActorClock() {
    return actorClock;
  }

  public ZeebeClientBuilder setActorClock(ActorClock actorClock) {
    this.actorClock = actorClock;
    return this;
  }

  @Override
  public String getDefaultJobWorkerName() {
    return defaultJobWorkerName;
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return defaultJobTimeout;
  }

  @Override
  public ZeebeClientBuilder defaultJobWorkerName(String workerName) {
    this.defaultJobWorkerName = workerName;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobTimeout(Duration timeout) {
    this.defaultJobTimeout = timeout;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultMessageTimeToLive(Duration timeToLive) {
    this.defaultMessageTimeToLive = timeToLive;
    return null;
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return defaultMessageTimeToLive;
  }

  @Override
  public ZeebeClientBuilder defaultTopic(String topic) {
    this.defaultTopic = topic;
    return this;
  }

  @Override
  public String getDefaultTopic() {
    return defaultTopic;
  }

  @Override
  public ZeebeClient build() {
    return new ZeebeClientImpl(this, actorClock);
  }

  @Override
  public ZeebeClientBuilder withProperties(Properties properties) {
    if (properties.containsKey(ClientProperties.BROKER_CONTACTPOINT)) {
      brokerContactPoint(properties.getProperty(ClientProperties.BROKER_CONTACTPOINT));
    }
    if (properties.containsKey(ClientProperties.REQUEST_TIMEOUT_SEC)) {
      requestTimeout(
          Duration.ofSeconds(
              Integer.parseInt(properties.getProperty(ClientProperties.REQUEST_TIMEOUT_SEC))));
    }
    if (properties.containsKey(REQUEST_BLOCKTIME_MILLIS)) {
      requestBlocktime(
          Duration.ofMillis(Integer.parseInt(properties.getProperty(REQUEST_BLOCKTIME_MILLIS))));
    }
    if (properties.containsKey(SENDBUFFER_SIZE)) {
      sendBufferSize(Integer.parseInt(properties.getProperty(SENDBUFFER_SIZE)));
    }
    if (properties.containsKey(ClientProperties.MANAGEMENT_THREADS)) {
      numManagementThreads(
          Integer.parseInt(properties.getProperty(ClientProperties.MANAGEMENT_THREADS)));
    }
    if (properties.containsKey(ClientProperties.TCP_CHANNEL_KEEP_ALIVE_PERIOD)) {
      tcpChannelKeepAlivePeriod(
          Duration.ofMillis(
              Long.parseLong(
                  properties.getProperty(ClientProperties.TCP_CHANNEL_KEEP_ALIVE_PERIOD))));
    }
    if (properties.containsKey(ClientProperties.SUBSCRIPTION_EXECUTION_THREADS)) {
      numSubscriptionExecutionThreads(
          Integer.parseInt(
              properties.getProperty(ClientProperties.SUBSCRIPTION_EXECUTION_THREADS)));
    }
    if (properties.containsKey(ClientProperties.TOPIC_SUBSCRIPTION_BUFFER_SIZE)) {
      defaultTopicSubscriptionBufferSize(
          Integer.parseInt(
              properties.getProperty(ClientProperties.TOPIC_SUBSCRIPTION_BUFFER_SIZE)));
    }
    if (properties.containsKey(ClientProperties.JOB_SUBSCRIPTION_BUFFER_SIZE)) {
      defaultJobSubscriptionBufferSize(
          Integer.parseInt(properties.getProperty(ClientProperties.JOB_SUBSCRIPTION_BUFFER_SIZE)));
    }
    if (properties.containsKey(DEFAULT_JOB_WORKER_NAME)) {
      defaultJobWorkerName(properties.getProperty(DEFAULT_JOB_WORKER_NAME));
    }
    if (properties.containsKey(DEFAULT_JOB_TIMEOUT)) {
      defaultJobTimeout(
          Duration.ofMillis(Long.parseLong(properties.getProperty(DEFAULT_JOB_TIMEOUT))));
    }
    if (properties.containsKey(DEFAULT_MESSAGE_TIME_TO_LIVE)) {
      defaultMessageTimeToLive(
          Duration.ofMillis(Long.parseLong(properties.getProperty(DEFAULT_MESSAGE_TIME_TO_LIVE))));
    }
    if (properties.containsKey(DEFAULT_TOPIC)) {
      defaultTopic(properties.getProperty(DEFAULT_TOPIC));
    }

    return this;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();

    appendProperty(sb, "brokerContactPoint", brokerContactPoint);
    appendProperty(sb, "requestTimeout", requestTimeout);
    appendProperty(sb, "requestBlocktime", requestBlocktime);
    appendProperty(sb, "sendBufferSize", sendBufferSize);
    appendProperty(sb, "numManagementThreads", numManagementThreads);
    appendProperty(sb, "numSubscriptionExecutionThreads", numSubscriptionExecutionThreads);
    appendProperty(sb, "topicSubscriptionBufferSize", topicSubscriptionBufferSize);
    appendProperty(sb, "jobSubscriptionBufferSize", jobSubscriptionBufferSize);
    appendProperty(sb, "tcpChannelKeepAlivePeriod", tcpChannelKeepAlivePeriod);
    appendProperty(sb, "defaultJobWorkerName", defaultJobWorkerName);
    appendProperty(sb, "defaultJobTimeout", defaultJobTimeout);
    appendProperty(sb, "defaultMessageTimeToLive", defaultMessageTimeToLive);
    appendProperty(sb, "defaultTopic", defaultTopic);

    return sb.toString();
  }

  private static void appendProperty(StringBuilder sb, String propertyName, Object value) {
    sb.append(propertyName + ": " + value + "\n");
  }
}
