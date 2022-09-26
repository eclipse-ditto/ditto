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
import java.util.Optional;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.exceptions.StreamingSubscriptionNotFoundException;
import org.eclipse.ditto.base.model.signals.commands.streaming.CancelStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.RequestFromStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.base.model.signals.commands.streaming.SubscribeForPersistedEvents;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionFailed;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.reactivestreams.Subscriber;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor that manages streaming subscriptions for 1 websocket connection or 1 ConnectionPersistenceActor.
 */
public final class StreamingSubscriptionManager extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "streamingSubscriptionManager";

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final Duration COMMAND_FORWARDER_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(15);

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Duration idleTimeout;
    private final ActorSelection commandForwarder;
    private final Materializer materializer;

    private int subscriptionIdCounter = 0;

    @SuppressWarnings("unused")
    private StreamingSubscriptionManager(final Duration idleTimeout,
            final ActorSelection commandForwarder,
            final Materializer materializer) {
        this.idleTimeout = idleTimeout;
        this.commandForwarder = commandForwarder;
        this.materializer = materializer;
    }

    /**
     * Create Props for a subscription manager.
     *
     * @param idleTimeout lifetime of an idle StreamingSubscriptionActor.
     * @param commandForwarder recipient of streaming subscription commands.
     * @param materializer materializer for the search streams.
     * @return Props of the actor.
     */
    public static Props props(final Duration idleTimeout,
            final ActorSelection commandForwarder,
            final Materializer materializer) {

        return Props.create(StreamingSubscriptionManager.class, idleTimeout, commandForwarder, materializer);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RequestFromStreamingSubscription.class, this::requestSubscription)
                .match(SubscribeForPersistedEvents.class, this::subscribeForPersistedEvents)
                .match(CancelStreamingSubscription.class, this::cancelSubscription)
                .build();
    }

    private void requestSubscription(final RequestFromStreamingSubscription requestFromStreamingSubscription) {
        forwardToChild(requestFromStreamingSubscription.getSubscriptionId(), requestFromStreamingSubscription);
    }

    private void cancelSubscription(final CancelStreamingSubscription cancelStreamingSubscription) {
        forwardToChild(cancelStreamingSubscription.getSubscriptionId(), cancelStreamingSubscription);
    }

    private void forwardToChild(final String streamingSubscriptionId, final StreamingSubscriptionCommand<?> command) {
        final Optional<ActorRef> subscriptionActor = getContext().findChild(streamingSubscriptionId);
        if (subscriptionActor.isPresent()) {
            log.withCorrelationId(command).debug("Forwarding to child: <{}>", command);
            subscriptionActor.get().tell(command, getSender());
        } else {
            // most likely a user error
            log.withCorrelationId(command)
                    .info("StreamingSubscriptionID not found, responding with StreamingSubscriptionFailed: <{}>", command);
            final StreamingSubscriptionNotFoundException error =
                    StreamingSubscriptionNotFoundException.of(streamingSubscriptionId, command.getDittoHeaders());
            final StreamingSubscriptionFailed streamingSubscriptionFailed =
                    StreamingSubscriptionFailed.of(streamingSubscriptionId, command.getEntityId(), error, command.getDittoHeaders());
            getSender().tell(streamingSubscriptionFailed, ActorRef.noSender());
        }
    }

    private void subscribeForPersistedEvents(final SubscribeForPersistedEvents subscribeForPersistedEvents) {
        FeatureToggle.checkHistoricalApiAccessFeatureEnabled(
                subscribeForPersistedEvents.getType(), subscribeForPersistedEvents.getDittoHeaders());

        log.withCorrelationId(subscribeForPersistedEvents)
                .info("Processing <{}>", subscribeForPersistedEvents);
        final EntityId entityId = subscribeForPersistedEvents.getEntityId();
        final String subscriptionId = nextSubscriptionId(subscribeForPersistedEvents);
        final Props props = StreamingSubscriptionActor.props(idleTimeout, entityId, getSender(),
                subscribeForPersistedEvents.getDittoHeaders());
        final ActorRef subscriptionActor = getContext().actorOf(props, subscriptionId);
        final Source<JsonValue, ?> itemSource = getPersistedEventsSource(subscribeForPersistedEvents);
        connect(subscriptionActor, itemSource, entityId);
    }

    private void connect(final ActorRef streamingSubscriptionActor,
            final Source<JsonValue, ?> itemSource,
            final EntityId entityId) {
        final Subscriber<JsonValue> subscriber =
                StreamingSubscriptionActor.asSubscriber(streamingSubscriptionActor, entityId);
        lazify(itemSource).runWith(Sink.fromSubscriber(subscriber), materializer);
    }

    private Source<JsonValue, ?> getPersistedEventsSource(final SubscribeForPersistedEvents subscribe) {

        return Source.completionStageSource(
                Patterns.ask(commandForwarder, subscribe, subscribe.getDittoHeaders()
                        .getTimeout()
                        .orElse(COMMAND_FORWARDER_LOCAL_ASK_TIMEOUT)
                )
                .handle((response, throwable) -> {
                    if (response instanceof SourceRef<?> sourceRef) {
                        return sourceRef.getSource()
                                .map(item -> {
                                    if (item instanceof Signal<?> signal) {
                                        return ProtocolFactory.wrapAsJsonifiableAdaptable(
                                                DITTO_PROTOCOL_ADAPTER.toAdaptable(signal)
                                        ).toJson();
                                    } else if (item instanceof Jsonifiable<?> jsonifiable) {
                                        return jsonifiable.toJson();
                                    } else if (item instanceof JsonValue val) {
                                        return val;
                                    } else {
                                        throw new IllegalStateException("Unexpected element!");
                                    }
                                });
                    } else if (response instanceof DittoRuntimeException dittoRuntimeException) {
                        return Source.failed(dittoRuntimeException);
                    } else {
                        final var dittoRuntimeException = DittoRuntimeException
                                .asDittoRuntimeException(throwable,
                                        cause -> DittoInternalErrorException.newBuilder()
                                                .dittoHeaders(subscribe.getDittoHeaders())
                                                .cause(cause)
                                                .build()
                                );
                        return Source.failed(dittoRuntimeException);
                    }
                })
        );
    }

    private String nextSubscriptionId(final SubscribeForPersistedEvents subscribeForPersistedEvents) {
        final String prefix = subscribeForPersistedEvents.getPrefix().orElse("");
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
