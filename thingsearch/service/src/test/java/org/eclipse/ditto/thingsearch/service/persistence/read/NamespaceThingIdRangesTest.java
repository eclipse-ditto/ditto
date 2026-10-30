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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.bson.BsonDocument;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Unit tests for {@link NamespaceThingIdRanges}.
 */
public final class NamespaceThingIdRangesTest {

    @Test
    public void rangeOfNamespace() {
        assertThat(NamespaceThingIdRanges.of("org.eclipse.ditto")).isEqualTo(
                BsonDocument.parse("{\"_id\":{\"$gte\":\"org.eclipse.ditto:\",\"$lt\":\"org.eclipse.ditto;\"}}"));
    }

    @Test
    public void rangeOfEmptyNamespace() {
        assertThat(NamespaceThingIdRanges.of(""))
                .isEqualTo(BsonDocument.parse("{\"_id\":{\"$gte\":\":\",\"$lt\":\";\"}}"));
    }

    @Test
    public void rangeContainsExactlyTheThingsOfItsNamespace() {
        final String start = "a.b:";
        final String end = "a.b;";

        assertThat(List.of(ThingId.of("a.b", "x"), ThingId.of("a.b", "~"), ThingId.of("a.b", "x:y")))
                .allSatisfy(thingId -> assertThat(thingId.toString()).isGreaterThanOrEqualTo(start).isLessThan(end));
        assertThat(List.of(ThingId.of("a.b.c", "x"), ThingId.of("a.b-c", "x"), ThingId.of("a.bc", "x"),
                ThingId.of("a", "b"), ThingId.of("a.c", "x")))
                .allSatisfy(thingId -> assertThat(thingId.toString()).satisfiesAnyOf(
                        id -> assertThat(id).isLessThan(start),
                        id -> assertThat(id).isGreaterThanOrEqualTo(end)));
    }

    @Test
    public void spanningOneNamespaceIsItsRange() {
        assertThat(NamespaceThingIdRanges.spanning(Set.of("a.b")))
                .isEqualTo(NamespaceThingIdRanges.of("a.b"));
    }

    @Test
    public void spanningComparesWithDelimiterAppended() {
        // "a.b" < "a.b.c", but "a.b.c:" < "a.b:" - comparing the bare namespaces would exclude the things of "a.b.c"
        assertThat(NamespaceThingIdRanges.spanning(Set.of("a.b", "a.b.c")))
                .isEqualTo(BsonDocument.parse("{\"_id\":{\"$gte\":\"a.b.c:\",\"$lt\":\"a.b;\"}}"));
    }

    @Test
    public void spanningSeveralNamespaces() {
        assertThat(NamespaceThingIdRanges.spanning(Set.of("org.eclipse.ditto", "com.example", "", "z")))
                .isEqualTo(BsonDocument.parse("{\"_id\":{\"$gte\":\":\",\"$lt\":\"z;\"}}"));
    }

    @Test
    public void spanningNoNamespaceThrows() {
        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(() -> NamespaceThingIdRanges.spanning(Set.of()));
    }

    @Test
    public void namespacesInAscendingThingIdOrder() {
        assertThat(NamespaceThingIdRanges.inThingIdOrder(Set.of("a.b", "a.b.c", "a.b-c", "", "z"), SortDirection.ASC))
                .containsExactly("", "a.b-c", "a.b.c", "a.b", "z");
    }

    @Test
    public void namespacesInDescendingThingIdOrder() {
        assertThat(NamespaceThingIdRanges.inThingIdOrder(Set.of("a.b", "a.b.c", "a.b-c", "", "z"), SortDirection.DESC))
                .containsExactly("z", "a.b", "a.b.c", "a.b-c", "");
    }

}
