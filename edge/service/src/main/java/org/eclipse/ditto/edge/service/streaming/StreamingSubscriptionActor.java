/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.streaming;

import java.time.Duration;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.exceptions.StreamingSubscriptionProtocolErrorException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.StreamingSubscriptionTimeoutException;
import org.eclipse.ditto.base.model.signals.commands.streaming.CancelStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.RequestFromStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionComplete;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionCreated;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionFailed;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionHasNext;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonValue;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that translates streaming subscription commands into stream operations and stream signals into streaming
 * subscription events.
 */
public final class StreamingSubscriptionActor extends AbstractActorWithStashWithTimers {

    /**
     * Live on as zombie for a while to prevent timeout at client side
     */
    private static final Duration ZOMBIE_LIFETIME = Duration.ofSeconds(10L);

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final EntityId entityId;
    private Subscription subscription;
    private ActorRef sender;
    private DittoHeaders dittoHeaders;

    StreamingSubscriptionActor(final Duration idleTimeout,
            final EntityId entityId,
            final ActorRef sender,
            final DittoHeaders dittoHeaders) {
        this.entityId = entityId;
        this.sender = sender;
        this.dittoHeaders = dittoHeaders;
        getContext().setReceiveTimeout(idleTimeout);
    }

    /**
     * Create Props object for the StreamingSubscriptionActor.
     *
     * @param idleTimeout maximum lifetime while idling
     * @param entityId the entity ID for which the streaming subscription is created.
     * @param sender sender of the command that created this actor.
     * @param dittoHeaders headers of the command that created this actor.
     * @return Props for this actor.
     */
    public static Props props(final Duration idleTimeout, final EntityId entityId, final ActorRef sender,
            final DittoHeaders dittoHeaders) {
        return Props.create(StreamingSubscriptionActor.class, idleTimeout, entityId, sender, dittoHeaders);
    }

    /**
     * Wrap a subscription actor as a reactive stream subscriber.
     *
     * @param streamingSubscriptionActor reference to the subscription actor.
     * @param entityId the entity ID for which the subscriber is created.
     * @return the actor presented as a reactive stream subscriber.
     */
    public static Subscriber<JsonValue> asSubscriber(final ActorRef streamingSubscriptionActor,
            final EntityId entityId) {
        return new StreamingSubscriberOps(streamingSubscriptionActor, entityId);
    }

