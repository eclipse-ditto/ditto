/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.streaming;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_COMPLETED;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_FAILED;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.streaming.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import scala.Option;
import scala.concurrent.duration.FiniteDuration;

/**
 * An actor that supervises stream forwarders. It maintains the end time of the last successful stream in a
 * {@code StreamMetadataPersistence} and starts streams whenever the timestamp becomes too old. It collaborates
 * with {@code AbstractStreamingActor} and {@code AbstractStreamForwarder} to ensure that the recipient of stream
 * messages eventually receive all messages up until the recent past.
 * <pre>
 * {@code
 * Streaming                                                                       Supervisor           Stream
 * Actor                                                                              +                 Message
 *    +                                                                               |                 Recipient
 *    |                                                                               |                   +
 *    |                                                                               |                   |
 *    |                                                                         spawns|                   |
 *    |                                          START_STREAMING            <---------+                   |
 *    |  <-----------------------------------------------------+  Stream                                  |
 *    |                                                           Forwarder                               |
 *    |                                                              +                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |                                                              |                                    |
 *    |  spawns          Akka                                        |                                    |
 *    |  +------------>  Stream                                      |                                    |
 *    |                  Source                                      |                                    |
 *    |                    +                                         |                                    |
 *    |                    |                                         |                                    |
 *    |                    |  STREAM_STARTED(BATCH_SIZE)             |                                    |
 *    |                    |  +----------------------------------->  |                                    |
 *    |                    |                                         |                                    |
 *    |                    |                                    ACK  |                                    |
 *    |                    |  <-----------------------------------+  |                                    |
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
 *    |                    |                                    ACK  |                                    |
 *    |                    |  <-----------------------------------+  |                                    |
 *    |                    |                                         |                                    |
 *    |                    |  STREAM_COMPLETED                       |                                    |
 *    |                    |  +----------------------------------->  |                                    |
 *    |                    |                                         |                                    |
 *    |                    +                                         |                                    |
 *    |                   Dead                                       | STREAM_COMPLETED                   |
 *    |                                                              | +--------------+                   |
 *    |                                                              +                |                   |
 *    |                                                             Dead              |                   |
 *    |                                                                               |                   |
 *    |                                                                               |                   |
 *    |                                                                               v                   |
 *    |                                                                            Stream                 |
 *    |                                                                            Supervisor             |
 * }
 * </pre>
 */
public final class DefaultStreamSupervisor<E> extends AbstractActor {

    /**
     * The name of the supervised stream forwarder.
     */
    public static final String STREAM_FORWARDER_ACTOR_NAME = "streamForwarder";

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final SupervisorStrategy supervisorStrategy =
            new OneForOneStrategy(true, DeciderBuilder.matchAny(e -> SupervisorStrategy.stop()).build());

    private Cancellable scheduledStreamStart;

    private final ActorRef forwardTo;
    private final ActorRef provider;
    private final Class<E> elementClass;
    private final Function<E, Source<Object, NotUsed>> mapEntityFunction;
    private final Function<SudoStreamModifiedEntities, ?> streamTriggerMessageMapper;
    private final StreamMetadataPersistence streamMetadataPersistence;
    private final Materializer materializer;
    private final StreamConsumerSettings streamConsumerSettings;
    private @Nullable ActorRef forwarder;
    private @Nullable StreamTrigger nextStream;
    private @Nullable StreamTrigger activeStream;
    private @Nullable Boolean activeStreamSuccess;

    /*
     * Regular check that this actor is not stuck.
     */
    private Cancellable activityCheck;

    /*
     * Timestamp of the last instant when a forwarder is started or stopped.
     */
    private Instant lastStreamStartOrStop;

