/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.protocol;

import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Utility for filtering {@link Adaptable} payloads based on partial access paths.
 */
public final class AdaptablePartialAccessFilter {

    private AdaptablePartialAccessFilter() {
        // No instantiation
    }

    /**
     * Filters an {@link Adaptable} payload based on partial access paths.
     *
     * @param adaptable the adaptable to filter
     * @param subscriberAuthContext the authorization context of the subscriber (may be null)
     * @return filtered adaptable, or original if no filtering is needed
     */
    public static Adaptable filterAdaptableForPartialAccess(
            final Adaptable adaptable,
            @Nullable final AuthorizationContext subscriberAuthContext) {

        final DittoHeaders headers = adaptable.getDittoHeaders();
        final String partialAccessPathsHeader = headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey());
        if (partialAccessPathsHeader == null || partialAccessPathsHeader.isEmpty()) {
            return adaptable;
        }

        final TopicPath topicPath = adaptable.getTopicPath();
        if (!TopicPath.Group.THINGS.equals(topicPath.getGroup()) ||
                !TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return adaptable;
        }

        final PartialAccessPathResolver.AccessiblePathsResult result =
                PartialAccessPathResolver.resolveAccessiblePathsFromHeader(
                        partialAccessPathsHeader, subscriberAuthContext, headers);

        if (result.hasUnrestrictedAccess()) {
            return adaptable;
        }

        if (!result.shouldFilter()) {
            return createEmptyPayloadAdaptable(adaptable);
        }

        final Set<JsonPointer> accessiblePaths = result.getAccessiblePaths();
        final boolean isPayloadObject = adaptable.getPayload().getValue()
                .map(JsonValue::isObject)
                .orElse(false);
        final JsonPointer eventPath = adaptable.getPayload().getPath();

        if (!isPayloadObject) {
            final PathTrie pathTrie = PathTrie.fromPaths(accessiblePaths);
            final boolean isExactMatch = pathTrie.isExactMatch(eventPath);
            final boolean hasAccessibleDescendant = pathTrie.hasAccessibleDescendant(eventPath);
            final boolean isPathAccessible = isExactMatch || hasAccessibleDescendant;
            if (!isPathAccessible) {
                return createEmptyPayloadAdaptable(adaptable);
            }
            return adaptable;
        }

        final Adaptable filteredAdaptable = filterAdaptablePayload(adaptable, accessiblePaths);
        return filteredAdaptable;
    }

    private static Adaptable createEmptyPayloadAdaptable(final Adaptable adaptable) {
        return ProtocolFactory.newAdaptableBuilder(adaptable)
                .withPayload(Payload.newBuilder(adaptable.getPayload())
                        .withValue(JsonFactory.newObject())
                        .build())
                .build();
    }

    private static Adaptable filterAdaptablePayload(
            final Adaptable adaptable,
            final Set<JsonPointer> accessiblePaths) {

        final JsonObject originalPayload = adaptable.getPayload().getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonFactory.newObject());

        final JsonObject filteredPayload = JsonPartialAccessFilter.filterJsonByPaths(
                originalPayload, accessiblePaths);

        return ProtocolFactory.newAdaptableBuilder(adaptable)
                .withPayload(Payload.newBuilder(adaptable.getPayload())
                        .withValue(filteredPayload)
                        .build())
                .build();
    }

}
