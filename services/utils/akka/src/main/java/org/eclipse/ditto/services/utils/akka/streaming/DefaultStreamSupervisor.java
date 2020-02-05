/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.akka.streaming;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.models.streaming.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.NotUsed;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * An actor that supervises stream forwarders. It maintains the end time of the last successful stream in a
 * {@code StreamMetadataPersistence} and starts streams whenever the timestamp becomes too old. It collaborates
 * with {@code AbstractStreamingActor} and {@code AbstractStreamForwarder} to ensure that the recipient of stream
 * messages eventually receive all messages up until the recent past.
 * <pre>
 * {@code
 * Streaming                                                      Supervisor                            Stream
 * Actor                                                             +                                  Message
 *    +                                                              |                                  Recipient
 *    |                                                              |                                    +
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                          START_STREAMING     |                                    |
 *    |  <-----------------------------------------------------+     |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |  spawns                                                      |                                    |
 *    |  +------------> Source                                       |                                    |
 *    |                                                              |                                    |
 *    |                    +                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |  SourceRef                              |                                    |
 *    |                    |  +----------------------------------->  |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |  BATCH(ELEMENT)                         |                                    |
 *    |                    |  +----------------------------------->  |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |  MESSAGE #1 of ELEMENT             |
 *    |                    |                                         |  +------------------------------>  |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                               ACK  |
 *    |                    |                                         |  <------------------------------+  |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |  MESSAGE #2 of ELEMENT             |
 *    |                    |                                         |  +------------------------------>  |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                               ACK  |
 *    |                    |                                         |  <------------------------------+  |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |  STREAM_COMPLETED                       |                                    |
 *    |                    |  +----------------------------------->  |                                    |
 *    |                    |                                         |                                    |
 *    |                    +                                         |                                    |
 *    |                   Dead                                       |                                    |
 *    |                                                              |                                    |
 *    |                                                              +                                    |
 *    |                                                                                                   |
 *    |                                                                                                   |
 *    |                                                                                                   |
 *    |                                                                                                   |
 *    |                                                                                                   |
 *    +                                                                                                   +
 * }
 * </pre>
 */
public final class DefaultStreamSupervisor<E> extends AbstractActorWithTimers {

    /**
     * Timeout waiting for SourceRef from the streaming actor.
     */
    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(10L);

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final SupervisorStrategy supervisorStrategy =
            new OneForOneStrategy(true, DeciderBuilder.matchAny(e -> SupervisorStrategy.stop()).build());

    private final ActorRef forwardTo;
    private final ActorRef provider;
    private final Class<E> elementClass;
    private final Function<E, Source<Object, NotUsed>> mapEntityFunction;
    private final Function<SudoStreamModifiedEntities, ?> streamTriggerMessageMapper;
    private final TimestampPersistence streamMetadataPersistence;
    private final Materializer materializer;
    private final SyncConfig syncConfig;
    private @Nullable StreamTrigger activeStream;
    private @Nullable Boolean activeStreamSuccess;

    /*
     * Timestamp of the last instant when a stream is started or stopped.
     */
    private Instant lastStreamStartOrStop;

    /*
     * package-private for unit tests
     */
    @SuppressWarnings("unused")
    DefaultStreamSupervisor(final ActorRef forwardTo,
            final ActorRef provider,
            final Class<E> elementClass,
            final Function<E, Source<Object, NotUsed>> mapEntityFunction,
            final Function<SudoStreamModifiedEntities, ?> streamTriggerMessageMapper,
            final TimestampPersistence streamMetadataPersistence,
            final Materializer materializer,
            final SyncConfig syncConfig) {

        this.forwardTo = checkNotNull(forwardTo, "forward-to actor reference");
        this.provider = checkNotNull(provider, "provider actor reference");
        this.elementClass = checkNotNull(elementClass, "element class");
        this.mapEntityFunction = checkNotNull(mapEntityFunction, "map entity function");
        this.streamTriggerMessageMapper = checkNotNull(streamTriggerMessageMapper, "stream trigger message mapper");
        this.streamMetadataPersistence = checkNotNull(streamMetadataPersistence, "stream metadata persistence");
        this.materializer = checkNotNull(materializer, "Materializer");
        this.syncConfig = checkNotNull(syncConfig, "SyncConfig");

        lastStreamStartOrStop = Instant.now();

        scheduleActivityCheck();

        // schedule first stream after delay
        computeAndScheduleNextStreamTrigger(null);
    }

