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

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.thingsearch.SizeOption;
import org.eclipse.ditto.model.thingsearchparser.RqlOptionParser;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.SubscriptionNotFoundException;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.reactivestreams.Subscriber;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor that manages search subscriptions for 1 websocket connection or 1 ConnectionPersistenceActor.
 */
public final class SubscriptionManager extends AbstractActor {

    // TODO: unify with other sources of page size limits

    public static final String ACTOR_NAME = "subscriptionManager";

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_MAX_RETRIES = 5; // 32s

    private final Duration idleTimeout;
    private final int maxRetries;
    private final ActorRef pubSubMediator;
    private final ActorRef conciergeForwarder;
    private final ActorMaterializer materializer;
    private final DittoDiagnosticLoggingAdapter log;

    private int subscriptionIdCounter = 0;

    SubscriptionManager(final Duration idleTimeout,
            final int maxRetries,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final ActorMaterializer materializer) {
        this.idleTimeout = idleTimeout;
        this.maxRetries = maxRetries;
        this.pubSubMediator = pubSubMediator;
        this.conciergeForwarder = conciergeForwarder;
        this.materializer = materializer;
        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    }

    /**
     * Create Props for a subscription manager.
     *
     * @param idleTimeout lifetime of an idle SubscriptionActor.
     * @param pubSubMediator pub-sub mediator for reporting of out-of-sync things.
     * @param conciergeForwarder recipient of thing and StreamThings commands.
     * @param materializer materializer for the search streams.
     * @return Props of the actor.
     */
    public static Props props(final Duration idleTimeout,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final ActorMaterializer materializer) {

        return Props.create(SubscriptionManager.class, idleTimeout, DEFAULT_MAX_RETRIES, pubSubMediator,
                conciergeForwarder, materializer);
    }

    private static JsonArray asJsonArray(final Collection<String> strings) {
        return strings.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray());
    }

    private static String joinOptions(final Collection<String> strings) {
        return String.join(",", strings);
    }

    private static int getPageSize(@Nullable final String optionString) {
        if (optionString == null) {
            return DEFAULT_PAGE_SIZE;
        } else {
            final int pageSize = RqlOptionParser.parseOptions(optionString)
                    .stream()
                    .flatMap(option -> option instanceof SizeOption
                            ? Stream.of(((SizeOption) option).getSize())
                            : Stream.empty())
                    .findFirst()
                    .orElse(DEFAULT_PAGE_SIZE);
            if (pageSize > 0 && pageSize <= MAX_PAGE_SIZE) {
                return pageSize;
            } else {
                throw InvalidOptionException.newBuilder()
                        .message("Invalid option: '" + optionString + "'")
                        .description("size(n) -- n must be between 1 and " + MAX_PAGE_SIZE)
                        .build();
            }
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RequestSubscription.class, this::requestSubscription)
                .match(CreateSubscription.class, this::createSubscription)
                .match(CancelSubscription.class, this::cancelSubscription)
                .build();
    }

    private void requestSubscription(final RequestSubscription requestSubscription) {
        forwardToChild(requestSubscription.getSubscriptionId(), requestSubscription);
    }

    private void cancelSubscription(final CancelSubscription cancelSubscription) {
        forwardToChild(cancelSubscription.getSubscriptionId(), cancelSubscription);
    }

    private void forwardToChild(final String subscriptionId, final ThingSearchCommand<?> command) {
        final Optional<ActorRef> subscriptionActor = getContext().findChild(subscriptionId);
        if (subscriptionActor.isPresent()) {
            log.withCorrelationId(command).debug("Forwarding to child: <{}>", command);
            subscriptionActor.get().tell(command, getSender());
        } else {
            // most likely a user error
            log.withCorrelationId(command)
                    .info("SubscriptionID not found, responding with SubscriptionFailed: <{}>", command);
            final SubscriptionNotFoundException error =
                    SubscriptionNotFoundException.of(subscriptionId, command.getDittoHeaders());
            final SubscriptionFailed subscriptionFailed =
                    SubscriptionFailed.of(subscriptionId, error, command.getDittoHeaders());
            getSender().tell(subscriptionFailed, ActorRef.noSender());
        }
    }

    private void createSubscription(final CreateSubscription createSubscription) {
        log.withCorrelationId(createSubscription).info("Processing <{}>", createSubscription);
        final String subscriptionId = nextSubscriptionId(createSubscription);
        final Props props = SubscriptionActor.props(idleTimeout, getSender(), createSubscription.getDittoHeaders());
        final ActorRef subscriptionActor = getContext().actorOf(props, subscriptionId);
        final Source<JsonArray, NotUsed> pageSource = getPageSource(createSubscription);
        connect(subscriptionActor, pageSource);
        final SubscriptionCreated subscriptionCreated =
                SubscriptionCreated.of(subscriptionId, createSubscription.getDittoHeaders());
        getSender().tell(subscriptionCreated, ActorRef.noSender());
    }

    private void connect(final ActorRef subscriptionActor, final Source<JsonArray, NotUsed> pageSource) {
        final Subscriber<JsonArray> subscriber = SubscriptionActor.asSubscriber(subscriptionActor);
        pageSource.runWith(Sink.fromSubscriber(subscriber), materializer);
    }

    private Source<JsonArray, NotUsed> getPageSource(final CreateSubscription createSubscription) {
        final String optionString = createSubscription.getOptions().map(SubscriptionManager::joinOptions).orElse(null);
        try {
            final SearchSource searchSource = SearchSource.newBuilder()
                    .pubSubMediator(pubSubMediator)
                    .conciergeForwarder(conciergeForwarder)
                    .maxRetries(maxRetries)
                    .namespaces(createSubscription.getNamespaces().map(SubscriptionManager::asJsonArray).orElse(null))
                    .filter(createSubscription.getFilter().orElse(null))
                    .fields(createSubscription.getSelectedFields().orElse(null))
                    .option(optionString)
                    .dittoHeaders(createSubscription.getDittoHeaders())
                    .build();
            return searchSource.start()
                    .grouped(getPageSize(optionString))
                    .map(JsonArray::of)
                    .buffer(1, OverflowStrategy.backpressure());
        } catch (final DittoRuntimeException e) {
            return Source.failed(e);
        }
    }

    private String nextSubscriptionId(final CreateSubscription createSubscription) {
        final String prefix = createSubscription.getPrefix().orElse("");
        return prefix + subscriptionIdCounter++;
    }
}
