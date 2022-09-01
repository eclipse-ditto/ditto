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
package org.eclipse.ditto.internal.models.signalenrichment;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;

import akka.actor.ActorSelection;
import akka.pattern.Patterns;

/**
 * Retrieve fixed parts of things by asking an actor.
 */
public final class ByRoundTripSignalEnrichmentFacade implements SignalEnrichmentFacade {

    private final ActorSelection commandHandler;
    private final Duration askTimeout;

    private ByRoundTripSignalEnrichmentFacade(final ActorSelection commandHandler, final Duration askTimeout) {
        this.commandHandler = checkNotNull(commandHandler, "commandHandler");
        this.askTimeout = checkNotNull(askTimeout, "askTimeout");
    }

    /**
     * Create a signal-enriching facade that retrieves partial things by round-trip.
     *
     * @param commandHandler The recipient of retrieve-thing commands.
     * @param askTimeout How long to wait for each response.
     * @return The facade.
     * @throws java.lang.NullPointerException if any argument is null.
     */
    public static ByRoundTripSignalEnrichmentFacade of(final ActorSelection commandHandler, final Duration askTimeout) {
        return new ByRoundTripSignalEnrichmentFacade(commandHandler, askTimeout);
    }

    @Override
    public CompletionStage<JsonObject> retrievePartialThing(final ThingId thingId,
            @Nullable final JsonFieldSelector jsonFieldSelector,
            final DittoHeaders dittoHeaders,
            @Nullable final Signal<?> concernedSignal) {

        // remove channel header to prevent looping on live messages
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = dittoHeaders.toBuilder()
                .channel(null)
                .putHeader(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED.getKey(), Boolean.TRUE.toString());
        if (dittoHeaders.getCorrelationId().isEmpty()) {
            dittoHeadersBuilder.correlationId(Optional.ofNullable(concernedSignal)
                    .map(Signal::getDittoHeaders)
                    .flatMap(DittoHeaders::getCorrelationId)
                    .orElseGet(() -> UUID.randomUUID().toString()) + "-enrichment");
        }
        final DittoHeaders headersWithoutChannel = dittoHeadersBuilder.build();

        final RetrieveThing command =
                RetrieveThing.getBuilder(thingId, headersWithoutChannel)
                        .withSelectedFields(jsonFieldSelector)
                        .build();

        final CompletionStage<Object> askResult = Patterns.ask(commandHandler, command, askTimeout);

        return askResult.thenCompose(ByRoundTripSignalEnrichmentFacade::extractPartialThing);
    }

    private static CompletionStage<JsonObject> extractPartialThing(final Object object) {
        if (object instanceof RetrieveThingResponse retrieveThingResponse) {
            final JsonSchemaVersion jsonSchemaVersion = retrieveThingResponse.getDittoHeaders()
                    .getSchemaVersion()
                    .orElse(JsonSchemaVersion.LATEST);
            return CompletableFuture.completedFuture(retrieveThingResponse.getEntity(jsonSchemaVersion));
        } else {
            final CompletableFuture<JsonObject> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(toThrowable(object));
            return failedFuture;
        }
    }

    private static Throwable toThrowable(final Object object) {
        if (object instanceof Throwable throwable) {
            return throwable;
        } else {
            return new IllegalStateException("Unexpected message: " + object);
        }
    }
}
