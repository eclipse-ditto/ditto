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
package org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors;

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.DOT;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_GRANTED;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REVOKED;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.persistence.mongo.KeyNameReviser;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;

import com.mongodb.client.model.Filters;

/**
 * Base class for visitors that create filter Bson from expressions.
 */
public abstract class AbstractFieldBsonCreator {

    static final CharSequence FEATURE_ID_WILDCARD = "*";

    private static final KeyNameReviser KEY_NAME_REVISER = KeyNameReviser.escapeProblematicPlainChars();

    @Nullable
    private final List<String> authorizationSubjectIds;

    AbstractFieldBsonCreator(@Nullable final List<String> authorizationSubjectIds) {
        this.authorizationSubjectIds = authorizationSubjectIds;
    }

    /**
     * Create filter BSON for global readability.
     *
     * @param authorizationSubjectIds authorization subject IDs for visibility restriction.
     * @return the BSON filter.
     */
    public static Bson getGlobalReadBson(final Iterable<String> authorizationSubjectIds) {
        return Filters.in(PersistenceConstants.FIELD_GLOBAL_READ, authorizationSubjectIds);
    }

    abstract Bson visitPointer(final String pointer);

    abstract Bson visitRootLevelField(final String fieldName);

    // simple fields, attributes and direct (no wildcard) feature
    Optional<Bson> getAuthorizationBson(final JsonPointer pointer) {
        return Optional.ofNullable(authorizationSubjectIds)
                .map(subjectIds -> getAuthFilter(authorizationSubjectIds, pointer));
    }

    Optional<Bson> getFeatureWildcardAuthorizationBson(final JsonPointer pointer) {
        final Stream<JsonPointer> fixedPaths = Stream.of(
                JsonPointer.empty(),    // root grants
                JsonPointer.of("/features"), // features grants
                JsonPointer.of("/id")); // feature grants
        final Stream<JsonPointer> collectedPaths = collectPaths(pointer);
        final List<JsonPointer> allPaths = Stream.concat(fixedPaths, collectedPaths).toList();
        return Optional.ofNullable(authorizationSubjectIds).map(nonNullSubjectsIds -> {
            Bson child = null;
            for (final JsonPointer path : allPaths) {
                child = getAuthFilter(nonNullSubjectsIds, path, child);
            }
            return child;
        });
    }

    public final Bson visitSimple(final String fieldName) {
        return fieldName.startsWith(PersistenceConstants.SLASH)
                ? visitPointer(fieldName)
                : visitRootLevelField(fieldName);
    }

    private Bson getAuthFilter(final List<String> authSubjectIds, final JsonPointer pointer) {
        if (pointer.isEmpty()) {
            return getAuthFilter(authSubjectIds, pointer, null);
        } else {
            return getAuthFilter(authSubjectIds, pointer, getAuthFilter(authSubjectIds, pointer.cutLeaf()));
        }
    }

    private Bson getAuthFilter(final Iterable<String> authSubjectIds, final JsonPointer pointer,
            @Nullable final Bson child) {

        // p.<dotted-path>.·g
        final var grant = toDottedPath(FIELD_POLICY, pointer, FIELD_GRANTED);
        // p.<dotted-path>.·r
        final var revoke = toDottedPath(FIELD_POLICY, pointer, FIELD_REVOKED);

        final Bson or = child != null
                ? Filters.or(Filters.in(grant, authSubjectIds), child)
                : Filters.or(Filters.in(grant, authSubjectIds));
        return Filters.and(Filters.nin(revoke, authSubjectIds), or);
    }

    /**
     * Collects all paths contained in pointer, excluding the root pointer.
     *
     * @param pointer the pointer for which to collect all contained paths
     * @return stream of paths contained in pointer, without root pointer.
     */
    private Stream<JsonPointer> collectPaths(final JsonPointer pointer) {
        if (pointer.isEmpty()) {
            return Stream.empty();
        }
        return Stream.concat(collectPaths(pointer.cutLeaf()), Stream.of(pointer));
    }

    static String toDottedPath(final CharSequence prefix, final Iterable<JsonKey> jsonPointer) {
        return String.join(DOT, prefix, toDottedPath(jsonPointer));
    }

    static String toDottedPath(final CharSequence prefix, final Iterable<JsonKey> jsonPointer,
            final CharSequence suffix) {
        if (jsonPointer.iterator().hasNext()) {
            return String.join(DOT, prefix, toDottedPath(jsonPointer), suffix);
        } else {
            return String.join(DOT, prefix, suffix);
        }
    }

    static String toDottedPath(final Iterable<JsonKey> jsonPointer) {
        return StreamSupport.stream(jsonPointer.spliterator(), false)
                .map(k -> KEY_NAME_REVISER.apply(k.toString()))
                .collect(Collectors.joining(DOT));
    }

}
