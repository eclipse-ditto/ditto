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
package org.eclipse.ditto.services.utils.persistentactors.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link MetadataHeaderParser}.
 */
public final class MetadataHeaderParserTest {

    private MetadataHeaderParser underTest;

    @Before
    public void setUp() {
        underTest = MetadataHeaderParser.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataHeaderParser.class, areImmutable());
    }

    @Test
    public void tryToParseNullDittoHeaders() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.parse(null))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void parseEmptyDittoHeaders() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();

        final Stream<MetadataHeader> metadataHeaderStream = underTest.parse(emptyDittoHeaders);

        assertThat(metadataHeaderStream).isEmpty();
    }

    @Test
    public void parseSingleTopLevelMetadataHeader() {
        final String keySuffix = "issuedAt";
        final JsonValue issueInstant = JsonValue.of(String.valueOf(Instant.now()));
        final MetadataHeader expected =
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of(keySuffix)), issueInstant);
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putHeader(MetadataHeaderKey.PREFIX + keySuffix, issueInstant.toString())
                .build();

        final Stream<MetadataHeader> metadataHeaderStream = underTest.parse(dittoHeaders);

        assertThat(metadataHeaderStream).containsOnly(expected);
    }

    @Test
    public void parseMultipleMixedLevelMetadataHeaders() {
        final JsonValue issueInstant1 = JsonValue.of("2020-06-09T14:29:00Z");
        final JsonValue issueInstant2 = JsonValue.of("2020-06-09T14:14:00Z");
        final Collection<MetadataHeader> expected = new ArrayList<>();
        expected.add(MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("issuedAt")), issueInstant1));
        expected.add(
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/features/lamp/properties/color/r/issuedAt")),
                        issueInstant2));
        expected.add(
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/features/lamp/properties/color/g/issuedAt")),
                        issueInstant2));
        expected.add(
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/features/lamp/properties/color/b/issuedAt")),
                        issueInstant2));

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putHeader(MetadataHeaderKey.PREFIX + "issuedAt", issueInstant1.toString())
                .putHeader(MetadataHeaderKey.PREFIX +
                        "/features/lamp/properties/color/r/issuedAt", issueInstant2.toString())
                .putHeader(MetadataHeaderKey.PREFIX +
                        "/features/lamp/properties/color/g/issuedAt", issueInstant2.toString())
                .putHeader(MetadataHeaderKey.PREFIX +
                        "/features/lamp/properties/color/b/issuedAt", issueInstant2.toString())
                .build();

        final Stream<MetadataHeader> metadataHeaderStream = underTest.parse(dittoHeaders);

        assertThat(metadataHeaderStream).containsOnlyElementsOf(expected);
    }

}