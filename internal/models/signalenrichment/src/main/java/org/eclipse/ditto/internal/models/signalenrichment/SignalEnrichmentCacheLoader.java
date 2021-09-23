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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

/**
 * Loads partial things by using the passed in {@code SignalEnrichmentFacade}.
 */
@AllValuesAreNonnullByDefault
final class SignalEnrichmentCacheLoader implements AsyncCacheLoader<SignalEnrichmentCacheKey, JsonObject> {

    private final SignalEnrichmentFacade facade;

    private SignalEnrichmentCacheLoader(final SignalEnrichmentFacade facade) {
        this.facade = facade;
    }

    /**
     * Creates a new cache loader which uses the passed {@code facade} in order to retrieve partial things.
     *
     * @param facade the SignalEnrichmentFacade to delegate loading of partial things to.
     * @return the instantiated cache loader.
     */
    static SignalEnrichmentCacheLoader of(final SignalEnrichmentFacade facade) {
        return new SignalEnrichmentCacheLoader(facade);
    }

    @Override
    public CompletableFuture<JsonObject> asyncLoad(final SignalEnrichmentCacheKey key, final Executor executor) {
        final Optional<SignalEnrichmentContext> contextOptional = key.getCacheLookupContext();
        final Optional<JsonFieldSelector> selectorOptional =
                contextOptional.flatMap(SignalEnrichmentContext::getJsonFieldSelector);
        if (contextOptional.isPresent()) {
            final SignalEnrichmentContext context = contextOptional.get();
            final ThingId thingId = ThingId.of(key.getId());
            final JsonFieldSelector jsonFieldSelector = selectorOptional.orElse(null);
            final DittoHeaders dittoHeaders = context.getDittoHeaders();
            return facade.retrievePartialThing(thingId, jsonFieldSelector, dittoHeaders, null)
                    .toCompletableFuture();
        } else {
            // no context; nothing to load.
            return CompletableFuture.completedFuture(JsonObject.empty());
        }
    }
}
