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

import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.DOES_NOT_HAVE_NEXT_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_ACK_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_COMPLETED;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_FAILED;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_STARTED;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor that receives a stream of elements, forwards them to another actor, and expects as many acknowledgements as
 * there are messages forwarded. Terminates self if no message was received for a period of time. Below is this
 * actor's state transition diagram.
 * <pre>
 * {@code
 *
 *    +-----+
 *    |START|
 *    +--+--+
 *       |
 *       |
 *       | STREAM_STARTED: ACK
 *       |
 *       v
 *  +----+----+               NO: ACK              +---------+
 *  |         +<-----------------------------------+         |
 *  |ITERATING|                                    |HAS_NEXT?|
 *  |         +----------------------------------->+         |
 *  +----+----+         ELEMENTS: MAP_ENTITY       +--+---+--+
 *       |                                            |   ^
 *       |                                            |   |
 *       |STREAM_FINISHED: ACK            YES: FORWARD|   |ACK
 *       |                                            |   |
 *       v                                            v   |
 *  +----+----+                            +----------+---+----------+
 *  |KILL_SELF|                            |WAIT_FOR_ACK_FROM_UPDATER|
 *  +---------+                            +-------------------------+
 *
 * }
 * </pre>
 *
 * @param <E> Type of received stream elements.
 */

public abstract class AbstractStreamForwarder<E> extends AbstractActor {

    /**
     * Logger associated with this actor.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    protected final ActorMaterializer materializer = ActorMaterializer.create(getContext());

    private Instant lastMessageReceived = Instant.now();

    private Cancellable activityCheck;

    protected AbstractStreamForwarder() {
        log.info("Creating new StreamForwarder");
    }

    /**
     * Returns the maximum time this actor waits for a message before it terminates itself.
     *
     * @return The maximum idle time.
     */
    protected abstract Duration getMaxIdleTime();

    /**
     * Returns the class of stream elements.
     *
     * @return The class.
     */
    protected abstract Class<E> getElementClass();

    /**
     * Returns the recipient of forwarded messages.
     *
     * @return The recipient.
     */
    protected abstract ActorRef getRecipient();

    /**
     * Returns the recipient of stream status messages.
     *
     * @return Whom to send stream completion and failure messages.
     */
    protected abstract ActorRef getCompletionRecipient();

    /**
     * Transforms a stream element into a source of messages to forward to the recipient.
     *
     * @param element The stream element.
     * @return A source of messages.
     */
    protected abstract Source<Object, NotUsed> mapEntity(final E element);

    private Receive initialBehavior() {
        return ReceiveBuilder.create()
                .matchEquals(STREAM_STARTED, unit -> {
                    getSender().tell(STREAM_ACK_MSG, getSelf());
                    updateLastMessageReceived();
                    getContext().become(iteratingBehavior());
                })
                .matchEquals(STREAM_FAILED, this::streamFailed)
                .matchEquals(CheckForActivity.INSTANCE, this::checkForActivity)
                .build();
    }

    private Receive iteratingBehavior() {
        return ReceiveBuilder.create()
                .matchEquals(STREAM_COMPLETED, this::streamCompleted)
                .matchEquals(STREAM_FAILED, this::streamFailed)
                .matchEquals(CheckForActivity.INSTANCE, this::checkForActivity)
                .match(BatchedEntityIdWithRevisions.class, this::transitionToForwardingLoop)
                .build();
    }

    private Receive hasNextBehavior(final ActorRef elementSender) {
        return ReceiveBuilder.create()
                .matchEquals(STREAM_ACK_MSG, unit -> updateLastMessageReceived())
                .matchEquals(DOES_NOT_HAVE_NEXT_MSG, unit -> {
                    updateLastMessageReceived();
                    getContext().become(iteratingBehavior());
                    log.debug("sending ack {} to streaming actor {}", STREAM_ACK_MSG, elementSender);
                    elementSender.tell(STREAM_ACK_MSG, getSelf());
                })
                .matchEquals(STREAM_FAILED, this::streamFailed)
                .match(CheckForActivity.class, this::checkForActivity)
                .build();
    }

