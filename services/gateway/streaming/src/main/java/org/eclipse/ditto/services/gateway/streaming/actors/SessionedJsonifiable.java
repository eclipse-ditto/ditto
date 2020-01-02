/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.things.SignalEnrichmentFacade;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

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
    CompletionStage<JsonObject> retrieveExtraFields(@Nullable final SignalEnrichmentFacade facade);

    /**
     * Retrieve JSON pointers for extra fields if any is defined for the session.
     *
     * @return the JSON pointers.
     */
    Iterable<JsonPointer> getPointers();

    /**
     * Create a sessioned Jsonifiable for a signal.
     *
     * @param signal the signal.
     * @param authorizationSubjects the authorization subjects of the streaming session.
     * @param extraFields extra fields of the streaming session.
     * @return the sessioned Jsonifiable.
     */
    static SessionedJsonifiable signal(final Signal<?> signal, final List<String> authorizationSubjects,
            @Nullable final JsonFieldSelector extraFields) {
        return new SessionedSignal(signal, authorizationSubjects, extraFields);
    }

    /**
     * Create a sessioned Jsonifiable for an error.
     *
     * @param error the error.
     * @return the sessioned Jsonifiable.
     */
    static SessionedJsonifiable error(final DittoRuntimeException error) {
        return new SessionedResponseErrorOrAck(error, error.getDittoHeaders());
    }

    /**
     * Create a sessioned Jsonifiable for a response.
     *
     * @param response the response.
     * @return the sessioned Jsonifiable.
     */
    static SessionedJsonifiable response(final CommandResponse<?> response) {
        return new SessionedResponseErrorOrAck(response, response.getDittoHeaders());
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
            final String connectionCorrelationId) {
        return new SessionedResponseErrorOrAck(
                new StreamingAck(streamingType, subscribed),
                DittoHeaders.newBuilder().correlationId(connectionCorrelationId).build()
        );
    }

    /**
     * Sessioned Jsonifiable that supports signal enrichment.
     */
    @Immutable
    final class SessionedSignal implements SessionedJsonifiable {

        private final Signal<?> signal;
        private final List<String> authorizationSubjects;
        @Nullable private final JsonFieldSelector extraFields;

        private SessionedSignal(final Signal<?> signal, final List<String> authorizationSubjects,
                @Nullable final JsonFieldSelector extraFields) {
            this.signal = signal;
            this.authorizationSubjects = authorizationSubjects;
            this.extraFields = extraFields;
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
            final CompletionStage<JsonObject> retrievalStage;
            if (extraFields != null && (facade == null || !(entityId instanceof ThingId))) {
                final CompletableFuture<JsonObject> future = new CompletableFuture<>();
                future.completeExceptionally(SignalEnrichmentFailedException.newBuilder()
                        .dittoHeaders(signal.getDittoHeaders())
                        .build());
                return future;
            } else if (extraFields != null) {
                final DittoHeaders headers =
                        signal.getDittoHeaders().toBuilder().authorizationSubjects(authorizationSubjects).build();
                return facade.retrievePartialThing((ThingId) entityId, extraFields, headers);
            } else {
                return CompletableFuture.completedFuture(JsonObject.empty());
            }
        }

        @Override
        public Iterable<JsonPointer> getPointers() {
            return Optional.<Iterable<JsonPointer>>ofNullable(extraFields).orElseGet(Collections::emptyList);
        }
    }

    /**
     * Sessioned Jsonifiable that does not support signal enrichment.
     */
    @Immutable
    final class SessionedResponseErrorOrAck implements SessionedJsonifiable {

        private final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable;
        private final DittoHeaders dittoHeaders;

        private SessionedResponseErrorOrAck(
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
        public Iterable<JsonPointer> getPointers() {
            return Collections.emptyList();
        }
    }
}
