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
package org.eclipse.ditto.services.models.signalenrichment;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;

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
            final JsonFieldSelector jsonFieldSelector,
            final DittoHeaders dittoHeaders,
            @Nullable final Signal<?> concernedSignal) {

        // remove channel header to prevent looping on live messages
        final DittoHeaders headersWithoutChannel = dittoHeaders.toBuilder().channel(null).build();

        final RetrieveThing command =
                RetrieveThing.getBuilder(thingId, headersWithoutChannel)
                        .withSelectedFields(jsonFieldSelector)
                        .build();

        final CompletionStage<Object> askResult = Patterns.ask(commandHandler, command, askTimeout);

        return askResult.thenCompose(ByRoundTripSignalEnrichmentFacade::extractPartialThing);
    }

    private static CompletionStage<JsonObject> extractPartialThing(final Object object) {
        if (object instanceof RetrieveThingResponse) {
            final RetrieveThingResponse retrieveThingResponse = (RetrieveThingResponse) object;
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
        if (object instanceof Throwable) {
            return (Throwable) object;
        } else {
            return new IllegalStateException("Unexpected message: " + object);
        }
    }
}
