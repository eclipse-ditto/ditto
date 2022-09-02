/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePayload} and its builder.
 */
public final class ImmutablePayloadTest {

    private static final JsonPointer KNOWN_PATH = JsonPointer.empty();
    private static final JsonValue KNOWN_VALUE = JsonValue.of("foo");
    private static final HttpStatus KNOWN_STATUS = HttpStatus.OK;
    private static final long KNOWN_REVISION = 1337;
    private static final Instant KNOWN_TIMESTAMP = Instant.now();
    private static final JsonFieldSelector KNOWN_FIELDS = JsonFieldSelector.newInstance("/foo");

    private static JsonObject knownExtra;
    private static JsonObject knownJsonRepresentation;
    private static ImmutablePayload knownPayload;

    @BeforeClass
    public static void setUpClass() {
        knownExtra = JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder()
                        .set("manufacturer", "ACME corp")
                        .build())
                .set("features", JsonObject.newBuilder()
                        .set("location", JsonObject.newBuilder()
                                .set("longitude", 42.123D)
                                .set("latitude", 3.54D)
                                .build())
                        .build())
                .build();

        knownJsonRepresentation = JsonObject.newBuilder()
                .set(Payload.JsonFields.PATH, KNOWN_PATH.toString())
                .set(Payload.JsonFields.VALUE, KNOWN_VALUE)
                .set(Payload.JsonFields.EXTRA, knownExtra)
                .set(Payload.JsonFields.STATUS, KNOWN_STATUS.getCode())
                .set(Payload.JsonFields.REVISION, KNOWN_REVISION)
                .set(Payload.JsonFields.TIMESTAMP, KNOWN_TIMESTAMP.toString())
                .set(Payload.JsonFields.FIELDS, KNOWN_FIELDS.toString())
                .build();

        knownPayload = ImmutablePayload.getBuilder(KNOWN_PATH)
                .withValue(KNOWN_VALUE)
                .withExtra(knownExtra)
                .withStatus(KNOWN_STATUS)
                .withRevision(KNOWN_REVISION)
                .withTimestamp(KNOWN_TIMESTAMP)
                .withFields(KNOWN_FIELDS)
                .build();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePayload.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePayload.class,
                areImmutable(),
                provided(MessagePath.class, JsonObject.class, JsonValue.class,
                        JsonFieldSelector.class, Metadata.class).areAlsoImmutable());
    }

    @Test
    public void gettersReturnExpected() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat((Object) knownPayload.getPath())
                    .as("path")
                    .isEqualTo(ImmutableMessagePath.of(KNOWN_PATH));
            softly.assertThat(knownPayload.getValue())
                    .as("value")
                    .contains(KNOWN_VALUE);
            softly.assertThat(knownPayload.getExtra())
                    .as("extra")
                    .contains(knownExtra);
            softly.assertThat(knownPayload.getHttpStatus())
                    .as("status")
                    .contains(KNOWN_STATUS);
            softly.assertThat(knownPayload.getRevision())
                    .as("revision")
                    .contains(KNOWN_REVISION);
            softly.assertThat(knownPayload.getTimestamp())
                    .as("timestamp")
                    .contains(KNOWN_TIMESTAMP);
            softly.assertThat(knownPayload.getFields())
                    .as("fields")
                    .contains(KNOWN_FIELDS);
        }
    }

    @Test
    public void toJsonWorksAsExpected() {
        assertThat(knownPayload.toJson()).isEqualTo(knownJsonRepresentation);
    }

    @Test
    public void fromJsonWorksAsExpected() {
        assertThat(ImmutablePayload.fromJson(knownJsonRepresentation)).isEqualTo(knownPayload);
    }

    @Test
    public void toBuilderMaintainsIdentity() {
        final Payload payload = Payload.newBuilder(knownPayload).build();
        assertThat(payload).isEqualTo(knownPayload);
    }

}
