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
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;

/**
 * Sessioned Jsonifiable that does not support signal enrichment.
 */
@Immutable
final class SessionedResponseErrorOrAck implements SessionedJsonifiable {

    private final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable;
    private final DittoHeaders dittoHeaders;

    SessionedResponseErrorOrAck(
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final DittoHeaders dittoHeaders) {
        this.jsonifiable = jsonifiable;
        this.dittoHeaders = dittoHeaders;
    }

    @Override
    public Jsonifiable.WithPredicate<JsonObject, JsonField> getJsonifiable() {
        return jsonifiable;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public CompletionStage<JsonObject> retrieveExtraFields(@Nullable final SignalEnrichmentFacade facade) {
        return CompletableFuture.completedFuture(JsonObject.empty());
    }

    @Override
    public Optional<StreamingSession> getSession() {
        return Optional.empty();
    }
}
