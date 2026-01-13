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
package org.eclipse.ditto.internal.utils.protocol;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.eclipse.ditto.protocol.PayloadBuilder;
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

        final Map<String, List<JsonPointer>> partialAccessPaths =
                PartialAccessPathResolver.parsePartialAccessPathsAsMap(partialAccessPathsHeader);
        return filterAdaptableForPartialAccess(adaptable, subscriberAuthContext, partialAccessPaths);
    }

    /**
     * Filters an {@link Adaptable} payload based on partial access paths that are already parsed.
     *
     * @param adaptable the adaptable to filter
     * @param subscriberAuthContext the authorization context of the subscriber (may be null)
     * @param partialAccessPaths parsed partial access paths (subject ID -> paths)
     * @return filtered adaptable, or original if no filtering is needed
     */
    public static Adaptable filterAdaptableForPartialAccess(
            final Adaptable adaptable,
            @Nullable final AuthorizationContext subscriberAuthContext,
            final Map<String, List<JsonPointer>> partialAccessPaths) {

        if (partialAccessPaths.isEmpty()) {
            return adaptable;
        }

        final TopicPath topicPath = adaptable.getTopicPath();
        if (!TopicPath.Group.THINGS.equals(topicPath.getGroup()) ||
                !TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return adaptable;
        }

        final PartialAccessPathResolver.AccessiblePathsResult result =
                PartialAccessPathResolver.resolveAccessiblePaths(
                        partialAccessPaths, subscriberAuthContext, adaptable.getDittoHeaders().getReadGrantedSubjects());

        return filterAdaptableWithResult(adaptable, result);
    }

    /**
     * Filters an {@link Adaptable} payload based on a pre-resolved {@link PartialAccessPathResolver.AccessiblePathsResult}.
     * This method is useful when you want to filter based on all partial-access subjects (union of paths)
     * rather than a specific subscriber's context.
     *
     * @param adaptable the adaptable to filter
     * @param result the pre-resolved accessible paths result
     * @return filtered adaptable, or original if no filtering is needed
     */
    public static Adaptable filterAdaptableWithResult(
            final Adaptable adaptable,
            final PartialAccessPathResolver.AccessiblePathsResult result) {

        final TopicPath topicPath = adaptable.getTopicPath();
        if (!TopicPath.Group.THINGS.equals(topicPath.getGroup()) ||
                !TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return adaptable;
        }

        if (result.hasUnrestrictedAccess()) {
            return adaptable;
        }

        if (!result.shouldFilter()) {
            return createEmptyPayloadAdaptable(adaptable);
        }

        final Set<JsonPointer> accessiblePaths = result.getAccessiblePaths();
        final boolean isPayloadObject = adaptable.getPayload().getValue()
                // JsonNull reports isObject()==true (and can be coerced to JsonObjectNull),
                // but we must not treat "null" payloads as objects for filtering purposes.
                .map(value -> value.isObject() && !value.isNull())
                .orElse(false);
        final JsonPointer eventPath = JsonPointer.of(adaptable.getPayload().getPath().toString());

        if (!isPayloadObject) {
            final boolean isPathAccessible = isPathAccessibleForNonObjectPayload(eventPath, accessiblePaths);
            if (!isPathAccessible) {
                return createEmptyPayloadAdaptable(adaptable);
            }
            return filterExtraFields(adaptable, accessiblePaths);
        }

        return filterAdaptablePayload(adaptable, accessiblePaths);
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

        final PayloadBuilder payloadBuilder = Payload.newBuilder(adaptable.getPayload())
                .withValue(filteredPayload);
        filterExtraFieldsIntoBuilder(adaptable, accessiblePaths, payloadBuilder);

        return ProtocolFactory.newAdaptableBuilder(adaptable)
                .withPayload(payloadBuilder.build())
                .build();
    }


    private static Adaptable filterExtraFields(
            final Adaptable adaptable,
            final Set<JsonPointer> accessiblePaths) {

        final PayloadBuilder payloadBuilder = Payload.newBuilder(adaptable.getPayload());
        filterExtraFieldsIntoBuilder(adaptable, accessiblePaths, payloadBuilder);

        return ProtocolFactory.newAdaptableBuilder(adaptable)
                .withPayload(payloadBuilder.build())
                .build();
    }


    private static void filterExtraFieldsIntoBuilder(
            final Adaptable adaptable,
            final Set<JsonPointer> accessiblePaths,
            final PayloadBuilder payloadBuilder) {

        final Optional<JsonObject> originalExtra = adaptable.getPayload().getExtra();
        if (originalExtra.isEmpty()) {
            return;
        }

        final JsonObject filteredExtra = JsonPartialAccessFilter.filterJsonByPaths(
                originalExtra.get(), accessiblePaths);

        if (filteredExtra.isEmpty()) {
            return;
        }

        payloadBuilder.withExtra(filteredExtra);
    }

    private static boolean isPathAccessibleForNonObjectPayload(final JsonPointer eventPath,
            final Set<JsonPointer> accessiblePaths) {
        if (accessiblePaths.isEmpty()) {
            return false;
        }

        if (accessiblePaths.contains(eventPath)) {
            return true;
        }

        if (accessiblePaths.contains(JsonPointer.empty())) {
            return true;
        }

        return false;
    }

}