    /**
     * Creates the props for {@code DefaultStreamSupervisor}.
     *
     * @param <E> the type of elements.
     * @param forwardTo the {@link ActorRef} to which the stream will be forwarded.
     * @param provider the {@link ActorRef} which provides the stream.
     * @param elementClass the class of elements.
     * @param mapEntityFunction the function to create a source of messages from each streamed element.
     * @param streamTriggerMessageMapper a mapping function to convert a {@link SudoStreamModifiedEntities} message to a
     * message understood by the stream provider. Can be used to send messages via Akka PubSub.
     * @param streamMetadataPersistence the {@link TimestampPersistence} used to read and write stream metadata (is
     * used to remember the end time of the last stream after a re-start).
     * @param materializer the materializer to run Akka streams with.
     * @param syncConfig the configuration settings for stream consumption.
     * @return the props
     */
    public static <E> Props props(final ActorRef forwardTo,
            final ActorRef provider,
            final Class<E> elementClass,
            final Function<E, Source<Object, NotUsed>> mapEntityFunction,
            final Function<SudoStreamModifiedEntities, ?> streamTriggerMessageMapper,
            final TimestampPersistence streamMetadataPersistence,
            final Materializer materializer,
            final SyncConfig syncConfig) {

        return Props.create(DefaultStreamSupervisor.class, forwardTo, provider, elementClass, mapEntityFunction,
                streamTriggerMessageMapper, streamMetadataPersistence, materializer, syncConfig);
    }

    private Object newStartStreamingCommand(final StreamTrigger streamRestrictions) {
        final SudoStreamModifiedEntities retrieveModifiedEntityIdWithRevisions =
                SudoStreamModifiedEntities.of(streamRestrictions.getQueryStart(),
                        streamRestrictions.getQueryEnd(),
                        syncConfig.getElementsStreamedPerBatch(),
                        syncConfig.getStreamingActorTimeout().toMillis(),
                        DittoHeaders.empty());

        return streamTriggerMessageMapper.apply(retrieveModifiedEntityIdWithRevisions);
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(StreamTrigger.class, streamTrigger -> {
                    log.debug("Triggering stream: {}", streamTrigger);
                    tryToStartStream(streamTrigger);
                })
                .match(SourceRef.class, this::startStreamForwarding)
                .match(ScheduleNextStream.class, this::scheduleNextStream)
                .matchEquals(CheckForActivity.INSTANCE, this::checkForActivity)
                .build();
    }

    private void scheduleNextStream(final ScheduleNextStream message) {
        if (Objects.equals(activeStream, message.activeStreamTrigger)) {
            if (message.terminated) {
                streamTerminated();
            }
            if (message.error != null) {
                log.error("Stream from <{}> failed for <{}> due to <{}>", provider, activeStream, message.error);
                activeStreamSuccess = false;
            } else {
                log.debug("Stream completed without error: <{}>", activeStream);
                activeStreamSuccess = true;
            }
            doScheduleNextStream();
        } else {
            log.warning("Got ScheduleNextStream <{}> which does not match the active stream trigger <{}>",
                    message, activeStream);
        }
    }

    private void doScheduleNextStream() {
        if (activeStream == null) {
            log.error("Cannot schedule next stream, because active stream is unknown.");
            return;
        }
        if (activeStreamSuccess == null) {
            log.warning("Cannot schedule next stream, because success of active stream is unknown.");
            return;
        }

        if (activeStreamSuccess) {
            final Instant lastSuccessfulQueryEnd = activeStream.getQueryEnd();
            streamMetadataPersistence.setTimestamp(lastSuccessfulQueryEnd)
                    .runWith(akka.stream.javadsl.Sink.last(), materializer)
                    .thenRun(() -> log.debug("Updated last sync timestamp to value: <{}>.", lastSuccessfulQueryEnd))
                    .exceptionally(error -> {
                        log.error(error, "Failed to update last sync timestamp to value: <{}>.",
                                lastSuccessfulQueryEnd);
                        return null;
                    });
            computeAndScheduleNextStreamTrigger(lastSuccessfulQueryEnd);
        } else {
            rescheduleActiveStream();
        }

        // There is no active stream before the next StreamTrigger message arrives.
        activeStream = null;
    }

    private void computeAndScheduleNextStreamTrigger(@Nullable final Instant lastSuccessfulQueryEnd) {
        Patterns.pipe(computeNextStreamTrigger(lastSuccessfulQueryEnd), getContext().dispatcher()).to(getSelf());
    }

