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
package org.eclipse.ditto.base.model.headers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeader;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.MetadataHeadersValueValidator}.
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

        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.PUT_METADATA, metadataHeaders.toJsonString()))
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
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.PUT_METADATA, invalidMetadataHeadersCharSequence))
                .withMessage("The value '%s' of the header '%s' is not a valid MetadataHeaders.",
                        invalidMetadataHeadersCharSequence, DittoHeaderDefinition.PUT_METADATA.getKey())
                .satisfies(dittoHeaderInvalidException -> assertThat(dittoHeaderInvalidException.getDescription())
                        .contains("Metadata header entry JSON object did not include required </value> field!" +
                                " Check if all required JSON fields were set."))
                .withCauseInstanceOf(JsonMissingFieldException.class);
    }

}
