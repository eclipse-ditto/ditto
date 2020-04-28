/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.streaming.actors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.gateway.streaming.CloseStreamExceptionally;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.actor.AbstractActorPublisherWithStash;
import akka.stream.actor.ActorPublisherMessage;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor publishing {@link Event}s and {@link CommandResponse}s which were sent to him applying backpressure if
 * necessary.
 */
public final class EventAndResponsePublisher extends AbstractActorPublisherWithStash<SessionedJsonifiable> {

    private static final int MESSAGE_CONSUMPTION_CHECK_SECONDS = 2;
    private final DittoDiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final int backpressureBufferSize;
    private final List<SessionedJsonifiable> buffer = new ArrayList<>();
    private final AtomicBoolean currentlyInMessageConsumedCheck = new AtomicBoolean(false);

    @SuppressWarnings("unused")
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
        return Props.create(EventAndResponsePublisher.class, backpressureBufferSize);
    }

    @Override
    public Receive createReceive() {
        // Initially, this Actor can only receive the Connect message:
        return ReceiveBuilder.create()
                .match(Connect.class, connect -> {
                    final String connectionCorrelationId = connect.getConnectionCorrelationId();
                    logger.withCorrelationId(connectionCorrelationId).debug("Established new connection: {}",
                            connectionCorrelationId);
                    getContext().become(connected(connectionCorrelationId));
                })
                .matchAny(any -> {
                    logger.info("Got unknown message during init phase '{}' - stashing..", any);
                    stash();
                }).build();
    }

    private Receive connected(final CharSequence connectionCorrelationId) {
        unstashAll();

        return ReceiveBuilder.create()
                .match(SessionedJsonifiable.class, j -> buffer.size() >= backpressureBufferSize,
                        this::handleBackpressureFor)
                .match(SessionedJsonifiable.class, jsonifiable -> {
                    if (buffer.isEmpty() && totalDemand() > 0) {
                        onNext(jsonifiable);
                    } else {
                        buffer.add(jsonifiable);
                        deliverBuf();
                    }
                })
                .match(CloseStreamExceptionally.class, closeStreamExceptionally -> {
                    final DittoRuntimeException reason = closeStreamExceptionally.getReason();
                    logger.withCorrelationId(closeStreamExceptionally.getConnectionCorrelationId())
                            .info("Closing stream exceptionally because of <{}>.", reason);
                    if (0 < totalDemand()) {
                        onNext(SessionedJsonifiable.error(reason));
                    }
                    onErrorThenStop(reason);
                })
                .match(ActorPublisherMessage.Request.class, request -> {
                    logger.withCorrelationId(connectionCorrelationId).debug("Got new demand: {}", request);
                    deliverBuf();
                })
                .match(ActorPublisherMessage.Cancel.class, cancel -> getContext().stop(getSelf()))
                .matchAny(any -> logger.withCorrelationId(connectionCorrelationId)
                        .warning("Got unknown message during connected phase: '{}'", any))
                .build();
    }

    private void handleBackpressureFor(final SessionedJsonifiable jsonifiable) {
        logger.setCorrelationId(jsonifiable.getDittoHeaders());
        if (currentlyInMessageConsumedCheck.compareAndSet(false, true)) {
            logger.warning(
                    "Backpressure - buffer of '{}' outstanding Events/CommandResponses is full, dropping '{}'",
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
        while (totalDemand() > 0) {
            /*
             * totalDemand is a Long and could be larger than
             * what buffer.splitAt can accept
             */
            if (totalDemand() <= Integer.MAX_VALUE) {
                final List<SessionedJsonifiable> took = buffer.subList(0, Math.min(buffer.size(), (int) totalDemand()));
                took.forEach(this::onNext);
                buffer.removeAll(took);
                break;
            } else {
                final List<SessionedJsonifiable> took = buffer.subList(0, Math.min(buffer.size(), Integer.MAX_VALUE));
                took.forEach(this::onNext);
                buffer.removeAll(took);
            }
        }
    }

}
