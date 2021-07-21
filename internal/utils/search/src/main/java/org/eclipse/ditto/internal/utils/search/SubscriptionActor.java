/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.search;

import java.time.Duration;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.SubscriptionProtocolErrorException;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.SubscriptionTimeoutException;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CancelSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.RequestFromSubscription;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionComplete;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionCreated;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionFailed;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionHasNextPage;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that translates subscription commands into stream operations and stream signals into subscription events.
 *
 * @since 1.1.0
 */
public final class SubscriptionActor extends AbstractActorWithStashWithTimers {

    /**
     * Live on as zombie for a while to prevent timeout at client side
     */
    private static final Duration ZOMBIE_LIFETIME = Duration.ofSeconds(10L);

    private final DittoDiagnosticLoggingAdapter log;

    private Subscription subscription;
    private ActorRef sender;
    private DittoHeaders dittoHeaders;

    SubscriptionActor(final Duration idleTimeout, final ActorRef sender, final DittoHeaders dittoHeaders) {
        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        this.sender = sender;
        this.dittoHeaders = dittoHeaders;
        getContext().setReceiveTimeout(idleTimeout);
    }

    /**
     * Create Props object for the SubscriptionActor.
     *
     * @param idleTimeout maximum lifetime while idling
     * @param sender sender of the command that created this actor.
     * @param dittoHeaders headers of the command that created this actor.
     * @return Props for this actor.
     */
    public static Props props(final Duration idleTimeout, final ActorRef sender, final DittoHeaders dittoHeaders) {
        return Props.create(SubscriptionActor.class, idleTimeout, sender, dittoHeaders);
    }

    /**
     * Wrap a subscription actor as a reactive stream subscriber.
     *
     * @param subscriptionActor reference to the subscription actor.
     * @return the actor presented as a reactive stream subscriber.
     */
    public static Subscriber<JsonArray> asSubscriber(final ActorRef subscriptionActor) {
        return new SubscriberOps(subscriptionActor);
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
                .match(RequestFromSubscription.class, this::requestSubscription)
                .match(CancelSubscription.class, this::cancelSubscription)
                .match(SubscriptionHasNextPage.class, this::subscriptionHasNext)
                .match(SubscriptionComplete.class, this::subscriptionComplete)
                .match(SubscriptionFailed.class, this::subscriptionFailed)
                .match(Subscription.class, this::onSubscribe)
                .matchEquals(ReceiveTimeout.getInstance(), this::idleTimeout)
                .build();
    }

    private Receive createZombieBehavior() {
        return ReceiveBuilder.create()
                .match(RequestFromSubscription.class, requestSubscription -> {
                    log.withCorrelationId(requestSubscription)
                            .info("Rejecting RequestSubscription[demand={}] as zombie",
                                    requestSubscription.getDemand());
                    final String errorMessage =
                            "This subscription is considered cancelled. No more messages are processed.";
                    final SubscriptionFailed subscriptionFailed = SubscriptionFailed.of(
                            getSubscriptionId(),
                            SubscriptionProtocolErrorException.newBuilder()
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
        final String subscriptionId = getSubscriptionId();
        final SubscriptionTimeoutException error = SubscriptionTimeoutException.of(subscriptionId, dittoHeaders);
        final SubscriptionFailed subscriptionFailed = SubscriptionFailed.of(subscriptionId, error, dittoHeaders);
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

    private SubscriptionCreated getSubscriptionCreated() {
        return SubscriptionCreated.of(getSubscriptionId(), dittoHeaders);
    }

    private void setSenderAndDittoHeaders(final ThingSearchCommand<?> command) {
        sender = getSender();
        dittoHeaders = command.getDittoHeaders();
    }

    private void requestSubscription(final RequestFromSubscription requestFromSubscription) {
        if (subscription == null) {
            log.withCorrelationId(requestFromSubscription).debug("Stashing <{}>", requestFromSubscription);
            stash();
        } else {
            log.withCorrelationId(requestFromSubscription).debug("Processing <{}>", requestFromSubscription);
            setSenderAndDittoHeaders(requestFromSubscription);
            subscription.request(requestFromSubscription.getDemand());
        }
    }

    private void cancelSubscription(final CancelSubscription cancelSubscription) {
        if (subscription == null) {
            log.withCorrelationId(cancelSubscription).info("Stashing <{}>", cancelSubscription);
            stash();
        } else {
            log.withCorrelationId(cancelSubscription).info("Processing <{}>", cancelSubscription);
            setSenderAndDittoHeaders(cancelSubscription);
            subscription.cancel();
            becomeZombie();
        }
    }

    private void subscriptionHasNext(final SubscriptionHasNextPage event) {
        log.debug("Forwarding {}", event);
        sender.tell(event.setDittoHeaders(dittoHeaders), ActorRef.noSender());
    }

    private void subscriptionComplete(final SubscriptionComplete event) {
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

    private void subscriptionFailed(final SubscriptionFailed event) {
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

    private static final class SubscriberOps implements Subscriber<JsonArray> {

        private final ActorRef subscriptionActor;
        private final String subscriptionId;

        private SubscriberOps(final ActorRef subscriptionActor) {
            this.subscriptionActor = subscriptionActor;
            subscriptionId = subscriptionActor.path().name();
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            subscriptionActor.tell(subscription, ActorRef.noSender());
        }

        @Override
        public void onNext(final JsonArray items) {
            final SubscriptionHasNextPage event = SubscriptionHasNextPage.of(subscriptionId, items, DittoHeaders.empty());
            subscriptionActor.tell(event, ActorRef.noSender());
        }

        @Override
        public void onError(final Throwable t) {
            final SubscriptionFailed event =
                    SubscriptionFailed.of(subscriptionId,
                            DittoRuntimeException.asDittoRuntimeException(t, e -> {
                                if (e instanceof IllegalArgumentException) {
                                    // incorrect protocol from the client side
                                    return SubscriptionProtocolErrorException.of(e, DittoHeaders.empty());
                                } else {
                                    return GatewayInternalErrorException.newBuilder().cause(e).build();
                                }
                            }),
                            DittoHeaders.empty());
            subscriptionActor.tell(event, ActorRef.noSender());
        }

        @Override
        public void onComplete() {
            final SubscriptionComplete event = SubscriptionComplete.of(subscriptionId, DittoHeaders.empty());
            subscriptionActor.tell(event, ActorRef.noSender());
        }
    }
}