    /*
     * package-private for unit tests
     */
    DefaultStreamSupervisor(final ActorRef forwardTo, final ActorRef provider,
            final Class<E> elementClass,
            final Function<E, Source<Object, NotUsed>> mapEntityFunction,
            final Function<SudoStreamModifiedEntities, ?> streamTriggerMessageMapper,
            final StreamMetadataPersistence streamMetadataPersistence,
            final Materializer materializer,
            final StreamConsumerSettings streamConsumerSettings) {
        this.forwardTo = requireNonNull(forwardTo);
        this.provider = requireNonNull(provider);
        this.elementClass = requireNonNull(elementClass);
        this.mapEntityFunction = requireNonNull(mapEntityFunction);
        this.streamTriggerMessageMapper = requireNonNull(streamTriggerMessageMapper);
        this.streamMetadataPersistence = requireNonNull(streamMetadataPersistence);
        this.materializer = requireNonNull(materializer);
        this.streamConsumerSettings = requireNonNull(streamConsumerSettings);

        activityCheck = scheduleActivityCheck(streamConsumerSettings);
        lastStreamStartOrStop = Instant.now();

        final StreamTrigger nextStreamTrigger = computeNextStreamTrigger(null);
        scheduleStream(nextStreamTrigger);
    }

    /**
     * Creates the props for {@link DefaultStreamSupervisor}.
     *
     * @param <E> the type of elements.
     * @param forwardTo the {@link ActorRef} to which the stream will be forwarded.
     * @param provider the {@link ActorRef} which provides the stream.
     * @param elementClass the class of elements.
     * @param mapEntityFunction the function to create a source of messages from each streamed element.
     * @param streamTriggerMessageMapper a mapping function to convert a {@link SudoStreamModifiedEntities} message to a
     * message understood by the stream provider. Can be used to send messages via Akka PubSub.
     * @param streamMetadataPersistence the {@link StreamMetadataPersistence} used to read and write stream metadata (is
     * used to remember the end time of the last stream after a re-start).
     * @param materializer the materializer to run Akka streams with.
     * @param streamConsumerSettings The settings for stream consumption.
     * @return the props
     */
    public static <E> Props props(final ActorRef forwardTo, final ActorRef provider,
            final Class<E> elementClass,
            final Function<E, Source<Object, NotUsed>> mapEntityFunction,
            final Function<SudoStreamModifiedEntities, ?> streamTriggerMessageMapper,
            final StreamMetadataPersistence streamMetadataPersistence,
            final Materializer materializer,
            final StreamConsumerSettings streamConsumerSettings) {

        return Props.create(DefaultStreamSupervisor.class, new Creator<DefaultStreamSupervisor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public DefaultStreamSupervisor create() {
                return new DefaultStreamSupervisor<>(forwardTo, provider, elementClass, mapEntityFunction,
                        streamTriggerMessageMapper, streamMetadataPersistence, materializer, streamConsumerSettings);
            }
        });
    }

    private Props getStreamForwarderProps() {
        return DefaultStreamForwarder.props(forwardTo, getSelf(), streamConsumerSettings.getMaxIdleTime(),
                elementClass, mapEntityFunction);
    }

    private Object newStartStreamingCommand(final StreamTrigger streamRestrictions) {
        final SudoStreamModifiedEntities retrieveModifiedEntityIdWithRevisions =
                SudoStreamModifiedEntities.of(streamRestrictions.getQueryStart(),
                        streamRestrictions.getQueryEnd(),
                        streamConsumerSettings.getElementsStreamedPerBatch(),
                        streamConsumerSettings.getStreamingActorTimeout().toMillis(),
                        DittoHeaders.empty());

        return streamTriggerMessageMapper.apply(retrieveModifiedEntityIdWithRevisions);
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public void postStop() throws Exception {
        if (null != scheduledStreamStart) {
            scheduledStreamStart.cancel();
        }
        if (null != activityCheck) {
            activityCheck.cancel();
        }
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(TryToStartStream.INSTANCE, tryToStartStream -> {
                    log.debug("Switching to supervisingBehaviour has been triggered by message: {}",
                            tryToStartStream);
                    becomeSupervising();
                    tryToStartStream();
                })
                .match(Terminated.class, this::terminated)
                .matchEquals(CheckForActivity.INSTANCE, this::checkForActivity)
                .build();
    }

    private void becomeSupervising() {
        log.debug("becoming supervising...");
        getContext().become(createSupervisingBehavior());
    }

    private Receive createSupervisingBehavior() {
        return ReceiveBuilder.create()
                .matchEquals(STREAM_COMPLETED, msg -> streamCompleted())
                .matchEquals(STREAM_FAILED, msg -> streamFailed())
                .matchEquals(FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG, msg -> streamTimedOut())
                .matchEquals(TryToStartStream.INSTANCE, msg -> tryToStartStream())
                .match(Terminated.class, this::terminated)
                .matchEquals(CheckForActivity.INSTANCE, this::checkForActivity)
                .build();
    }

    private void streamCompleted() {
        log.debug("Stream completed.");
        activeStreamSuccess = true;
    }

    private void streamTimedOut() {
        log.debug("Stream timed out.");
        activeStreamSuccess = false;
    }

    private void streamFailed() {
        log.debug("Stream failed");
        activeStreamSuccess = false;
    }

    private void scheduleNextStream() {
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
            streamMetadataPersistence.updateLastSuccessfulStreamEnd(lastSuccessfulQueryEnd)
                    .runWith(akka.stream.javadsl.Sink.last(), materializer)
                    .thenRun(() -> log.info("Updated last sync timestamp to value: <{}>.", lastSuccessfulQueryEnd))
                    .exceptionally(error -> {
                        log.error(error, "Failed to update last sync timestamp to value: <{}>.",
                                lastSuccessfulQueryEnd);
                        return null;
                    });

            final StreamTrigger nextStreamTrigger = computeNextStreamTrigger(lastSuccessfulQueryEnd);
            scheduleStream(nextStreamTrigger);
        } else {
            rescheduleActiveStream();
        }
    }

    private StreamTrigger computeNextStreamTrigger(@Nullable final Instant lastSuccessfulQueryEnd) {
        final Instant now = Instant.now();

        final Instant queryStart;
        // short-cut: we do not need to access the database if last synch has been completed
        if (lastSuccessfulQueryEnd != null) {
            queryStart = lastSuccessfulQueryEnd;
        } else {
            // the initial start ts is only used when no sync has been run yet (i.e. no timestamp has been persisted)
            final Instant initialStartTsWithoutStandardOffset =
                    now.minus(streamConsumerSettings.getInitialStartOffset());
            final Optional<Instant> instant = streamMetadataPersistence.retrieveLastSuccessfulStreamEnd();
            queryStart = instant.orElse
                    (initialStartTsWithoutStandardOffset);
        }

        final Duration offsetFromNow = Duration.between(queryStart, now);
        // check if the queryStart is very long in the past to be able to log a warning
        final Duration warnOffset = streamConsumerSettings.getOutdatedWarningOffset();
        if (!offsetFromNow.isNegative() && offsetFromNow.compareTo(warnOffset) > 0) {
            log.warning("The next Query-Start <{}> is older than the configured warn-offset <{}>. Please verify that" +
                            " this does not happen frequently, otherwise won't get \"up-to-date\" anymore.",
                    queryStart, warnOffset);
        }

        final Duration startOffset = streamConsumerSettings.getStartOffset();
        final Duration streamInterval = streamConsumerSettings.getStreamInterval();
        return StreamTrigger.calculateStreamTrigger(now, queryStart, startOffset, streamInterval);
    }

    private void scheduleStream(final StreamTrigger streamTrigger) {
        this.nextStream = streamTrigger;

        scheduleStream(streamTrigger.getPlannedStreamStart());
    }

    private void scheduleStream(final Instant when) {
        final Duration duration;
        final Instant now = Instant.now();
        if (when.isBefore(now)) {
            // if "when" is in the past, schedule immediately
            duration = Duration.ZERO;
        } else {
            duration = Duration.between(now, when);
        }

        scheduleStream(duration);
    }

    private void scheduleStream(final Duration duration) {
        log.info("Schedule Stream in: {}", duration);
        if (scheduledStreamStart != null) {
            scheduledStreamStart.cancel();
        }

        final FiniteDuration finiteDuration = fromDuration(duration);
        scheduledStreamStart = getContext().system().scheduler()
                .scheduleOnce(finiteDuration, getSelf(), TryToStartStream.INSTANCE,
                        getContext().dispatcher(), ActorRef.noSender());
    }

    private Cancellable scheduleActivityCheck(final StreamConsumerSettings streamConsumerSettings) {
        final FiniteDuration initialDelay = fromDuration(streamConsumerSettings.getOutdatedWarningOffset());
        final FiniteDuration interval = fromDuration(streamConsumerSettings.getStreamInterval());
        final CheckForActivity message = CheckForActivity.INSTANCE;
        return getContext().getSystem()
                .scheduler()
                .schedule(initialDelay, interval, getSelf(), message, getContext().dispatcher(), ActorRef.noSender());
    }

    private void tryToStartStream() {
        if (forwarder != null) {
            log.warning("Forwarder is still running: {}. Re-scheduling current stream.", forwarder);
            rescheduleActiveStream();
        } else {
            final Object startStreamCommand = newStartStreamingCommand(nextStream);

            forwarder = createOrGetForwarder();

            log.info("Requesting stream from <{}> on behalf of <{}> by <{}>", provider, forwarder,
                    startStreamCommand);
            provider.tell(startStreamCommand, forwarder);
            activeStream = nextStream;
            activeStreamSuccess = null;
        }
    }

    private void rescheduleActiveStream() {
        final Instant rescheduledPlannedStreamStart = Instant.now().plus(streamConsumerSettings.getStreamInterval());
        log.warning("Re-scheduling at {}", rescheduledPlannedStreamStart);

        if (activeStream == null) {
            log.error("Cannot re-schedule stream, because metadata of active stream is unknown.");
            return;
        }

        final StreamTrigger rescheduledStreamTrigger = activeStream.rescheduleAt(rescheduledPlannedStreamStart);
        scheduleStream(rescheduledStreamTrigger);
    }

    private ActorRef createOrGetForwarder() {
        final Option<ActorRef> refOption = getContext().child(STREAM_FORWARDER_ACTOR_NAME);
        final ActorRef forwarderRef;
        if (refOption.isDefined()) {
            forwarderRef = refOption.get();
        } else {
            forwarderRef = startStreamForwarder();
            log.debug("Watching forwarder: {}", forwarderRef);
            // important: watch the child to get notified when it terminates
            getContext().watch(forwarderRef);
        }

        return forwarderRef;
    }

    private ActorRef startStreamForwarder() {
        streamForwarderStartedOrStopped();
        return getContext().actorOf(getStreamForwarderProps(), STREAM_FORWARDER_ACTOR_NAME);
    }

    private void terminated(final Terminated terminated) {
        streamForwarderStartedOrStopped();
        final ActorRef terminatedActor = terminated.getActor();
        log.debug("Received Terminated-Message: {}", terminated);

        if (!Objects.equals(terminatedActor, forwarder)) {
            log.warning("Received Terminated-Message from actor <{}> which does not match current forwarder <{}>",
                    terminatedActor, forwarder);
            return;
        }

        getContext().unwatch(terminatedActor);
        forwarder = null;

        scheduleNextStream();
    }

    private void checkForActivity(final CheckForActivity instance) {
        final Duration outdatedWarningOfset = streamConsumerSettings.getOutdatedWarningOffset();
        final Instant now = Instant.now();
        if (lastStreamStartOrStop != null &&
                lastStreamStartOrStop.plus(outdatedWarningOfset).isBefore(now)) {
            // Did not start or stop stream for a long time. Something is wrong.
            // Throw IllegalStateException to trigger restart.
            final String message =
                    String.format("Started or stopped last time at <%s>, which is older than <%s> from now <%s>",
                            lastStreamStartOrStop.toString(), outdatedWarningOfset.toString(), now.toString());
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    private static FiniteDuration fromDuration(final Duration duration) {
        return FiniteDuration.create(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Declare stream forwarder to be started or stopped. Used for self-sanity check.
     * If stream supervisor does not start or stop a forwarder for a long time then something is wrong.
     */
    private void streamForwarderStartedOrStopped() {
        lastStreamStartOrStop = Instant.now();
    }

    private enum TryToStartStream {
        INSTANCE
    }

    private enum CheckForActivity {
        INSTANCE
    }
}
