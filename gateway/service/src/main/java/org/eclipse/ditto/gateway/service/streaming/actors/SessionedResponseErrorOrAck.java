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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;

/**
 * Sessioned Jsonifiable that does not support signal enrichment.
 */
@Immutable
final class SessionedResponseErrorOrAck implements SessionedJsonifiable {

    private final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable;
    private final DittoHeaders dittoHeaders;
    @Nullable private final StartedSpan startedSpan;

    SessionedResponseErrorOrAck(
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final DittoHeaders dittoHeaders,
            @Nullable final StartedSpan startedSpan
    ) {
        this.jsonifiable = jsonifiable;
        this.dittoHeaders = dittoHeaders;
        this.startedSpan = startedSpan;
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

    @Override
    public void finishSpan() {
        if (null != startedSpan) {
            startedSpan.finish();
        }
    }
}
