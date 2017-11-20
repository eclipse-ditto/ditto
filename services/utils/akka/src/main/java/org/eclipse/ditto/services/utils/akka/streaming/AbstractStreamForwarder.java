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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor that receives a stream of elements, transforms them, forwards them to another actor, and expects as many
 * acknowledgements as there are streamed elements. Terminates self if no message was received for a period of time.
 * <p>
 * Stream elements are to be acknowledged by {@code }
 * </p>
 *
 * @param <E> Type of received stream elements.
 * @param <M> Type of messages forwarded to the recipient for each stream element.
 */
public abstract class AbstractStreamForwarder<E, M> extends AbstractActor {

    /**
     * Logger associated with this actor.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef recipient;
    private final Duration maxIdleTime;

    private Cancellable activityCheck;

    private Instant lastMessageReceived = Instant.now();
    private long elementCount = 0;
    private long ackCount = 0;
    private boolean streamComplete = false;

    /**
     * Creates an instance of this actor.
     *
     * @param recipient Recipient of forwarded stream elements.
     * @param maxIdleTime How long to wait before terminating self since the last received message.
     */
    protected AbstractStreamForwarder(final ActorRef recipient, final Duration maxIdleTime) {
        this.recipient = recipient;
        this.maxIdleTime = maxIdleTime;

        final FiniteDuration delayAndInterval = FiniteDuration.create(maxIdleTime.getSeconds(), TimeUnit.SECONDS);
        activityCheck = getContext().system().scheduler()
                .schedule(delayAndInterval, delayAndInterval, getSelf(), new CheckForActivity(),
                        getContext().dispatcher(), ActorRef.noSender());
    }

    /**
     * Returns the class of stream elements.
     *
     * @return The class.
     */
    protected abstract Class<E> getElementClass();

    /**
     * Transforms a stream element into a message to forward.
     *
     * @param element The stream element.
     * @return The message to forward.
     */
    protected abstract M transformElement(final E element);

    /**
     * Invoked when all stream elements are forwarded and acknowledged.
     * Do not start asynchronous operations: the actor terminates immediately after this method returns.
     */
    protected void onSuccess() {
        log.info("Stream complete: {} elements forwarded, {} acks received.", elementCount, ackCount);
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
        return ReceiveBuilder.create()
                .match(getElementClass(), this::transformAndForwardElement)
                .match(Status.Success.class, this::handleAck)
                .match(Done.class, this::handleStreamComplete)
                .match(CheckForActivity.class, this::checkForActivity)
                .matchAny(this::unhandled)
                .build();
    }

    private void transformAndForwardElement(final E element) {
        if (streamComplete) {
            log.error("Received stream element <{}> after stream termination.", element);
        }
        elementCount++;
        final M message = transformElement(element);
        recipient.tell(message, getSelf());
        updateLastMessageReceived();
    }

    private void handleAck(final Status.Success success) {
        ackCount++;
        updateLastMessageReceived();
        checkAllElementsAreAcknowledged();
    }

    private void handleStreamComplete(final Done done) {
        streamComplete = true;
        checkAllElementsAreAcknowledged();
    }

    private void checkAllElementsAreAcknowledged() {
        if (streamComplete && ackCount >= elementCount) {
            if (ackCount > elementCount) {
                log.error("Received {} Ack messages, which are more than the {} stream elements forwarded", ackCount,
                        elementCount);
            }
            onSuccess();
            getContext().stop(getSelf());
        }
    }

    private void checkForActivity(final CheckForActivity message) {
        final Duration sinceLastMessage = Duration.between(lastMessageReceived, Instant.now());
        if (sinceLastMessage.compareTo(maxIdleTime) > 0) {
            log.error("Stream timed out. {} elements, {} acks. Last message: <{}>", elementCount, ackCount,
                    lastMessageReceived);
            getContext().stop(getSelf());
        }
    }

    private void updateLastMessageReceived() {
        lastMessageReceived = Instant.now();
    }

    private static final class CheckForActivity {}
}
