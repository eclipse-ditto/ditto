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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.EntityId;
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
    private final List<String> authorizationSubjects;
    private final StreamingSession session;

    SessionedSignal(final Signal<?> signal, final List<String> authorizationSubjects,
            final StreamingSession session) {
        this.signal = signal;
        this.authorizationSubjects = authorizationSubjects;
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
        final EntityId entityId = signal.getEntityId();
        final Optional<JsonFieldSelector> extraFields = session.getExtraFields();
        if (extraFields.isPresent() && (facade == null || !(entityId instanceof ThingId))) {
            final CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(SignalEnrichmentFailedException.newBuilder()
                    .dittoHeaders(signal.getDittoHeaders())
                    .build());
            return future;
        } else if (extraFields.isPresent()) {
            final DittoHeaders headers =
                    signal.getDittoHeaders().toBuilder().authorizationSubjects(authorizationSubjects).build();
            return facade.retrievePartialThing((ThingId) entityId, extraFields.get(), headers, signal);
        } else {
            return CompletableFuture.completedFuture(JsonObject.empty());
        }
    }

    @Override
    public Optional<StreamingSession> getSession() {
        return Optional.of(session);
    }
}
