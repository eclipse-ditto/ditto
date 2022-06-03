/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;

import com.mongodb.reactivestreams.client.MongoClients;

final class FilterAssertions {

    public static final String AND = "$and";
    public static final String OR = "$or";
    public static final String ELEM_MATCH = "$elemMatch";

    private FilterAssertions() {}

    static void assertAuthFilter(final BsonValue authFilter, final List<String> subjects, final String... paths) {
        assertThat(authFilter.isDocument()).isTrue();
        final BsonDocument document = authFilter.asDocument();
        assertThat(document).hasSize(1);
        if (document.get(AND) != null) {
            final BsonArray next = document.get(AND).asArray();
            final BsonDocument revoke = next.get(0).asDocument();
            assertThat(revoke).hasSize(1);
            assertThat(revoke.entrySet()).containsExactly(Map.entry(revokePath(paths[0]), nin(subjects)));
            assertAuthFilter(next.get(1), subjects, paths);
        } else if (document.get(OR) != null) {
            final BsonArray next = document.get(OR).asArray();
            final BsonDocument grant = next.get(0).asDocument();
            assertThat(grant).hasSize(1);
            assertThat(grant.entrySet()).containsExactly(Map.entry(grantPath(paths[0]), in(subjects)));
            if (next.size() > 1) {
                assertAuthFilter(next.get(1), subjects, Arrays.copyOfRange(paths, 1, paths.length));
            } else if (paths.length > 1) {
                fail("The following paths are missing in the auth filter: %s", Arrays.asList(paths));
            }
        } else {
            fail("Must be either " + AND + " or " + OR);
        }
    }

    private static String revokePath(final String path) {
        return toPolicyPath(path, PersistenceConstants.FIELD_REVOKED);
    }

    private static String grantPath(final String path) {
        return toPolicyPath(path, PersistenceConstants.FIELD_GRANTED);
    }

    private static String toPolicyPath(final String path, final String lastField) {
        if (path.isEmpty()) {
            return String.join(PersistenceConstants.DOT, PersistenceConstants.FIELD_POLICY, lastField);
        } else {
            return String.join(PersistenceConstants.DOT, PersistenceConstants.FIELD_POLICY, path, lastField);
        }
    }

    private static BsonDocument nin(final Collection<String> subjects) {
        return new BsonDocument("$nin",
                new BsonArray(subjects.stream()
                        .map(BsonString::new).collect(Collectors.toList())));
    }

    private static BsonDocument in(final Collection<String> subjects) {
        return new BsonDocument("$in",
                new BsonArray(subjects.stream().map(BsonString::new).collect(Collectors.toList())));
    }

    static BsonDocument toBsonDocument(final Bson bson) {
        return bson.toBsonDocument(BsonDocument.class, MongoClients.getDefaultCodecRegistry());
    }
}
