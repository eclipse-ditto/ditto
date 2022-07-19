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
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.service.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.parser.thingsearch.RqlOptionParser;
import org.eclipse.ditto.thingsearch.model.SizeOption;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidOptionException;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.SubscriptionNotFoundException;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CancelSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.RequestFromSubscription;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionFailed;
import org.reactivestreams.Subscriber;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor that manages search subscriptions for 1 websocket connection or 1 ConnectionPersistenceActor.
 *
 * @since 1.1.0
 */
public final class SubscriptionManager extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "subscriptionManager";

    private final Duration idleTimeout;
    private final ActorRef pubSubMediator;
    private final ActorSelection proxyActor;
    private final Materializer materializer;
    private final DittoDiagnosticLoggingAdapter log;

    private final int defaultPageSize;
    private final int maxPageSize;

    private int subscriptionIdCounter = 0;

    SubscriptionManager(final Duration idleTimeout,
            final ActorRef pubSubMediator,
            final ActorSelection proxyActor,
            final Materializer materializer) {
        this.idleTimeout = idleTimeout;
        this.pubSubMediator = pubSubMediator;
        this.proxyActor = proxyActor;
        this.materializer = materializer;
        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

        final LimitsConfig limitsConfig =
                DefaultLimitsConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()));
        defaultPageSize = limitsConfig.getThingsSearchDefaultPageSize();
        maxPageSize = limitsConfig.getThingsSearchMaxPageSize();
    }

    /**
     * Create Props for a subscription manager.
     *
     * @param idleTimeout lifetime of an idle SubscriptionActor.
     * @param pubSubMediator pub-sub mediator for reporting of out-of-sync things.
     * @param proxyActor recipient of thing and StreamThings commands.
     * @param materializer materializer for the search streams.
     * @return Props of the actor.
     */
    public static Props props(final Duration idleTimeout,
            final ActorRef pubSubMediator,
            final ActorSelection proxyActor,
            final Materializer materializer) {

        return Props.create(SubscriptionManager.class, idleTimeout, pubSubMediator, proxyActor, materializer);
    }

    private static JsonArray asJsonArray(final Collection<String> strings) {
        return strings.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray());
    }

    private int getPageSize(@Nullable final String optionString) {
        if (optionString == null) {
            return defaultPageSize;
        } else {
            final int pageSize = RqlOptionParser.parseOptions(optionString)
                    .stream()
                    .flatMap(option -> option instanceof SizeOption sizeOption
                            ? Stream.of(sizeOption.getSize())
                            : Stream.empty())
                    .findFirst()
                    .orElse(defaultPageSize);
            if (pageSize > 0 && pageSize <= maxPageSize) {
                return pageSize;
            } else {
                throw InvalidOptionException.newBuilder()
                        .message("Invalid option: '" + optionString + "'")
                        .description("size(n) -- n must be between 1 and " + maxPageSize)
                        .build();
            }
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RequestFromSubscription.class, this::requestSubscription)
                .match(CreateSubscription.class, this::createSubscription)
                .match(CancelSubscription.class, this::cancelSubscription)
                .build();
    }

    private void requestSubscription(final RequestFromSubscription requestFromSubscription) {
        forwardToChild(requestFromSubscription.getSubscriptionId(), requestFromSubscription);
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
    }

    private void connect(final ActorRef subscriptionActor, final Source<JsonArray, NotUsed> pageSource) {
        final Subscriber<JsonArray> subscriber = SubscriptionActor.asSubscriber(subscriptionActor);
        lazify(pageSource).runWith(Sink.fromSubscriber(subscriber), materializer);
    }

    private Source<JsonArray, NotUsed> getPageSource(final CreateSubscription createSubscription) {
        final String optionString = createSubscription.getOptions().orElse(null);
        final JsonArray namespaces = createSubscription.getNamespaces()
                .filter(ns -> !ns.isEmpty())
                .map(SubscriptionManager::asJsonArray)
                .orElse(null);
        try {
            final SearchSource searchSource = SearchSource.newBuilder()
                    .pubSubMediator(pubSubMediator)
                    .commandForwarder(proxyActor)
                    .namespaces(namespaces)
                    .filter(createSubscription.getFilter().orElse(null))
                    .fields(createSubscription.getSelectedFields().orElse(null))
                    .options(optionString)
                    .dittoHeaders(createSubscription.getDittoHeaders())
                    .build();
            return searchSource.start(builder -> {})
                    .grouped(getPageSize(optionString))
                    .map(JsonArray::of);
        } catch (final DittoRuntimeException e) {
            return Source.failed(e);
        }
    }

    private String nextSubscriptionId(final CreateSubscription createSubscription) {
        final String prefix = createSubscription.getPrefix().orElse("");
        return prefix + subscriptionIdCounter++;
    }

    /**
     * Make a source that never completes until downstream request.
     *
     * @param upstream the source to lazify.
     * @param <T> the type of elements.
     * @return the lazified source.
     */
    private static <T> Source<T, ?> lazify(final Source<T, ?> upstream) {
        return Source.lazySource(() -> upstream);
    }

}
