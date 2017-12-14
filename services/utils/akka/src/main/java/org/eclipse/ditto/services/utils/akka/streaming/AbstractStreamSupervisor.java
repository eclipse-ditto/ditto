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

import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_FINISHED_MSG;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
import scala.Option;
import scala.concurrent.duration.FiniteDuration;

/**
 * An actor that supervises stream forwarders.
 */
public abstract class AbstractStreamSupervisor<C> extends AbstractActorWithStash {

    /**
     * The name of the supervised stream forwarder.
     */
    public static final String STREAM_FORWARDER_ACTOR_NAME = "streamForwarder";

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final SupervisorStrategy supervisorStrategy =
            new OneForOneStrategy(true, DeciderBuilder.matchAny(e -> SupervisorStrategy.stop()).build());

    private Cancellable activityCheck;

    private final StreamMetadataPersistence streamMetadataPersistence;
    private final Materializer materializer;
    private Duration startOffset;
    private final Duration streamInterval;
    private final Duration initialStartOffset;
    private final Duration warnOffset;
    private @Nullable ActorRef forwarder;
    private @Nullable StreamTrigger nextStream;
    private @Nullable StreamTrigger activeStream;
    private @Nullable Boolean activeStreamSuccess;

    /**
     * Constructor.
     *
     * @param streamMetadataPersistence the {@link StreamMetadataPersistence} used to read and write stream metadata (is
     * used to remember the end time of the last stream after a re-start).
     * @param materializer the materializer to run Akka streams with.
     * @param startOffset The offset for the start timestamp - needed to make sure that we don't lose events, cause the
     * timestamp of a event might be created before the actual insert to the DB. Furthermore the offset helps minimizing
     * conflicts with the event-processing mechanism.
     * @param streamInterval this interval defines the minimum and maximum creation time of the entities to be queried
     * by the underlying stream.
     * @param initialStartOffset this offset is only on initial start, i.e. when the last query end cannot be retrieved
     * by means of {@code streamMetadataPersistence}.
     * @param warnOffset if a query-start is more than this offset in the past, a warning will be logged
     */
    protected AbstractStreamSupervisor(final StreamMetadataPersistence streamMetadataPersistence,
            final Materializer materializer,
            final Duration startOffset,
            final Duration streamInterval, final Duration initialStartOffset, final Duration warnOffset) {
        this.streamMetadataPersistence = streamMetadataPersistence;
        this.materializer = materializer;

        this.startOffset = startOffset;
        this.streamInterval = streamInterval;
        this.initialStartOffset = initialStartOffset;
        this.warnOffset = warnOffset;
    }

    /**
     * Returns props to create a stream forwarder actor.
     *
     * @return The props.
     */
    protected abstract Props getStreamForwarderProps();

    /**
     * Computes the command for starting a stream.
     *
     * @return The command command to start a stream.
     */
    protected abstract C newStartStreamingCommand(final StreamTrigger streamMetadata);