    private CompletionStage<StreamTrigger> computeNextStreamTrigger(@Nullable final Instant lastSuccessfulQueryEnd) {
        return computeNextStreamTriggerSource(lastSuccessfulQueryEnd).runWith(Sink.head(), materializer);
    }

    private Source<StreamTrigger, NotUsed> computeNextStreamTriggerSource(
            @Nullable final Instant lastSuccessfulQueryEnd) {
        final Instant now = Instant.now();

        final Source<Instant, NotUsed> queryStartSource;
        // short-cut: we do not need to access the database if last sync has been completed
        if (lastSuccessfulQueryEnd != null) {
            queryStartSource = Source.single(lastSuccessfulQueryEnd);
        } else {
            // the initial start ts is only used when no sync has been run yet (i.e. no timestamp has been persisted)
            final Instant initialStartTsWithoutStandardOffset =
                    now.minus(syncConfig.getInitialStartOffset());
            queryStartSource = streamMetadataPersistence.getTimestampAsync()
                    .map(instant -> instant.orElse(initialStartTsWithoutStandardOffset));
        }

        final Source<StreamTrigger, NotUsed> streamTriggerSource =
                queryStartSource.map(queryStart -> {
                    final Duration offsetFromNow = Duration.between(queryStart, now);
                    // check if the queryStart is very long in the past to be able to log a warning
                    final Duration warnOffset = syncConfig.getOutdatedWarningOffset();
                    if (!offsetFromNow.isNegative() && offsetFromNow.compareTo(warnOffset) > 0) {
                        log.debug("The next Query-Start <{}> is older than the configured warn-offset <{}>. " +
                                        "Please verify that this does not happen frequently, " +
                                        "otherwise won't get \"up-to-date\" anymore.",
                                queryStart, warnOffset);
                    }

                    final Duration startOffset = syncConfig.getStartOffset();
                    final Duration streamInterval = syncConfig.getStreamInterval();
                    final Duration minimalDelayBetweenStreams = syncConfig.getMinimalDelayBetweenStreams();
                    return StreamTrigger.calculateStreamTrigger(now, queryStart, startOffset,
                            streamInterval, minimalDelayBetweenStreams, lastSuccessfulQueryEnd == null);
                });

        return streamTriggerSource.flatMapConcat(this::delayStreamTrigger);
    }

    private Duration computeNextStreamTriggerDelay(final StreamTrigger streamTrigger) {
        final Instant when = streamTrigger.getPlannedStreamStart();
        final Duration duration;
        final Instant now = Instant.now();
        if (when.isBefore(now)) {
            // if "when" is in the past, schedule immediately
            duration = Duration.ZERO;
        } else {
            duration = Duration.between(now, when);
        }
        final Duration nextStreamDelay =
                duration.minus(syncConfig.getMinimalDelayBetweenStreams()).isNegative()
                        ? syncConfig.getMinimalDelayBetweenStreams()
                        : duration;
        log.debug("Schedule Stream in: {}", nextStreamDelay);
        return nextStreamDelay;
    }

    private void scheduleActivityCheck() {
        final Duration interval = syncConfig.getStreamInterval();
        final CheckForActivity message = CheckForActivity.INSTANCE;
        getTimers().startPeriodicTimer("activityCheck", message, interval);
    }

    private void tryToStartStream(final StreamTrigger streamTrigger) {
        if (activeStream != null) {
            log.warning("Stream is still running: {}. Re-scheduling current stream.");
            rescheduleActiveStream();
        } else {
            final Object startStreamCommand = newStartStreamingCommand(streamTrigger);
            log.debug("Requesting stream from <{}> by <{}>", provider, startStreamCommand);
            provider.tell(startStreamCommand, getSelf());
            activeStream = streamTrigger;
            activeStreamSuccess = null;
            final CompletionStage<Object> answerFromProvider =
                    Patterns.ask(provider, startStreamCommand, PROVIDER_TIMEOUT)
                            .handle((result, error) -> {
                                if (result instanceof SourceRef) {
                                    return result;
                                } else {
                                    final Throwable e = result instanceof AskTimeoutException
                                            ? (AskTimeoutException) result
                                            : error != null
                                            ? error
                                            : new IllegalStateException("Unexpected message from provider: " + result);
                                    return new ScheduleNextStream(activeStream, e, false);
                                }
                            });
            Patterns.pipe(answerFromProvider, getContext().dispatcher()).to(getSelf());
        }
    }

