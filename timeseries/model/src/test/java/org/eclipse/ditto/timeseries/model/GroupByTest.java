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
package org.eclipse.ditto.timeseries.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link GroupBy}.
 */
public final class GroupByTest {

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(GroupBy.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void parsesThingIdDimension() {
        final GroupBy underTest = GroupBy.parse("thingId");

        assertThat(underTest.getKind()).isEqualTo(GroupBy.Kind.THING_ID);
        assertThat(underTest.getTagKey()).isEmpty();
        assertThat(underTest).isEqualTo(GroupBy.thingId());
    }

    @Test
    public void parsesPathDimension() {
        final GroupBy underTest = GroupBy.parse("path");

        assertThat(underTest.getKind()).isEqualTo(GroupBy.Kind.PATH);
        assertThat(underTest.getTagKey()).isEmpty();
        assertThat(underTest).isEqualTo(GroupBy.path());
    }

    @Test
    public void parsesTagDimension() {
        final GroupBy underTest = GroupBy.parse("tag:building");

        assertThat(underTest.getKind()).isEqualTo(GroupBy.Kind.TAG);
        assertThat(underTest.getTagKey()).contains("building");
        assertThat(underTest).isEqualTo(GroupBy.tag("building"));
    }

    @Test
    public void parseTrimsSurroundingWhitespace() {
        assertThat(GroupBy.parse("  thingId ")).isEqualTo(GroupBy.thingId());
        assertThat(GroupBy.parse(" tag:building ")).isEqualTo(GroupBy.tag("building"));
    }

    @Test
    public void wireFormRoundTrips() {
        assertThat(GroupBy.parse(GroupBy.thingId().toString())).isEqualTo(GroupBy.thingId());
        assertThat(GroupBy.parse(GroupBy.path().toString())).isEqualTo(GroupBy.path());
        assertThat(GroupBy.parse(GroupBy.tag("floor").toString())).isEqualTo(GroupBy.tag("floor"));
    }

    @Test
    public void groupKeyIsBareTagKeyForTagDimension() {
        // A group reads as {"building": "A"} rather than {"tag:building": "A"} — the "tag:" prefix
        // is request syntax, not part of the result identity.
        assertThat(GroupBy.tag("building").getGroupKey()).isEqualTo("building");
        assertThat(GroupBy.thingId().getGroupKey()).isEqualTo("thingId");
        assertThat(GroupBy.path().getGroupKey()).isEqualTo("path");
    }

    /**
     * A non-reserved value is a tag dimension named by its Thing path — the form the design document
     * specifies ({@code groupBy=attributes/floor}). Only {@code thingId} and {@code path} are reserved.
     */
    @Test
    public void parseTreatsAnyOtherValueAsATagPath() {
        final GroupBy parsed = GroupBy.parse("attributes/floor");
        assertThat(parsed.getKind()).isEqualTo(GroupBy.Kind.TAG);
        assertThat(parsed.getTagKey()).contains("attributes/floor");
        assertThat(parsed.getGroupKey()).isEqualTo("attributes/floor");
    }

    @Test
    public void parseStillAcceptsTheLegacyTagPrefix() {
        assertThat(GroupBy.parse("tag:building")).isEqualTo(GroupBy.tag("building"));
    }

    @Test
    public void parseRejectsAnEmptyDimension() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> GroupBy.parse("   "))
                .withMessageContaining("must not be empty");
    }

    @Test
    public void parseRejectsTagWithoutKey() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> GroupBy.parse("tag:"))
                .withMessageContaining("non-empty tag key");
    }

    @Test
    public void tagRejectsBlankKey() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> GroupBy.tag("   "));
    }

    @Test
    public void parseRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> GroupBy.parse(null));
    }

    @Test
    public void tagDimensionsWithDifferentKeysAreNotEqual() {
        assertThat(GroupBy.tag("building")).isNotEqualTo(GroupBy.tag("floor"));
    }
}
