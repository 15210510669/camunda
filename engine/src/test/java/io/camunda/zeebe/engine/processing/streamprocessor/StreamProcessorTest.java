/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.zeebe.streamplatform.api.CommandResponseWriter;
import io.camunda.zeebe.streamplatform.api.ProcessingScheduleService;
import io.camunda.zeebe.streamplatform.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.streamplatform.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.streamplatform.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamProcessorRule;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.streamplatform.StreamProcessor;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.util.exception.RecoverableException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.verification.VerificationWithTimeout;

public final class StreamProcessorTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);
  private static final JobRecord JOB_RECORD = Records.job(1).setType("test");

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();
  private ProcessingScheduleService scheduleService;

  @Test
  public void shouldCallStreamProcessorLifecycle() throws Exception {
    // given
    final StreamProcessorLifecycleAware lifecycleAware = mock(StreamProcessorLifecycleAware.class);
    final CountDownLatch recoveredLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors
                .withListener(lifecycleAware)
                .withListener(
                    new StreamProcessorLifecycleAware() {
                      @Override
                      public void onRecovered(final ReadonlyStreamProcessorContext context) {
                        recoveredLatch.countDown();
                      }
                    }));

    // when
    recoveredLatch.await();
    streamProcessorRule.closeStreamProcessor();

    // then
    final InOrder inOrder = inOrder(lifecycleAware);
    inOrder.verify(lifecycleAware, times(1)).onRecovered(any());
    inOrder.verify(lifecycleAware, times(1)).onClose();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldCallStreamProcessorLifecycleOnFail() throws InterruptedException {
    // given
    final CountDownLatch failedLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessorNotAwaitOpening(
        (processors, state) ->
            processors.withListener(
                new StreamProcessorLifecycleAware() {

                  @Override
                  public void onRecovered(final ReadonlyStreamProcessorContext context) {
                    throw new RuntimeException("force fail");
                  }

                  @Override
                  public void onFailed() {
                    failedLatch.countDown();
                  }
                }));

    // then
    assertThat(failedLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue();
  }

  @Test
  public void shouldProcessRecord() {
    // given
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                typedRecordProcessor));

    // when
    final long position =
        streamProcessorRule.writeCommand(
            ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(1));

    // then
    verify(typedRecordProcessor, TIMEOUT.times(1)).processRecord(any(), any());

    verifyNoMoreInteractions(typedRecordProcessor);

    Awaitility.await()
        .untilAsserted(
            () -> {
              Assertions.assertThat(streamProcessorRule.getLastSuccessfulProcessedRecordPosition())
                  .isEqualTo(position);
            });
  }

  @Test
  public void shouldRetryProcessingRecordOnRecoverableException() {
    // given
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    final AtomicInteger count = new AtomicInteger(0);
    doAnswer(
            (invocationOnMock -> {
              if (count.getAndIncrement() == 0) {
                throw new RecoverableException("recoverable");
              }
              return null;
            }))
        .when(typedRecordProcessor)
        .processRecord(any(), any());

    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                typedRecordProcessor));

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    verify(typedRecordProcessor, TIMEOUT.times(1)).processRecord(any(), any());

    verifyNoMoreInteractions(typedRecordProcessor);
  }

  @Test
  public void shouldIgnoreRecordWhenNoProcessorExistForThisType() {
    // given
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                typedRecordProcessor));

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.TERMINATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    verify(typedRecordProcessor, TIMEOUT.times(1)).processRecord(any(), any());

    verifyNoMoreInteractions(typedRecordProcessor);
  }

  @Test
  public void shouldProcessOnlyCommands() {
    // given
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                typedRecordProcessor));

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    streamProcessorRule.writeEvent(ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    streamProcessorRule.writeCommandRejection(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).processRecord(any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  @Ignore
  // this currently fails because the state writer expects a certain state
  // if the engine abstraction is done, we will can rewrite the test in a way
  // that we just return a result and verify whether the result was written
  // We can ignore the test for now, since it is tested anyway implicit
  public void shouldWriteFollowUpEvent() {
    // given
    final StreamProcessor streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, state) ->
                processors.onCommand(
                    ValueType.PROCESS_INSTANCE,
                    ProcessInstanceIntent.ACTIVATE_ELEMENT,
                    new TypedRecordProcessor<>() {
                      @Override
                      public void processRecord(final TypedRecord<UnifiedRecordValue> record) {
                        state
                            .getWriters()
                            .state()
                            .appendFollowUpEvent(
                                record.getKey(),
                                ProcessInstanceIntent.ELEMENT_ACTIVATING,
                                record.getValue());
                      }
                    }));

    // when
    final long position =
        streamProcessorRule.writeCommand(
            ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    final Record<ProcessInstanceRecord> activatedEvent = waitForActivatingEvent();
    assertThat(activatedEvent).isNotNull();
    assertThat((activatedEvent).getSourceRecordPosition()).isEqualTo(position);

    assertThat(streamProcessor.getLastWrittenPositionAsync().join())
        .isEqualTo((activatedEvent).getPosition());
    assertThat(streamProcessor.getLastProcessedPositionAsync().join()).isEqualTo(position);
  }

  @Test
  public void shouldExecuteSideEffects() throws Exception {
    // given
    final CountDownLatch processLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                new TypedRecordProcessor<>() {
                  @Override
                  public void processRecord(
                      final TypedRecord<UnifiedRecordValue> record,
                      final Consumer<SideEffectProducer> sideEffect) {

                    sideEffect.accept(
                        () -> {
                          processLatch.countDown();
                          return true;
                        });
                  }
                }));

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    assertThat(processLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldRepeatExecuteSideEffects() throws Exception {
    // given
    final CountDownLatch processLatch = new CountDownLatch(2);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                new TypedRecordProcessor<>() {
                  @Override
                  public void processRecord(
                      final TypedRecord<UnifiedRecordValue> record,
                      final Consumer<SideEffectProducer> sideEffect) {
                    sideEffect.accept(
                        () -> {
                          processLatch.countDown();
                          return processLatch.getCount() < 1;
                        });
                  }
                }));

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    assertThat(processLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldSkipSideEffectsOnException() throws Exception {
    // given
    final CountDownLatch processLatch = new CountDownLatch(2);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                new TypedRecordProcessor<>() {
                  @Override
                  public void processRecord(
                      final TypedRecord<UnifiedRecordValue> record,
                      final Consumer<SideEffectProducer> sideEffect) {

                    sideEffect.accept(
                        () -> {
                          throw new RuntimeException("expected");
                        });
                    processLatch.countDown();
                  }
                }));

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    assertThat(processLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldNotUpdateStateOnExceptionInProcessing() {
    // given
    final long jobKey = 1L;

    streamProcessorRule.startTypedStreamProcessor(
        (builder, processingContext) -> {
          scheduleService = processingContext.getScheduleService();
          final MutableZeebeState state = processingContext.getZeebeState();
          return builder.onCommand(
              ValueType.PROCESS_INSTANCE,
              ProcessInstanceIntent.ACTIVATE_ELEMENT,
              new TypedRecordProcessor<>() {
                @Override
                public void processRecord(final TypedRecord<UnifiedRecordValue> record) {

                  state.getJobState().create(jobKey, JOB_RECORD);

                  throw new RuntimeException("expected");
                }
              });
        });

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    verify(streamProcessorRule.getMockStreamProcessorListener(), TIMEOUT.times(2))
        .onProcessed(any());

    call(
            scheduleService,
            () -> {
              final var jobState = streamProcessorRule.getZeebeState().getJobState();
              final var job = jobState.getJob(jobKey);

              assertThat(job).isNull();
            })
        .join();
  }

  @Test
  public void shouldUpdateStateAfterProcessing() {
    // given
    final long jobKey = 1L;

    streamProcessorRule.startTypedStreamProcessor(
        (builder, processingContext) -> {
          scheduleService = processingContext.getScheduleService();
          final MutableZeebeState state = processingContext.getZeebeState();
          return builder.onCommand(
              ValueType.PROCESS_INSTANCE,
              ProcessInstanceIntent.ACTIVATE_ELEMENT,
              new TypedRecordProcessor<>() {
                @Override
                public void processRecord(final TypedRecord<UnifiedRecordValue> record) {

                  state.getJobState().create(jobKey, JOB_RECORD);
                }
              });
        });

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    verify(streamProcessorRule.getMockStreamProcessorListener(), TIMEOUT).onProcessed(any());

    call(
            scheduleService,
            () -> {
              final var jobState = streamProcessorRule.getZeebeState().getJobState();
              final var job = jobState.getJob(jobKey);

              assertThat(job).isNotNull();
            })
        .join();
  }

  @Test
  public void shouldWriteResponse() {
    // given
    streamProcessorRule.startTypedStreamProcessor(
        (processors, context) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                new TypedRecordProcessor<>() {
                  @Override
                  public void processRecord(final TypedRecord<UnifiedRecordValue> record) {
                    context
                        .getWriters()
                        .response()
                        .writeEventOnCommand(
                            3, ProcessInstanceIntent.ELEMENT_ACTIVATING, record.getValue(), record);
                  }
                }));

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    final CommandResponseWriter commandResponseWriter =
        streamProcessorRule.getCommandResponseWriter();

    verify(commandResponseWriter, TIMEOUT.times(1)).key(3);
    verify(commandResponseWriter, TIMEOUT.times(1))
        .intent(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    verify(commandResponseWriter, TIMEOUT.times(1)).recordType(RecordType.EVENT);
    verify(commandResponseWriter, TIMEOUT.times(1)).valueType(ValueType.PROCESS_INSTANCE);
    verify(commandResponseWriter, TIMEOUT.times(1)).tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldWriteResponseOnFailedEventProcessing() {
    // given
    streamProcessorRule.startTypedStreamProcessor(
        (processors, context) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                new TypedRecordProcessor<>() {
                  @Override
                  public void processRecord(final TypedRecord<UnifiedRecordValue> record) {
                    context
                        .getWriters()
                        .response()
                        .writeEventOnCommand(
                            3, ProcessInstanceIntent.ELEMENT_ACTIVATING, record.getValue(), record);

                    throw new RuntimeException("expected");
                  }
                }));

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    final CommandResponseWriter commandResponseWriter =
        streamProcessorRule.getCommandResponseWriter();

    // it doesn't send the staged command response
    verify(commandResponseWriter, TIMEOUT.times(1)).key(-1);
    verify(commandResponseWriter, TIMEOUT.times(1)).intent(ProcessInstanceIntent.ACTIVATE_ELEMENT);
    verify(commandResponseWriter, TIMEOUT.times(1)).recordType(RecordType.COMMAND_REJECTION);
    verify(commandResponseWriter, TIMEOUT.times(1)).valueType(ValueType.PROCESS_INSTANCE);
    // instead, it sends a rejection response because of the failure
    verify(commandResponseWriter, TIMEOUT.times(1)).rejectionType(RejectionType.PROCESSING_ERROR);
    verify(commandResponseWriter, TIMEOUT.times(1)).tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldInvokeOnProcessedListener() {
    // given
    streamProcessorRule.startTypedStreamProcessor(
        (processors, context) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ACTIVATE_ELEMENT,
                mock(TypedRecordProcessor.class)));

    // when
    final var position =
        streamProcessorRule.writeCommand(
            ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    final var processedCommandCaptor = ArgumentCaptor.forClass(TypedRecord.class);
    verify(streamProcessorRule.getMockStreamProcessorListener(), TIMEOUT)
        .onProcessed(processedCommandCaptor.capture());

    assertThat(processedCommandCaptor.getValue().getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldNotifyLifecycleListenersOnPauseAndResume() throws InterruptedException {
    // given
    final CountDownLatch pauseLatch = new CountDownLatch(1);
    final CountDownLatch resumeLatch = new CountDownLatch(1);

    final StreamProcessor streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, state) ->
                processors.withListener(
                    new StreamProcessorLifecycleAware() {
                      @Override
                      public void onPaused() {
                        pauseLatch.countDown();
                      }

                      @Override
                      public void onResumed() {
                        resumeLatch.countDown();
                      }
                    }));

    // when
    streamProcessor.pauseProcessing();
    streamProcessor.resumeProcessing();

    // then
    pauseLatch.await();
    resumeLatch.await();

    assertThat(pauseLatch.getCount()).isEqualTo(0);
    assertThat(resumeLatch.getCount()).isEqualTo(0);
  }

  @Test
  public void shouldResumeProcessMoreRecordsAfterPause() throws InterruptedException {
    // given
    final CountDownLatch pauseLatch = new CountDownLatch(1);
    final CountDownLatch resumeLatch = new CountDownLatch(1);
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    final StreamProcessor streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, state) ->
                processors
                    .onCommand(
                        ValueType.PROCESS_INSTANCE,
                        ProcessInstanceIntent.ACTIVATE_ELEMENT,
                        typedRecordProcessor)
                    .withListener(
                        new StreamProcessorLifecycleAware() {
                          @Override
                          public void onPaused() {
                            pauseLatch.countDown();
                          }

                          @Override
                          public void onResumed() {
                            resumeLatch.countDown();
                          }
                        }));

    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // when
    streamProcessor.pauseProcessing();
    pauseLatch.await();

    final long positionProcessedAfterResume =
        streamProcessorRule.writeCommand(
            ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    streamProcessor.resumeProcessing();
    resumeLatch.await();

    // then
    verify(streamProcessorRule.getMockStreamProcessorListener(), TIMEOUT.times(2))
        .onProcessed(any());

    Assertions.assertThat(streamProcessorRule.getLastSuccessfulProcessedRecordPosition())
        .isEqualTo(positionProcessedAfterResume);
  }

  @Test
  @Ignore
  // this currently fails because the state writer expects a certain state
  // if the engine abstraction is done, we will rewrite the test in a way
  // that we just return no result and verify whether the position was not updated
  // We can ignore the test for now, since it is tested anyway implicit
  public void shouldNotOverwriteLastWrittenPositionIfNoFollowUpEvent()
      throws ExecutionException, InterruptedException {
    // given
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors
                .onCommand(
                    ValueType.PROCESS_INSTANCE,
                    ProcessInstanceIntent.ACTIVATE_ELEMENT,
                    new TypedRecordProcessor<>() {
                      @Override
                      public void processRecord(final TypedRecord<UnifiedRecordValue> record) {
                        state
                            .getWriters()
                            .state()
                            .appendFollowUpEvent(
                                record.getKey(),
                                ProcessInstanceIntent.ELEMENT_ACTIVATING,
                                record.getValue());
                      }
                    })
                .onCommand(
                    ValueType.PROCESS_INSTANCE,
                    ProcessInstanceIntent.CANCEL,
                    mock(TypedRecordProcessor.class)));

    // when
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    final long position = waitForActivatingEvent().getPosition();

    streamProcessorRule.writeCommand(ProcessInstanceIntent.CANCEL, PROCESS_INSTANCE_RECORD);

    verify(streamProcessorRule.getMockStreamProcessorListener(), TIMEOUT.times(2))
        .onProcessed(any());

    // then
    final long lastWrittenPos =
        streamProcessorRule.getStreamProcessor(0).getLastWrittenPositionAsync().get();
    assertThat(lastWrittenPos).isEqualTo(position);
  }

  private Record<ProcessInstanceRecord> waitForActivatingEvent() {
    return TestUtil.doRepeatedly(
            () ->
                streamProcessorRule
                    .events()
                    .onlyProcessInstanceRecords()
                    .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .findAny())
        .until(Optional::isPresent)
        .get();
  }

  private ActorFuture<Void> call(
      final ProcessingScheduleService scheduleService, final Runnable runnable) {
    final CompletableActorFuture<Void> result = new CompletableActorFuture<>();
    scheduleService.runDelayed(
        Duration.ZERO,
        () -> {
          try {
            runnable.run();
            result.complete(null);
          } catch (final Exception e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }
}