    private void rescheduleActiveStream() {
        final Instant rescheduledPlannedStreamStart = Instant.now().plus(syncConfig.getStreamInterval());
        log.warning("Re-scheduling at {}", rescheduledPlannedStreamStart);

        if (activeStream == null) {
            log.error("Cannot re-schedule stream, because metadata of active stream is unknown.");
            return;
        }

        final StreamTrigger rescheduledStreamTrigger = activeStream.rescheduleAt(rescheduledPlannedStreamStart);

        final CompletionStage<StreamTrigger> delayedStreamTrigger =
                delayStreamTrigger(rescheduledStreamTrigger).runWith(Sink.head(), materializer);

        Patterns.pipe(delayedStreamTrigger, getContext().getDispatcher()).to(getSelf());
    }

    private Source<StreamTrigger, NotUsed> delayStreamTrigger(final StreamTrigger streamTrigger) {

        final Duration nextStreamTriggerDelay = computeNextStreamTriggerDelay(streamTrigger);

        if (nextStreamTriggerDelay.isZero()) {
            return Source.single(streamTrigger);
        } else {
            return Source.single(streamTrigger).initialDelay(nextStreamTriggerDelay);
        }
    }

    private void startStreamForwarding(final SourceRef<?> sourceRef) {
        streamForwardingStartedOrStopped();
        final StreamTrigger currentStreamTrigger = activeStream;
        final CompletionStage<ScheduleNextStream> termination = sourceRef.getSource()
                .flatMapConcat(this::forwardStreamElement)
                .log("forwardStreamElement", log)
                .toMat(Sink.ignore(), Keep.right())
                .run(materializer)
                .handle((result, error) -> new ScheduleNextStream(currentStreamTrigger, error, true));
        Patterns.pipe(termination, getContext().dispatcher()).to(getSelf());
    }

    private Source<Object, NotUsed> forwardStreamElement(final Object streamElement) {
        if (streamElement instanceof BatchedEntityIdWithRevisions) {
            final BatchedEntityIdWithRevisions<?> message = (BatchedEntityIdWithRevisions) streamElement;
            final List<?> elements = message.getElements();
            final Duration maxIdleTime = syncConfig.getMaxIdleTime();
            return Source.fromIterator(elements::iterator)
                    .map(this::typecheckMessageToForward)
                    .flatMapConcat(mapEntityFunction::apply)
                    .mapAsync(1, element -> Patterns.ask(forwardTo, element, maxIdleTime));
        } else {
            log.warning("Unexpected element from stream: <{}>", streamElement);
            return Source.empty();
        }

    }

    private E typecheckMessageToForward(final Object element) {
        if (elementClass.isInstance(element)) {
            return elementClass.cast(element);
        } else {
            final String failureMessage =
                    String.format("Element type mismatch. Expected <%s>, actual <%s>", elementClass, element);
            // abort stream right away
            throw new IllegalStateException(failureMessage);
        }
    }

    private void streamTerminated() {
        streamForwardingStartedOrStopped();
        log.debug("Current stream terminated: <{}>", activeStream);
    }

    private void checkForActivity(final CheckForActivity instance) {
        final Duration outdatedWarningOffset = syncConfig.getOutdatedWarningOffset();
        final Instant now = Instant.now();
        if (lastStreamStartOrStop != null &&
                lastStreamStartOrStop.plus(outdatedWarningOffset).isBefore(now)) {
            // Did not start or stop stream for a long time. Something is wrong.
            // Throw IllegalStateException to trigger restart.
            final String message =
                    String.format("Started or stopped last time at <%s>, which is older than <%s> from now <%s>",
                            lastStreamStartOrStop.toString(), outdatedWarningOffset.toString(), now.toString());
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Declare stream forwarder to be started or stopped. Used for self-sanity check.
     * If stream supervisor does not start or stop a forwarder for a long time then something is wrong.
     */
    private void streamForwardingStartedOrStopped() {
        lastStreamStartOrStop = Instant.now();
    }

    private enum CheckForActivity {
        INSTANCE
    }

    private static final class ScheduleNextStream {

        private final StreamTrigger activeStreamTrigger;
        private final Throwable error;
        private final boolean terminated;

        private ScheduleNextStream(final StreamTrigger activeStreamTrigger, final Throwable error,
                final boolean terminated) {
            this.activeStreamTrigger = activeStreamTrigger;
            this.error = error;
            this.terminated = terminated;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "[activeStreamTrigger=" + activeStreamTrigger +
                    ",error=" + error +
                    ",terminated=" + terminated +
                    "]";
        }
    }

}