    /**
     * Create a source of messages to forward from the batched elements received from the stream, expect
     * acknowledgement from the recipient for each message, and send self {@code DOES_NOT_HAVE_NEXT_MSG} at the end.
     *
     * @param batchedElements the batched stream elements.
     */
    private void transitionToForwardingLoop(final BatchedEntityIdWithRevisions<?> batchedElements) {
        log.debug("got element: {}", batchedElements);
        final ActorRef self = getSelf();
        final ActorRef recipient = getRecipient();
        final long timeoutMillis = getMaxIdleTime().toMillis();
        final List<E> typedElements = typecheck(batchedElements.getElements());
        getContext().become(hasNextBehavior(getSender()));
        Source.fromIterator(typedElements::iterator)
                .flatMapConcat(this::mapEntity)
                .concat(Source.single(DOES_NOT_HAVE_NEXT_MSG))
                .mapAsync(1, msgToForward -> {
                    if (DOES_NOT_HAVE_NEXT_MSG.equals(msgToForward)) {
                        self.tell(msgToForward, self);
                        return CompletableFuture.completedFuture(msgToForward);
                    } else {
                        return PatternsCS.<Object>ask(recipient, msgToForward, timeoutMillis)
                                .thenApply(ack -> {
                                    if (isSuccessAck(ack)) {
                                        log.debug("got ack: {}", ack);
                                    } else {
                                        log.error("got failure ack: {}", ack);
                                    }
                                    self.tell(STREAM_ACK_MSG, ActorRef.noSender());
                                    return ack;
                                });
                    }
                })
                .runWith(Sink.ignore(), materializer);
    }

    @SuppressWarnings("unchecked")
    private List<E> typecheck(final List<?> elements) {
        final List<E> result = new ArrayList<>(elements.size());
        for (Object element : elements) {
            if (getElementClass().isInstance(element)) {
                result.add((E) element);
            } else {
                final String failureMessage = String.format("Element type mismatch. Expected <%s>, actual <%s %s>",
                        getElementClass().toString(), element.getClass().toString(), element.toString());
                streamFailed(failureMessage);
                // return empty list to ensure no message is sent downstream before shutdown.
                return Collections.emptyList();
            }
        }
        return result;
    }

    private static boolean isSuccessAck(final Object ack) {
        return (ack instanceof StreamAck) &&
                StreamAck.Status.SUCCESS == ((StreamAck) ack).getStatus();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        final FiniteDuration delayAndInterval = FiniteDuration.create(getMaxIdleTime().getSeconds(), TimeUnit.SECONDS);
        activityCheck = getContext().system().scheduler()
                .schedule(delayAndInterval, delayAndInterval, getSelf(), CheckForActivity.INSTANCE,
                        getContext().dispatcher(), ActorRef.noSender());
    }

    @Override
    public void postStop() throws Exception {
        if (null != activityCheck) {
            activityCheck.cancel();
        }
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return initialBehavior();
    }

    private void checkForActivity(final CheckForActivity checkForActivity) {
        final Duration sinceLastMessage = Duration.between(lastMessageReceived, Instant.now());
        if (sinceLastMessage.compareTo(getMaxIdleTime()) > 0) {
            log.error("Stream timed out");
            getCompletionRecipient().tell(FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG, getSelf());
            shutdown();
        } else {
            log.debug("Stream is still considered as active");
        }
    }

    private void streamCompleted(final Object completionMessage) {
        log.info("Stream successfully finished.");
        getCompletionRecipient().tell(completionMessage, getSelf());
        getContext().stop(getSelf());
    }

    private void streamFailed(final Object failureMessage) {
        getCompletionRecipient().forward(failureMessage, getContext());
        shutdown();
    }

    private void updateLastMessageReceived() {
        lastMessageReceived = Instant.now();
        log.debug("Updated last message");
    }

    private void shutdown() {
        getContext().stop(getSelf());
    }

    private enum CheckForActivity {
        INSTANCE
    }
}