    @Override
    public void postStop() {
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RequestFromStreamingSubscription.class, this::requestSubscription)
                .match(CancelStreamingSubscription.class, this::cancelSubscription)
                .match(StreamingSubscriptionHasNext.class, this::subscriptionHasNext)
                .match(StreamingSubscriptionComplete.class, this::subscriptionComplete)
                .match(StreamingSubscriptionFailed.class, this::subscriptionFailed)
                .match(Subscription.class, this::onSubscribe)
                .matchEquals(ReceiveTimeout.getInstance(), this::idleTimeout)
                .matchAny(m -> log.warning("Unknown message: <{}>", m))
                .build();
    }

    private Receive createZombieBehavior() {
        return ReceiveBuilder.create()
                .match(RequestFromStreamingSubscription.class, requestSubscription -> {
                    log.withCorrelationId(requestSubscription)
                            .info("Rejecting RequestSubscription[demand={}] as zombie",
                                    requestSubscription.getDemand());
                    final String errorMessage =
                            "This subscription is considered cancelled. No more messages are processed.";
                    final StreamingSubscriptionFailed subscriptionFailed = StreamingSubscriptionFailed.of(
                            getSubscriptionId(),
                            entityId,
                            StreamingSubscriptionProtocolErrorException.newBuilder()
                                    .message(errorMessage)
                                    .build(),
                            requestSubscription.getDittoHeaders()
                    );
                    getSender().tell(subscriptionFailed, ActorRef.noSender());
                })
                .matchAny(message -> log.debug("Ignoring as zombie: <{}>", message))
                .build();
    }

    private void idleTimeout(final ReceiveTimeout receiveTimeout) {
        // usually a user error
        log.info("Stopping due to idle timeout");
        getContext().cancelReceiveTimeout();
        final String subscriptionId = getSubscriptionId();
        final StreamingSubscriptionTimeoutException error = StreamingSubscriptionTimeoutException
                .of(subscriptionId, dittoHeaders);
        final StreamingSubscriptionFailed subscriptionFailed = StreamingSubscriptionFailed
                .of(subscriptionId, entityId, error, dittoHeaders);
        if (subscription == null) {
            sender.tell(getSubscriptionCreated(), ActorRef.noSender());
        }
        sender.tell(subscriptionFailed, ActorRef.noSender());
        becomeZombie();
    }

    private void onSubscribe(final Subscription subscription) {
        if (this.subscription != null) {
            subscription.cancel();
        } else {
            this.subscription = subscription;
            sender.tell(getSubscriptionCreated(), ActorRef.noSender());
            unstashAll();
        }
    }

    private StreamingSubscriptionCreated getSubscriptionCreated() {
        return StreamingSubscriptionCreated.of(getSubscriptionId(), entityId, dittoHeaders);
    }

    private void setSenderAndDittoHeaders(final StreamingSubscriptionCommand<?> command) {
        sender = getSender();
        dittoHeaders = command.getDittoHeaders();
    }

    private void requestSubscription(final RequestFromStreamingSubscription requestFromStreamingSubscription) {
        if (subscription == null) {
            log.withCorrelationId(requestFromStreamingSubscription).debug("Stashing <{}>", requestFromStreamingSubscription);
            stash();
        } else {
            log.withCorrelationId(requestFromStreamingSubscription).debug("Processing <{}>", requestFromStreamingSubscription);
            setSenderAndDittoHeaders(requestFromStreamingSubscription);
            subscription.request(requestFromStreamingSubscription.getDemand());
        }
    }

    private void cancelSubscription(final CancelStreamingSubscription cancelStreamingSubscription) {
        if (subscription == null) {
            log.withCorrelationId(cancelStreamingSubscription).info("Stashing <{}>", cancelStreamingSubscription);
            stash();
        } else {
            log.withCorrelationId(cancelStreamingSubscription).info("Processing <{}>", cancelStreamingSubscription);
            setSenderAndDittoHeaders(cancelStreamingSubscription);
            subscription.cancel();
            becomeZombie();
        }
    }

    private void subscriptionHasNext(final StreamingSubscriptionHasNext event) {
        log.debug("Forwarding {}", event);
        sender.tell(event.setDittoHeaders(dittoHeaders), ActorRef.noSender());
    }

    private void subscriptionComplete(final StreamingSubscriptionComplete event) {
        // just in case: if error overtakes subscription, then there *will* be a subscription.
        if (subscription == null) {
            log.withCorrelationId(event).debug("Stashing <{}>", event);
            stash();
        } else {
            log.info("{}", event);
            sender.tell(event.setDittoHeaders(dittoHeaders), ActorRef.noSender());
            becomeZombie();
        }
    }

    private void subscriptionFailed(final StreamingSubscriptionFailed event) {
        // just in case: if error overtakes subscription, then there *will* be a subscription.
        if (subscription == null) {
            log.withCorrelationId(event).debug("Stashing <{}>", event);
            stash();
        } else {
            // log at INFO level because user errors may cause subscription failure.
            log.withCorrelationId(event).info("{}", event);
            sender.tell(event.setDittoHeaders(dittoHeaders), ActorRef.noSender());
            becomeZombie();
        }
    }

    private void becomeZombie() {
        getTimers().startSingleTimer(PoisonPill.getInstance(), PoisonPill.getInstance(), ZOMBIE_LIFETIME);
        getContext().become(createZombieBehavior());
    }

    private String getSubscriptionId() {
        return getSelf().path().name();
    }

    private static final class StreamingSubscriberOps implements Subscriber<JsonValue> {

        private final ActorRef streamingSubscriptionActor;
        private final String subscriptionId;
        private final EntityId entityId;

        private StreamingSubscriberOps(final ActorRef streamingSubscriptionActor, final EntityId entityId) {
            this.streamingSubscriptionActor = streamingSubscriptionActor;
            subscriptionId = streamingSubscriptionActor.path().name();
            this.entityId = entityId;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            streamingSubscriptionActor.tell(subscription, ActorRef.noSender());
        }

        @Override
        public void onNext(final JsonValue item) {
            final StreamingSubscriptionHasNext event = StreamingSubscriptionHasNext
                    .of(subscriptionId, entityId, item, DittoHeaders.empty());
            streamingSubscriptionActor.tell(event, ActorRef.noSender());
        }

        @Override
        public void onError(final Throwable t) {
            final StreamingSubscriptionFailed event =
                    StreamingSubscriptionFailed.of(subscriptionId,
                            entityId,
                            DittoRuntimeException.asDittoRuntimeException(t, e -> {
                                if (e instanceof IllegalArgumentException) {
                                    // incorrect protocol from the client side
                                    return StreamingSubscriptionProtocolErrorException.of(e, DittoHeaders.empty());
                                } else {
                                    return DittoInternalErrorException.newBuilder().cause(e).build();
                                }
                            }),
                            DittoHeaders.empty());
            streamingSubscriptionActor.tell(event, ActorRef.noSender());
        }

        @Override
        public void onComplete() {
            final StreamingSubscriptionComplete event = StreamingSubscriptionComplete.of(subscriptionId, entityId,
                    DittoHeaders.empty());
            streamingSubscriptionActor.tell(event, ActorRef.noSender());
        }
    }
}
