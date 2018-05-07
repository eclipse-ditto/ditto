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
package org.eclipse.ditto.services.gateway.streaming.actors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.actor.AbstractActorPublisher;
import akka.stream.actor.ActorPublisherMessage;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor publishing {@link Event}s and {@link CommandResponse}s which were sent to him applying backpressure if
 * necessary.
 */
public final class EventAndResponsePublisher
        extends AbstractActorPublisher<Jsonifiable.WithPredicate<JsonObject, JsonField>> {

    private static final int MESSAGE_CONSUMPTION_CHECK_SECONDS = 2;
    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);
    private final int backpressureBufferSize;
    private final List<Jsonifiable.WithPredicate<JsonObject, JsonField>> buffer = new ArrayList<>();
    private final AtomicBoolean currentlyInMessageConsumedCheck = new AtomicBoolean(false);
    private String connectionCorrelationId;

    private EventAndResponsePublisher(final int backpressureBufferSize) {
        this.backpressureBufferSize = backpressureBufferSize;
    }

    /**
     * Creates Akka configuration object Props for this EventAndResponsePublisher.
     *
     * @param backpressureBufferSize the max buffer size of how many outstanding CommandResponses and Events a single
     * consumer may have - additionally incoming CommandResponses and Events are dropped if this size is reached.
     * @return the Akka configuration Props object.
     */
    public static Props props(final int backpressureBufferSize) {
        return Props.create(EventAndResponsePublisher.class, new Creator<EventAndResponsePublisher>() {
            private static final long serialVersionUID = 1L;

            @Override
            public EventAndResponsePublisher create() {
                return new EventAndResponsePublisher(backpressureBufferSize);
            }
        });
    }

    @Override
    public Receive createReceive() {
        // Initially, this Actor can only receive the Connect message:
        return ReceiveBuilder.create()
                .match(Connect.class, connect -> {
                    final String connectionCorrelationId = connect.getConnectionCorrelationId();
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.debug("Established new connection: {}", connectionCorrelationId);
                    getContext().become(connected(connectionCorrelationId));
                })
                .matchAny(any -> logger.warning("Got unknown message during init phase '{}'", any)).build();
    }

    private Receive connected(final String connectionCorrelationId) {
        this.connectionCorrelationId = connectionCorrelationId;

        return ReceiveBuilder.create()
                .match(Signal.class, signal -> buffer.size() >= backpressureBufferSize, signal -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, signal);
                    handleBackpressureFor((Signal<?>) signal);
                })
                .match(Signal.class, signal -> {
                    if (buffer.isEmpty() && totalDemand() > 0) {
                        onNext((Signal<?>) signal);
                    } else {
                        buffer.add((Signal<?>) signal);
                        deliverBuf();
                    }
                })
                .match(DittoRuntimeException.class, cre -> buffer.size() >= backpressureBufferSize, cre -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, cre.getDittoHeaders().getCorrelationId());
                    handleBackpressureFor(cre);
                })
                .match(DittoRuntimeException.class, cre -> {
                    if (buffer.isEmpty() && totalDemand() > 0) {
                        onNext(cre);
                    } else {
                        buffer.add(cre);
                        deliverBuf();
                    }
                })
                .match(Jsonifiable.WithPredicate.class, signal -> buffer.size() >= backpressureBufferSize,
                        this::handleBackpressureFor)
                .match(Jsonifiable.WithPredicate.class, jsonifiable -> {
                    if (buffer.isEmpty() && totalDemand() > 0) {
                        onNext(jsonifiable);
                    } else {
                        buffer.add(jsonifiable);
                        deliverBuf();
                    }
                })
                .match(ActorPublisherMessage.Request.class, request -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.debug("Got new demand: {}", request);
                    deliverBuf();
                })
                .match(ActorPublisherMessage.Cancel.class, cancel -> getContext().stop(getSelf()))
                .matchAny(any -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.warning("Got unknown message during connected phase: '{}'", any);
                })
                .build();
    }

    private void handleBackpressureFor(final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {
        if (currentlyInMessageConsumedCheck.compareAndSet(false, true)) {
            logger.warning("Backpressure - buffer of '{}' outstanding Events/CommandResponses is full, dropping '{}'",
                    backpressureBufferSize, jsonifiable);

            final long bufSize = buffer.size();
            final ActorContext context = getContext();
            context.system().scheduler().scheduleOnce(FiniteDuration.apply(MESSAGE_CONSUMPTION_CHECK_SECONDS,
                    TimeUnit.SECONDS), () -> {
                if (bufSize == buffer.size()) {
                    logger.warning(
                            "Terminating Publisher - did not to consume anything in the last '{}' seconds, buffer "
                                    + "is still at '{}' outstanding messages", MESSAGE_CONSUMPTION_CHECK_SECONDS,
                            bufSize);
                    context.stop(getSelf());
                } else {
                    currentlyInMessageConsumedCheck.set(false);
                    logger.info("Outstanding messages were consumed, Publisher is not terminated");
                }
            }, context.system().dispatcher());
        }
    }

    private void deliverBuf() {
        LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
        while (totalDemand() > 0) {
        /*
         * totalDemand is a Long and could be larger than
         * what buffer.splitAt can accept
         */
            if (totalDemand() <= Integer.MAX_VALUE) {
                final List<Jsonifiable.WithPredicate<JsonObject, JsonField>> took =
                        buffer.subList(0, Math.min(buffer.size(), (int) totalDemand()));
                took.forEach(this::onNext);
                buffer.removeAll(took);
                break;
            } else {
                final List<Jsonifiable.WithPredicate<JsonObject, JsonField>> took =
                        buffer.subList(0, Math.min(buffer.size(), Integer.MAX_VALUE));
                took.forEach(this::onNext);
                buffer.removeAll(took);
            }
        }
    }

}
