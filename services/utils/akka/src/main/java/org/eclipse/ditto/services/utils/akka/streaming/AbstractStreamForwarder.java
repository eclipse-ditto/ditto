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
 * Actor that receives a stream of elements, forwards them to another actor, and expects as many
 * acknowledgements as there are streamed elements. Terminates self if no message was received for a period of time.
 * <p>
 * Each stream element is to be acknowledged by a {@code akka.actor.Status.Success}. An {@code akka.Done} object
 * should be sent at the end of the stream.
 * </p>
 *
 * @param <E> Type of received stream elements.
 */
public abstract class AbstractStreamForwarder<E> extends AbstractActor {

    /**
     * Logger associated with this actor.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private Instant lastMessageReceived = Instant.now();
    private long elementCount = 0;
    private long ackCount = 0;
    private boolean streamComplete = false;

    private Cancellable activityCheck;

    /**
     * Returns the actor to send transformed stream elements to.
     *
     * @return Reference of the recipient actor.
     */
    protected abstract ActorRef getRecipient();

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
     * Invoked when all stream elements are forwarded and acknowledged.
     * Do not start asynchronous operations: the actor terminates immediately after this method returns.
     */
    private void onSuccess() {
        log.info("Stream complete: {} elements forwarded, {} acks received.", elementCount, ackCount);
        getSuccessRecipient().tell(new Status.Success(lastMessageReceived), getSelf());
    }

    /**
     * Returns the actor to send a success message when the stream has been successfully completed.
     *
     * @return Reference of the success recipient actor.
     */
    protected abstract ActorRef getSuccessRecipient();

    @Override
    public void preStart() throws Exception {
        super.preStart();

        final FiniteDuration delayAndInterval = FiniteDuration.create(getMaxIdleTime().getSeconds(), TimeUnit.SECONDS);
        activityCheck = getContext().system().scheduler()
                .schedule(delayAndInterval, delayAndInterval, getSelf(), new CheckForActivity(),
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
        return ReceiveBuilder.create()
                .match(getElementClass(), this::transformAndForwardElement)
                .match(Status.Success.class, unused -> handleAck())
                .match(Status.Failure.class, this::handleFailure)
                .match(Done.class, unused -> handleStreamComplete())
                .match(CheckForActivity.class, unused -> checkForActivity())
                .matchAny(this::unhandled)
                .build();
    }

    private void transformAndForwardElement(final E element) {
        if (streamComplete) {
            log.error("Received stream element <{}> after stream termination; will forward it anyway.", element);
        }
        elementCount++;
        getRecipient().tell(element, getSelf());
        updateLastMessageReceived();
    }

    private void handleAck() {
        ackCount++;
        updateLastMessageReceived();
        checkAllElementsAreAcknowledged();
    }

    private void handleFailure(final Status.Failure failure) {
        log.warning("Received failure after: {} elements forwarded, {} acks received. Failure: {}", elementCount,
                ackCount, failure);
        shutdown();
    }

    private void handleStreamComplete() {
        streamComplete = true;
        checkAllElementsAreAcknowledged();
    }

    private void checkAllElementsAreAcknowledged() {
        if (streamComplete && ackCount >= elementCount) {
            if (ackCount > elementCount) {
                log.warning("Received {} Ack messages, which are more than the {} stream elements forwarded",
                        ackCount, elementCount);
            }

            log.info("Stream complete: {} elements forwarded, {} acks received.", elementCount, ackCount);
            onSuccess();
            shutdown();
        }
    }

    private void checkForActivity() {
        final Duration sinceLastMessage = Duration.between(lastMessageReceived, Instant.now());
        if (sinceLastMessage.compareTo(getMaxIdleTime()) > 0) {
            log.error("Stream timed out. {} elements, {} acks. Last message: <{}>", elementCount, ackCount,
                    lastMessageReceived);
            shutdown();
        }
    }

    private void updateLastMessageReceived() {
        lastMessageReceived = Instant.now();
    }

    private void shutdown() {
        getContext().stop(getSelf());
    }

    @SuppressWarnings("squid:S2094")
    private static final class CheckForActivity {}
}
