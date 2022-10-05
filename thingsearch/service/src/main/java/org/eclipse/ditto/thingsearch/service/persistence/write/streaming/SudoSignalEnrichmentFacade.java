/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;

import akka.actor.ActorRef;
import akka.pattern.Patterns;

/**
 * Sudo-retrieve things by asking an actor.
 */
final class SudoSignalEnrichmentFacade implements SignalEnrichmentFacade {

    private final ActorRef commandHandler;
    private final Duration askTimeout;

    private SudoSignalEnrichmentFacade(final ActorRef commandHandler, final Duration askTimeout) {
        this.commandHandler = checkNotNull(commandHandler, "commandHandler");
        this.askTimeout = checkNotNull(askTimeout, "askTimeout");
    }

    static SudoSignalEnrichmentFacade of(final ActorRef commandHandler, final Duration askTimeout) {
        return new SudoSignalEnrichmentFacade(commandHandler, askTimeout);
    }

    @Override
    public CompletionStage<JsonObject> retrievePartialThing(final ThingId thingId,
            @Nullable final JsonFieldSelector jsonFieldSelector,
            final DittoHeaders dittoHeaders,
            @Nullable final Signal<?> concernedSignal) {

        final DittoHeaders headersWithCorrelationId;
        if (dittoHeaders.getCorrelationId().isEmpty()) {
            headersWithCorrelationId = dittoHeaders.toBuilder().randomCorrelationId().build();
        } else {
            headersWithCorrelationId = dittoHeaders;
        }
        final SudoRetrieveThing command;
        if (jsonFieldSelector == null) {
            command = SudoRetrieveThing.of(thingId, headersWithCorrelationId);
        } else {
            command = SudoRetrieveThing.of(thingId, jsonFieldSelector, headersWithCorrelationId);
        }

        return Patterns.ask(commandHandler, command, askTimeout).thenCompose(SudoSignalEnrichmentFacade::extractThing);
    }

    private static CompletionStage<JsonObject> extractThing(final Object object) {
        if (object instanceof SudoRetrieveThingResponse) {
            final SudoRetrieveThingResponse response = (SudoRetrieveThingResponse) object;
            return CompletableFuture.completedFuture(response.getEntity(JsonSchemaVersion.LATEST));
        } else if (object instanceof ThingNotAccessibleException) {
            return CompletableFuture.completedFuture(JsonObject.empty());
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
