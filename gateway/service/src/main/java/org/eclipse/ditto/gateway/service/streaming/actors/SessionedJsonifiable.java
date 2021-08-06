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
package org.eclipse.ditto.gateway.service.streaming.actors;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.gateway.service.streaming.StreamingAck;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.TracingTags;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;

/**
 * Jsonifiable with information from the streaming session.
 */
public interface SessionedJsonifiable {

    /**
     * Retrieve the Jsonifiable.
     *
     * @return the Jsonifiable.
     */
    Jsonifiable.WithPredicate<JsonObject, JsonField> getJsonifiable();

    /**
     * Retrieve the Ditto headers of the Jsonifiable.
     *
     * @return the Ditto headers.
     */
    DittoHeaders getDittoHeaders();

    /**
     * Retrieve extra fields for the Jsonifiable according to the session information.
     * If the Jsonifiable does not support extra fields, then a future empty Json object is returned.
     * If the Jsonifiable supports extra fields but retrieval is impossible, then a failed future is returned.
     *
     * @param facade the signal enrichment facade with which extra fields are retrieved.
     * @return future of the result of retrieval.
     */
    CompletionStage<JsonObject> retrieveExtraFields(@Nullable SignalEnrichmentFacade facade);

    /**
     * Retrieve the streaming session if this is a signal and has an associated session.
     *
     * @return the session if any exists.
     */
    Optional<StreamingSession> getSession();

    /**
     * Finish a started trace.
     */
    void finishTrace();

    /**
     * Create a sessioned Jsonifiable for a signal.
     *
     * @param signal the signal.
     * @param sessionHeaders headers of the request that created the streaming session.
     * @param session session information for the signal's streaming type.
     * @return the sessioned Jsonifiable.
     */
    static SessionedJsonifiable signal(final Signal<?> signal, final DittoHeaders sessionHeaders,
            final StreamingSession session) {
        final StartedTrace trace = DittoTracing.trace(signal, "gw.streaming.out.signal")
                .tag(TracingTags.SIGNAL_TYPE, signal.getType())
                .start();
        final Signal<?> tracedSignal = DittoTracing.propagateContext(trace.getContext(), signal);
        return new SessionedSignal(tracedSignal, sessionHeaders, session, trace);
    }

    /**
     * Create a sessioned Jsonifiable for an error.
     *
     * @param error the error.
     * @return the sessioned Jsonifiable.
     */
    static SessionedJsonifiable error(final DittoRuntimeException error) {
        final StartedTrace trace = DittoTracing.trace(error, "gw.streaming.out.error")
                .start();
        trace.fail(error);
        final DittoRuntimeException tracedError = DittoTracing.propagateContext(trace.getContext(), error);
        return new SessionedResponseErrorOrAck(tracedError, error.getDittoHeaders(), trace);
    }

    /**
     * Create a sessioned Jsonifiable for a response.
     *
     * @param response the response.
     * @return the sessioned Jsonifiable.
     */
    static SessionedJsonifiable response(final CommandResponse<?> response) {
        final StartedTrace trace = DittoTracing.trace(response, "gw.streaming.out.response")
                .tag(TracingTags.SIGNAL_TYPE, response.getType())
                .start();
        final CommandResponse<?> tracedResponse = DittoTracing.propagateContext(trace.getContext(), response);
        return new SessionedResponseErrorOrAck(tracedResponse, response.getDittoHeaders(), trace);
    }

    /**
     * Create a sessioned Jsonifiable for a {@link org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent}
     * as response.
     *
     * @param subscriptionEvent the {@link org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent} as response.
     * @return the sessioned Jsonifiable.
     */
    static SessionedJsonifiable subscription(final SubscriptionEvent<?> subscriptionEvent) {
        return new SessionedResponseErrorOrAck(subscriptionEvent, subscriptionEvent.getDittoHeaders(), null);
    }

    /**
     * Create a sessioned Jsonifiable for a stream acknowledgement.
     *
     * @param streamingType the streaming type.
     * @param subscribed whether a subscription or an unsubscription is acknowledged..
     * @param connectionCorrelationId the correlation ID of the streaming session.
     * @return the sessioned Jsonifiable.
     */
    static SessionedJsonifiable ack(final StreamingType streamingType, final boolean subscribed,
            final CharSequence connectionCorrelationId) {
        return new SessionedResponseErrorOrAck(
                new StreamingAck(streamingType, subscribed),
                DittoHeaders.newBuilder().correlationId(connectionCorrelationId).build(), null);
    }

}
