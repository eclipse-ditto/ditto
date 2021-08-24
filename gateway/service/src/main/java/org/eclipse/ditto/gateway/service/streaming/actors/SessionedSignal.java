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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Sessioned Jsonifiable that supports signal enrichment.
 */
@Immutable
final class SessionedSignal implements SessionedJsonifiable {

    private final Signal<?> signal;
    private final DittoHeaders sessionHeaders;
    private final StreamingSession session;
    private final StartedTrace trace;

    SessionedSignal(final Signal<?> signal, final DittoHeaders sessionHeaders, final StreamingSession session,
            final StartedTrace trace) {
        this.signal = signal;
        this.sessionHeaders = sessionHeaders;
        this.session = session;
        this.trace = trace;
    }

    @Override
    public Jsonifiable.WithPredicate<JsonObject, JsonField> getJsonifiable() {
        return signal;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return signal.getDittoHeaders();
    }

    @Override
    public CompletionStage<JsonObject> retrieveExtraFields(@Nullable final SignalEnrichmentFacade facade) {
        final Optional<ThingFieldSelector> extraFields = session.getExtraFields();
        if (extraFields.isPresent()) {
            final Optional<ThingId> thingIdOptional = WithEntityId.getEntityIdOfType(ThingId.class, signal);
            if (facade != null && thingIdOptional.isPresent()) {
                return facade.retrievePartialThing(thingIdOptional.get(), extraFields.get(), sessionHeaders, signal);
            }
            final CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(SignalEnrichmentFailedException.newBuilder()
                    .dittoHeaders(signal.getDittoHeaders())
                    .build());
            session.getLogger().withCorrelationId(signal)
                    .warning("Completing extraFields retrieval with SignalEnrichmentFailedException, " +
                            "facade: <{}> - thingId: <{}>", facade, thingIdOptional.orElse(null));
            return future;
        } else {
            return CompletableFuture.completedFuture(JsonObject.empty());
        }
    }

    @Override
    public Optional<StreamingSession> getSession() {
        return Optional.of(session);
    }

    @Override
    public void finishTrace() {
        trace.finish();
    }
}
