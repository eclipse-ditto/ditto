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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.metadata.MetadataHeaderKey;
import org.junit.Test;

/**
 * Unit test for {@link MetadataHeaderValidator}.
 */
public final class MetadataHeaderValidatorTest {

    private static final MetadataHeaderKey VALID_KEY = MetadataHeaderKey.parse("*/key");
    private static final JsonValue JSON_OBJECT = JsonObject.newBuilder()
            .set("foo", "bar")
            .set("one", 2)
            .build();
    private static final JsonValue JSON_ARRAY = JsonArray.of("Foo", "Bar", "Baz");
    private static final JsonValue JSON_STRING =
            JsonValue.of("Corrupti quis et aut ipsa non molestiae dolor assumenda.");
    private static final JsonValue JSON_LONG = JsonValue.of(Long.MAX_VALUE);
    private static final JsonValue JSON_DOUBLE = JsonValue.of(Double.valueOf(23.42d));
    private static final JsonValue JSON_NULL_LITERAL = JsonValue.nullLiteral();

    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataHeaderValidator.class, areImmutable());
    }

    @Test
    public void validateValidKey() {
        final MetadataHeaderValidator underTest =
                MetadataHeaderValidator.of(VALID_KEY.toString(), JSON_NULL_LITERAL.toString());

        assertThatCode(underTest::validate).doesNotThrowAnyException();
    }

    @Test
    public void validateInvalidKey() {
        final String invalidKey = "*/*/blub";
        final MetadataHeaderValidator underTest = MetadataHeaderValidator.of(invalidKey, JSON_NULL_LITERAL.toString());

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(underTest::validate)
                .withMessage("The metadata header key <%s> is invalid!", invalidKey)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void validateNonJsonString() {
        final String invalidJsonObjectString = "foo";
        final MetadataHeaderValidator underTest =
                MetadataHeaderValidator.of(VALID_KEY.toString(), invalidJsonObjectString);

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(underTest::validate)
                .withMessage("The metadata header value <foo> for key <ditto-metadata:/*/key> is invalid!")
                .withCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void validateJsonNullLiteral() {
        final MetadataHeaderValidator underTest =
                MetadataHeaderValidator.of(VALID_KEY.toString(), JSON_NULL_LITERAL.toString());

        assertThatCode(underTest::validate).doesNotThrowAnyException();
    }

    @Test
    public void acceptJsonString() {
        final MetadataHeaderValidator underTest =
                MetadataHeaderValidator.of(VALID_KEY.toString(), JSON_STRING.toString());

        assertThatCode(underTest::validate).doesNotThrowAnyException();
    }

    @Test
    public void acceptJsonLong() {
        final MetadataHeaderValidator underTest =
                MetadataHeaderValidator.of(VALID_KEY.toString(), JSON_LONG.toString());

        assertThatCode(underTest::validate).doesNotThrowAnyException();
    }

    @Test
    public void acceptJsonDouble() {
        final MetadataHeaderValidator underTest =
                MetadataHeaderValidator.of(VALID_KEY.toString(), JSON_DOUBLE.toString());

        assertThatCode(underTest::validate).doesNotThrowAnyException();
    }

    @Test
    public void acceptJsonArray() {
        final MetadataHeaderValidator underTest =
                MetadataHeaderValidator.of(VALID_KEY.toString(), JSON_ARRAY.toString());

        assertThatCode(underTest::validate).doesNotThrowAnyException();
    }

    @Test
    public void acceptJsonObject() {
        final MetadataHeaderValidator underTest =
                MetadataHeaderValidator.of(VALID_KEY.toString(), JSON_OBJECT.toString());

        assertThatCode(underTest::validate).doesNotThrowAnyException();
    }

}