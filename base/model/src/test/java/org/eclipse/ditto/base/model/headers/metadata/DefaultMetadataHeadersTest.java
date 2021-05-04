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
package org.eclipse.ditto.base.model.headers.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.metadata.DefaultMetadataHeaders}.
 */
public final class DefaultMetadataHeadersTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMetadataHeaders.class)
                .usingGetClass()
                .withNonnullFields("treeSet")
                .verify();
    }

    @Test
    public void parseNullMetadataHeaderValue() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultMetadataHeaders.parseMetadataHeaders(null))
                .withMessage("The metadataHeadersCharSequence must not be null!")
                .withNoCause();
    }

    @Test
    public void parseEmptyCharSequence() {
        assertThat(DefaultMetadataHeaders.parseMetadataHeaders("")).isEmpty();
    }

    @Test
    public void parseEmptyJsonArrayCharSequence() {
        final String metadataHeaderValue = String.valueOf(JsonArray.empty());

        assertThat(DefaultMetadataHeaders.parseMetadataHeaders(metadataHeaderValue)).isEmpty();
    }

    @Test
    public void parseNonJsonArrayCharSequence() {
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> DefaultMetadataHeaders.parseMetadataHeaders("42"))
                .withMessage("<42> is not a valid JSON array!")
                .withNoCause();
    }

    @Test
    public void parseValidSingleMetadataEntry() {
        final MetadataHeader metadataHeader =
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/foo/bar")), JsonValue.of("baz"));
        final JsonArray metadataHeaderValue = JsonArray.of(metadataHeader.toJson());

        assertThat(DefaultMetadataHeaders.parseMetadataHeaders(metadataHeaderValue.toString()))
                .containsOnly(metadataHeader);
    }

    @Test
    public void parseValidMultipleMetadataEntries() {
        final MetadataHeader metadataHeader1 =
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/foo/bar")), JsonValue.of("baz"));
        final MetadataHeader metadataHeader2 =
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/*/issuedBy")), JsonValue.of("me"));
        final DefaultMetadataHeaders expected = DefaultMetadataHeaders.newInstance();
        expected.add(metadataHeader1);
        expected.add(metadataHeader2);

        assertThat(DefaultMetadataHeaders.parseMetadataHeaders(expected.toJsonString())).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final MetadataHeader metadataHeader1 =
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/foo/bar")), JsonValue.of("baz"));
        final MetadataHeader metadataHeader2 =
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/*/issuedBy")), JsonValue.of("me"));
        final JsonArray expected = JsonArray.newBuilder()
                .add(metadataHeader2.toJson())
                .add(metadataHeader1.toJson())
                .build();

        final DefaultMetadataHeaders underTest = DefaultMetadataHeaders.newInstance();
        underTest.add(metadataHeader1);
        underTest.add(metadataHeader2);

        assertThat(underTest.toJson()).isEqualTo(expected);
    }

    @Test
    public void metadataHeadersIsSortedByDefault() {
        final MetadataHeader metadataHeader1 =
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/foo/bar")), JsonValue.of("baz"));
        final MetadataHeader metadataHeader2 =
                MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("/*/issuedBy")), JsonValue.of("me"));

        final DefaultMetadataHeaders underTest = DefaultMetadataHeaders.newInstance();
        underTest.add(metadataHeader1);
        underTest.add(metadataHeader2);

        assertThat(underTest).containsExactly(metadataHeader2, metadataHeader1);
    }

}
