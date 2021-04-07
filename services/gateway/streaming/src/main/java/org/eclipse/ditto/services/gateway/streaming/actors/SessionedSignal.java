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
package org.eclipse.ditto.services.gateway.streaming.actors;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.WithEntityId;
import org.eclipse.ditto.model.base.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Sessioned Jsonifiable that supports signal enrichment.
 */
@Immutable
final class SessionedSignal implements SessionedJsonifiable {

    private final Signal<?> signal;
    private final DittoHeaders sessionHeaders;
    private final StreamingSession session;

    SessionedSignal(final Signal<?> signal, final DittoHeaders sessionHeaders, final StreamingSession session) {
        this.signal = signal;
        this.sessionHeaders = sessionHeaders;
        this.session = session;
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
        final Optional<JsonFieldSelector> extraFields = session.getExtraFields();
        if (extraFields.isPresent()) {
            final Optional<ThingId> thingIdOptional = WithEntityId.getEntityIdOfType(ThingId.class, signal);
            if (facade != null && thingIdOptional.isPresent()) {
                return facade.retrievePartialThing(thingIdOptional.get(), extraFields.get(), sessionHeaders, signal);
            }
            final CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(SignalEnrichmentFailedException.newBuilder()
                    .dittoHeaders(signal.getDittoHeaders())
                    .build());
            return future;
        } else {
            return CompletableFuture.completedFuture(JsonObject.empty());
        }
    }

    @Override
    public Optional<StreamingSession> getSession() {
        return Optional.of(session);
    }
}
