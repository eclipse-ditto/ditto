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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    // TODO add a config for it
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Map<String, Map<String, List<JsonPointer>>> PARSED_HEADER_CACHE = 
            new ConcurrentHashMap<>(MAX_CACHE_SIZE);

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
        @Nullable final String partialAccessPathsHeader = headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey());
        
        final boolean isPayloadObject = adaptable.getPayload().getValue()
                .map(JsonValue::isObject)
                .orElse(false);
        final JsonPointer eventPath = adaptable.getPayload().getPath();

        if (partialAccessPathsHeader == null || partialAccessPathsHeader.isEmpty()) {
            return adaptable;
        }

        final Map<String, List<JsonPointer>> partialAccessPaths = PARSED_HEADER_CACHE.computeIfAbsent(
                partialAccessPathsHeader,
                header -> {
                    final JsonObject partialAccessPathsJson = JsonObject.of(header);
                    final Map<String, List<JsonPointer>> parsedPaths = 
                            JsonPartialAccessFilter.parsePartialAccessPaths(partialAccessPathsJson);
                    
                    if (PARSED_HEADER_CACHE.size() >= MAX_CACHE_SIZE) {
                        PARSED_HEADER_CACHE.clear();
                    }
                    return parsedPaths;
                });

        if (partialAccessPaths.isEmpty()) {
            return adaptable;
        }

        final TopicPath topicPath = adaptable.getTopicPath();
        if (!TopicPath.Group.THINGS.equals(topicPath.getGroup()) ||
                !TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return adaptable;
        }

        final Set<String> subscriberSubjectIds = new LinkedHashSet<>();
        final Set<String> allSubscriberSubjectIds = new LinkedHashSet<>();

        if (subscriberAuthContext != null && !subscriberAuthContext.getAuthorizationSubjects().isEmpty()) {
            subscriberAuthContext.getAuthorizationSubjects().forEach(subject -> {
                final String subjectId = subject.getId();
                allSubscriberSubjectIds.add(subjectId);
                if (partialAccessPaths.containsKey(subjectId)) {
                    subscriberSubjectIds.add(subjectId);
                }
            });
        } else {
            return createEmptyPayloadAdaptable(adaptable);
        }

        final Set<String> readGrantedSubjectIds = new LinkedHashSet<>();
        headers.getReadGrantedSubjects().forEach(subject -> readGrantedSubjectIds.add(subject.getId()));

        if (subscriberSubjectIds.isEmpty()) {
            final boolean hasUnrestrictedAccess = allSubscriberSubjectIds.stream()
                    .anyMatch(subjectId ->
                            readGrantedSubjectIds.contains(subjectId) &&
                                    !partialAccessPaths.containsKey(subjectId)
                    );

            if (hasUnrestrictedAccess) {
                return adaptable;
            } else {
                return createEmptyPayloadAdaptable(adaptable);
            }
        }

        final Set<JsonPointer> accessiblePaths = new HashSet<>();
        for (final String subjectId : subscriberSubjectIds) {
            final List<JsonPointer> subjectPaths = partialAccessPaths.get(subjectId);
            if (subjectPaths != null && !subjectPaths.isEmpty()) {
                accessiblePaths.addAll(subjectPaths);
            }
        }

        if (accessiblePaths.isEmpty()) {
            return createEmptyPayloadAdaptable(adaptable);
        }

        // For non-object payloads (attribute/feature property changes), check if the path is accessible
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

        final Adaptable result = filterAdaptablePayload(adaptable, accessiblePaths);
        return result;
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

    /**
     * Checks if a path is accessible for non-object payloads (attribute/feature property changes).
     * A path is accessible only if it exactly matches an accessible path.
     * 
     * Note: We don't use prefix matching because if /attributes is accessible but /attributes/private
     * is not in the accessible paths list, it means /attributes/private is denied and should not be accessible.
     *
     * @param path the path to check
     * @param accessiblePaths the set of accessible paths
     * @return true if the path is accessible
     */
    private static boolean isPathAccessibleForNonObjectPayload(final JsonPointer path, final Set<JsonPointer> accessiblePaths) {
        // Check if path exactly matches an accessible path
        if (accessiblePaths.contains(path)) {
            return true;
        }
        
        // Check for root path (allows everything)
        if (accessiblePaths.contains(JsonPointer.empty())) {
            return true;
        }
        
        return false;
    }

}