    /**
     * Returns the actor to request streams from.
     *
     * @return Reference to the streaming actor.
     */
    protected abstract ActorRef getStreamingActor();

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        final StreamTrigger nextStreamTrigger = computeNextStreamTrigger(null);
        scheduleStream(nextStreamTrigger);
    }

    private void scheduleStream(final Instant when) {
        final Duration duration;
        final Instant now = Instant.now();
        if (now.isAfter(when)) {
            // if "when" is in the past, schedule immediately
            duration = Duration.ZERO;
        } else {
            duration = Duration.between(now, when);
        }

        scheduleStream(duration);
    }

    private void scheduleStream(final Duration duration) {
        if (activityCheck != null) {
            activityCheck.cancel();
        }

        final FiniteDuration finiteDuration = FiniteDuration.create(duration.getSeconds(), TimeUnit.SECONDS);
        activityCheck = getContext().system().scheduler()
                .scheduleOnce(finiteDuration, getSelf(), TryToStartStream.INSTANCE,
                        getContext().dispatcher(), ActorRef.noSender());
    }

    @Override
    public void postStop() {
        if (null != activityCheck) {
            activityCheck.cancel();
        }
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(TryToStartStream.class, tryToStartStream -> {
                    log.debug("Switching to supervisingBehaviour has been triggered by message: {}",
                            tryToStartStream);
                    becomeSupervising();
                    tryToStartStream();
                })
                .match(Terminated.class, this::terminated)
                .matchAny(m -> {
                    log.debug("Initial behavior - Stashing message: {}", m);
                    stash();
                })
                .build();
    }

    private void becomeSupervising() {
        log.debug("becoming supervising...");
        getContext().become(createSupervisingBehavior());
        unstashAll();
    }

    private Receive createSupervisingBehavior() {
        return ReceiveBuilder.create()
                .matchEquals(STREAM_FINISHED_MSG, unused -> streamCompleted())
                .matchEquals(FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG, unused -> streamTimedOut())
                .match(Terminated.class, this::terminated)
                .match(TryToStartStream.class, unused -> tryToStartStream())
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
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
            final String successMessage = MessageFormat
                    .format("Updating last sync timestamp to value: <{0}>", lastSuccessfulQueryEnd);
            streamMetadataPersistence.updateLastSuccessfulStreamEnd(lastSuccessfulQueryEnd)
                    .runWith(akka.stream.javadsl.Sink.last(), materializer)
                    .thenRun(() -> log.info(successMessage))
                    .exceptionally(error -> {
                        log.error("Failed to update last sync timestamp to value: <{}>, due to: <{}>",
                                lastSuccessfulQueryEnd, error);
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
            final Instant initialStartTsWithoutStandardOffset = now.minus(initialStartOffset);

            queryStart = streamMetadataPersistence.retrieveLastSuccessfulStreamEnd(initialStartTsWithoutStandardOffset);
        }

        final Duration offsetFromNow = Duration.between(queryStart, now);
        // if offset is not in the future, i.e. in the past
        if (!offsetFromNow.isNegative() && offsetFromNow.compareTo(warnOffset) > 0) {
            log.warning("The next Query-Start <{}> is older than the configured warn-offset <{}>. Please verify that" +
                            " this does not happen frequently, otherwise won't get \"up-to-date\" anymore.",
                    queryStart, warnOffset);
        }

        return StreamTrigger.calculateStreamTrigger(now, queryStart, startOffset, streamInterval);
    }

    private void scheduleStream(final StreamTrigger streamTrigger) {
        this.nextStream = streamTrigger;

        scheduleStream(streamTrigger.getPlannedStreamStart());
    }

    private void tryToStartStream() {
        if (forwarder != null) {
            log.warning("Forwarder is still running: {}. Re-scheduling current stream.", forwarder);
            rescheduleActiveStream();
        } else {
            final C startStreamCommand = newStartStreamingCommand(nextStream);

            final ActorRef streamingActor = getStreamingActor();
            forwarder = createOrGetForwarder();

            log.info("Requesting stream from <{}> on behalf of <{}> by <{}>", streamingActor, forwarder,
                    startStreamCommand);
            getStreamingActor().tell(startStreamCommand, forwarder);
            activeStream = nextStream;
            activeStreamSuccess = null;
        }
    }

    private void rescheduleActiveStream() {
        final Instant rescheduledPlannedStreamStart = Instant.now().plus(streamInterval);
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
            forwarderRef = getContext().actorOf(getStreamForwarderProps(), STREAM_FORWARDER_ACTOR_NAME);
            log.debug("Watching forwarder: {}", forwarderRef);
            // important: watch the child to get notified when it terminates
            getContext().watch(forwarderRef);
        }

        return forwarderRef;
    }

    private void terminated(final Terminated terminated) {
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

    private static final class TryToStartStream {

        private static final TryToStartStream INSTANCE = new TryToStartStream();

        private TryToStartStream() {
            // no-op
        }
    }
}
