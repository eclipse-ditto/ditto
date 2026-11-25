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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * A parsed {@code ditto-partial-access-paths} or {@code ditto-revoked-access-paths} header in the indexed format
 * {@code {"subjects":[...],"paths":{"attributes/secret":[0]},"root":[0]}}; {@code root} is optional and only used
 * by the revoked header. A header which cannot be parsed completely is {@link #getMalformedReason() malformed}, which
 * callers must treat as "nothing readable".
 */
final class AccessPathsHeader {

    static final AccessPathsHeader ABSENT = new AccessPathsHeader(Map.of(), Set.of(), null);

    private static final String SUBJECTS_KEY = "subjects";
    private static final String ROOT_KEY = "root";

    private final Map<String, List<JsonPointer>> pathsBySubject;
    private final Set<String> rootSubjects;
    @Nullable private final String malformedReason;

    private AccessPathsHeader(final Map<String, List<JsonPointer>> pathsBySubject, final Set<String> rootSubjects,
            @Nullable final String malformedReason) {
        this.pathsBySubject = pathsBySubject;
        this.rootSubjects = rootSubjects;
        this.malformedReason = malformedReason;
    }

    static AccessPathsHeader parse(@Nullable final String headerValue) {
        if (headerValue == null || headerValue.isEmpty()) {
            return ABSENT;
        }
        try {
            final JsonValue json = JsonFactory.readFrom(headerValue);
            if (!json.isObject() || json.isNull()) {
                return malformed("not a JSON object");
            }
            final JsonObject jsonObject = json.asObject();
            final JsonValue subjectsValue = jsonObject.getValue(SUBJECTS_KEY).orElse(null);
            if (subjectsValue == null || !subjectsValue.isArray() ||
                    !subjectsValue.asArray().stream().allMatch(JsonValue::isString)) {
                return malformed("no subjects array");
            }
            final IndexedPartialAccessPaths indexed = JsonPartialAccessFilter.parseIndexedPartialAccessPaths(
                    jsonObject);
            final int subjectCount = indexed.subjects().size();
            final boolean invalidIndex = indexed.paths().values().stream()
                    .flatMap(List::stream)
                    .anyMatch(index -> index < 0 || index >= subjectCount);
            if (invalidIndex) {
                return malformed("subject index out of range");
            }
            final Set<String> rootSubjects = new HashSet<>();
            final JsonValue rootValue = jsonObject.getValue(ROOT_KEY).orElse(null);
            if (rootValue != null) {
                if (!rootValue.isArray()) {
                    return malformed("root is no array");
                }
                for (final JsonValue index : rootValue.asArray()) {
                    if (!index.isInt() || index.asInt() < 0 || index.asInt() >= subjectCount) {
                        return malformed("root subject index out of range");
                    }
                    rootSubjects.add(indexed.subjects().get(index.asInt()));
                }
            }
            return new AccessPathsHeader(JsonPartialAccessFilter.expandIndexed(indexed),
                    Collections.unmodifiableSet(rootSubjects), null);
        } catch (final RuntimeException e) {
            return malformed(String.valueOf(e.getMessage()));
        }
    }

    private static AccessPathsHeader malformed(final String reason) {
        return new AccessPathsHeader(Map.of(), Set.of(), reason);
    }

    Map<String, List<JsonPointer>> getPathsBySubject() {
        return pathsBySubject;
    }

    Set<String> getRootSubjects() {
        return rootSubjects;
    }

    /**
     * @return why the header is malformed, empty if it is not.
     */
    Optional<String> getMalformedReason() {
        return Optional.ofNullable(malformedReason);
    }

}
