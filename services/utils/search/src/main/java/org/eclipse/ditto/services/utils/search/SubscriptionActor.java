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
package org.eclipse.ditto.services.utils.search;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionComplete;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionHasNext;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that translates subscription commands into stream operations and stream signals into subscription events.
 */
public final class SubscriptionActor extends AbstractActorWithStash {

    private final DittoDiagnosticLoggingAdapter log;

    private Subscription subscription;
    private ActorRef sender;
    private DittoHeaders dittoHeaders;

    SubscriptionActor(final ActorRef sender, final DittoHeaders dittoHeaders) {
        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        this.sender = sender;
        this.dittoHeaders = dittoHeaders;
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
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RequestSubscription.class, this::requestSubscription)
                .match(CancelSubscription.class, this::cancelSubscription)
                .match(SubscriptionHasNext.class, this::subscriptionHasNext)
                .match(SubscriptionComplete.class, this::subscriptionComplete)
                .match(SubscriptionFailed.class, this::subscriptionFailed)
                .match(Subscription.class, this::onSubscribe)
                .build();
    }

    private void onSubscribe(final Subscription subscription) {
        if (this.subscription != null) {
            subscription.cancel();
        } else {
            this.subscription = subscription;
            unstashAll();
        }
    }

    private void setSenderAndDittoHeaders(final ThingSearchCommand<?> command) {
        sender = getSender();
        dittoHeaders = command.getDittoHeaders();
    }

    private void requestSubscription(final RequestSubscription requestSubscription) {
        if (subscription == null) {
            log.withCorrelationId(requestSubscription).debug("Stashing <{}>", requestSubscription);
            stash();
        } else {
            log.withCorrelationId(requestSubscription).debug("Processing <{}>", requestSubscription);
            setSenderAndDittoHeaders(requestSubscription);
            subscription.request(requestSubscription.getDemand());
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
            getContext().stop(getSelf());
        }
    }

    private void subscriptionHasNext(final SubscriptionHasNext event) {
        log.debug("{}", event);
        sender.tell(event.setDittoHeaders(dittoHeaders), getSelf());
    }

    private void subscriptionComplete(final SubscriptionComplete event) {
        log.info("{}", event);
        sender.tell(event.setDittoHeaders(dittoHeaders), getSelf());
        getContext().stop(getSelf());
    }

    private void subscriptionFailed(final SubscriptionFailed event) {
        log.withCorrelationId(event).error(event.getError(), "SubscriptionFailed");
        sender.tell(event.setDittoHeaders(dittoHeaders), getSelf());
        getContext().stop(getSelf());
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
            final SubscriptionHasNext event = SubscriptionHasNext.of(subscriptionId, items, DittoHeaders.empty());
            subscriptionActor.tell(event, ActorRef.noSender());
        }

        @Override
        public void onError(final Throwable t) {
            final SubscriptionFailed event =
                    SubscriptionFailed.of(subscriptionId,
                            DittoRuntimeException.asDittoRuntimeException(t, e ->
                                    GatewayInternalErrorException.newBuilder().cause(e).build()),
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
