/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * Adds the READ restrictions of subscribers to a live event, which unlike a twin event is not enriched by the
 * persistence actor. They are calculated on the payload of the event only, as only the payload is delivered.
 *
 * @since 3.9.9
 */
public final class LiveEventAccessPaths {

    private LiveEventAccessPaths() {
        throw new AssertionError();
    }

    /**
     * Adds the {@code ditto-partial-access-paths} and {@code ditto-revoked-access-paths} headers to a live event.
     *
     * @param liveEvent the authorized live event.
     * @param policyEnforcer the policy enforcer of the Thing, filtered for the Thing's namespace.
     * @param partialAccessEventsEnabled whether subjects with partial READ permissions receive the event.
     * @return the live event with the headers, or the live event itself if READ is restricted for nobody.
     */
    public static ThingEvent<?> addTo(final ThingEvent<?> liveEvent, final PolicyEnforcer policyEnforcer,
            final boolean partialAccessEventsEnabled) {

        final JsonPointer resourcePath = liveEvent.getResourcePath();
        // deletions carry no entity; their path must still be checked
        @Nullable final JsonObject payloadAsThing = liveEvent.getEntity()
                .or(() -> resourcePath.isEmpty() ? Optional.<JsonValue>empty() : Optional.of(JsonValue.nullLiteral()))
                .map(value -> toThingJson(resourcePath, value))
                .orElse(null);
        if (payloadAsThing == null || payloadAsThing.isEmpty()) {
            return liveEvent;
        }
        final DittoHeadersBuilder<?, ?> headersBuilder = liveEvent.getDittoHeaders().toBuilder();
        boolean restricted = false;
        if (partialAccessEventsEnabled) {
            final Map<String, List<JsonPointer>> partialAccessPaths =
                    PartialAccessPathCalculator.calculatePartialAccessPaths(policyEnforcer, payloadAsThing);
            if (!partialAccessPaths.isEmpty()) {
                headersBuilder.putJsonHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        PartialAccessPathCalculator.toIndexedJsonObject(partialAccessPaths));
                restricted = true;
            }
        }
        final Map<String, List<JsonPointer>> revokedAccessPaths =
                RevokedAccessPathCalculator.calculateRevokedAccessPaths(payloadAsThing, policyEnforcer);
        if (!revokedAccessPaths.isEmpty()) {
            headersBuilder.putJsonHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(),
                    PartialAccessPathCalculator.toIndexedJsonObject(revokedAccessPaths));
            restricted = true;
        }
        return restricted ? liveEvent.setDittoHeaders(headersBuilder.build()) : liveEvent;
    }

    @Nullable
    private static JsonObject toThingJson(final JsonPointer resourcePath, final JsonValue value) {
        if (resourcePath.isEmpty()) {
            return value.isObject() && !value.isNull() ? value.asObject() : null;
        }
        return JsonObject.empty().setValue(resourcePath, value);
    }

}
