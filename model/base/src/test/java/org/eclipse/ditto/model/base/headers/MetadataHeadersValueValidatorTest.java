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
package org.eclipse.ditto.model.base.headers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.metadata.MetadataHeader;
import org.eclipse.ditto.model.base.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.model.base.headers.metadata.MetadataHeaders;
import org.junit.Test;

/**
 * Unit test for {@link MetadataHeadersValueValidator}.
 */
public final class MetadataHeadersValueValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataHeadersValueValidator.class, areImmutable());
    }

    @Test
    public void validateValidMetadataHeadersCharSequence() {
        final MetadataHeaders metadataHeaders = MetadataHeaders.newInstance();
        metadataHeaders.add(MetadataHeader.of(MetadataHeaderKey.parse("/*/issuedBy"), JsonValue.of("me")));
        metadataHeaders.add(MetadataHeader.of(MetadataHeaderKey.parse("/foo/bar"), JsonValue.of("baz")));

        final MetadataHeadersValueValidator underTest = MetadataHeadersValueValidator.getInstance();

        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.METADATA, metadataHeaders.toJsonString()))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateInvalidMetadataHeadersCharSequence1() {
        final JsonArray invalidMetadataHeaderEntries = JsonArray.newBuilder()
                .add(JsonObject.newBuilder()
                        .set(MetadataHeader.JsonFields.METADATA_KEY, "/foo/bar")
                        .build())
                .build();
        final String invalidMetadataHeadersCharSequence = invalidMetadataHeaderEntries.toString();

        final MetadataHeadersValueValidator underTest = MetadataHeadersValueValidator.getInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.METADATA, invalidMetadataHeadersCharSequence))
                .withMessage("The value '%s' of the header '%s' is not a valid MetadataHeaders.",
                        invalidMetadataHeadersCharSequence, DittoHeaderDefinition.METADATA.getKey())
                .satisfies(dittoHeaderInvalidException -> assertThat(dittoHeaderInvalidException.getDescription())
                        .contains("Metadata header entry JSON object did not include required </value> field!" +
                                " Check if all required JSON fields were set."))
                .withCauseInstanceOf(JsonMissingFieldException.class);
    }

    @Test
    public void validateInvalidMetadataHeadersCharSequence2() {
        final JsonArray invalidMetadataHeaderEntries = JsonArray.newBuilder()
                .add(JsonObject.newBuilder()
                        .set(MetadataHeader.JsonFields.METADATA_KEY, "/*/*/foo")
                        .set(MetadataHeader.JsonFields.METADATA_VALUE, JsonValue.of("bar"))
                        .build())
                .build();
        final String invalidMetadataHeadersCharSequence = invalidMetadataHeaderEntries.toString();

        final MetadataHeadersValueValidator underTest = MetadataHeadersValueValidator.getInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.METADATA, invalidMetadataHeadersCharSequence))
                .withMessage("The value '%s' of the header '%s' is not a valid MetadataHeaders.",
                        invalidMetadataHeadersCharSequence, DittoHeaderDefinition.METADATA.getKey())
                .satisfies(dittoHeaderInvalidException -> assertThat(dittoHeaderInvalidException.getDescription())
                        .contains("The metadata header key </*/*/foo> is invalid!" +
                                " A wildcard path of a metadata header key must have exactly two levels but it had" +
                                " <3>!"))
                .withCauseInstanceOf(JsonParseException.class);
    }

}