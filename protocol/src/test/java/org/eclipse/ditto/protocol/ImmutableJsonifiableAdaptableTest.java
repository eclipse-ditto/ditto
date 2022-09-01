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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonifiableAdaptable}.
 */
public final class ImmutableJsonifiableAdaptableTest {

    private static final String KNOWN_TOPIC = "org.eclipse.ditto/myThing/things/twin/commands/modify";
    private static final DittoHeaders KNOWN_HEADERS = DittoHeaders.newBuilder()
            .correlationId("cor-id")
            .responseRequired(true)
            .build();

    private static final JsonPointer KNOWN_PATH = JsonPointer.empty();
    private static final JsonValue KNOWN_VALUE = JsonValue.of("foo");
    private static final HttpStatus KNOWN_STATUS = HttpStatus.OK;
    private static final long KNOWN_REVISION = 1337;
    private static final Instant KNOWN_TIMESTAMP = Instant.now();
    private static final Metadata KNOWN_METADATA = Metadata.newBuilder()
            .set("foo", 42)
            .set("bar", JsonObject.newBuilder().set("fop", "fada").build())
            .build();
    private static final JsonFieldSelector KNOWN_FIELDS = JsonFieldSelector.newInstance("/foo");

    private static JsonObject knownExtra;
    private static Payload knownPayload;

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

        knownPayload = ImmutablePayload.getBuilder(KNOWN_PATH)
                .withValue(KNOWN_VALUE)
                .withExtra(knownExtra)
                .withStatus(KNOWN_STATUS)
                .withRevision(KNOWN_REVISION)
                .withTimestamp(KNOWN_TIMESTAMP)
                .withMetadata(KNOWN_METADATA)
                .withFields(KNOWN_FIELDS)
                .build();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonifiableAdaptable.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonifiableAdaptable.class, areImmutable(),
                provided(Adaptable.class).areAlsoImmutable());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAdaptable() {
        ImmutableJsonifiableAdaptable.of(null);
    }

    @Test
    public void jsonSerializationWorksAsExpected() {
        final JsonObject expected = JsonObject.newBuilder()
                .set(JsonifiableAdaptable.JsonFields.TOPIC, KNOWN_TOPIC)
                .set(JsonifiableAdaptable.JsonFields.HEADERS, KNOWN_HEADERS.toJson())
                .set(Payload.JsonFields.PATH, KNOWN_PATH.toString())
                .set(Payload.JsonFields.VALUE, KNOWN_VALUE)
                .set(Payload.JsonFields.EXTRA, knownExtra)
                .set(Payload.JsonFields.STATUS, KNOWN_STATUS.getCode())
                .set(Payload.JsonFields.REVISION, KNOWN_REVISION)
                .set(Payload.JsonFields.TIMESTAMP, KNOWN_TIMESTAMP.toString())
                .set(Payload.JsonFields.METADATA, KNOWN_METADATA.toJson())
                .set(Payload.JsonFields.FIELDS, KNOWN_FIELDS.toString())
                .build();

        final Adaptable adaptable =
                ImmutableAdaptable.of(ProtocolFactory.newTopicPath(KNOWN_TOPIC), knownPayload, KNOWN_HEADERS);
        final JsonifiableAdaptable jsonifiableAdaptable = ImmutableJsonifiableAdaptable.of(adaptable);

        final JsonObject actual = jsonifiableAdaptable.toJson().asObject();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void jsonDeserializationWorksAsExpected() {
        final Adaptable adaptable =
                ImmutableAdaptable.of(ProtocolFactory.newTopicPath(KNOWN_TOPIC), knownPayload, KNOWN_HEADERS);
        final JsonifiableAdaptable expected = ImmutableJsonifiableAdaptable.of(adaptable);

        final JsonObject payloadJsonObject = JsonObject.newBuilder()
                .set(JsonifiableAdaptable.JsonFields.TOPIC, KNOWN_TOPIC)
                .set(JsonifiableAdaptable.JsonFields.HEADERS, KNOWN_HEADERS.toJson())
                .set(Payload.JsonFields.PATH, KNOWN_PATH.toString())
                .set(Payload.JsonFields.VALUE, KNOWN_VALUE)
                .set(Payload.JsonFields.EXTRA, knownExtra)
                .set(Payload.JsonFields.STATUS, KNOWN_STATUS.getCode())
                .set(Payload.JsonFields.REVISION, KNOWN_REVISION)
                .set(Payload.JsonFields.TIMESTAMP, KNOWN_TIMESTAMP.toString())
                .set(Payload.JsonFields.METADATA, KNOWN_METADATA.toJson())
                .set(Payload.JsonFields.FIELDS, KNOWN_FIELDS.toString())
                .build();

        final ImmutableJsonifiableAdaptable actual = ImmutableJsonifiableAdaptable.fromJson(payloadJsonObject);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deserializeJsonWithoutTopicPathFails() {
        final JsonObject invalidJsonObject = JsonObject.newBuilder()
                .set(JsonifiableAdaptable.JsonFields.HEADERS, KNOWN_HEADERS.toJson())
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> ImmutableJsonifiableAdaptable.fromJson(invalidJsonObject))
                .withMessageContaining(JsonifiableAdaptable.JsonFields.TOPIC.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void deserializeJsonWithInvalidTopicPathFails() {
        final String invalidTopicPath = "abc";
        final JsonObject invalidJsonObject = JsonObject.newBuilder()
                .set(JsonifiableAdaptable.JsonFields.HEADERS, KNOWN_HEADERS.toJson())
                .set(JsonifiableAdaptable.JsonFields.TOPIC, invalidTopicPath)
                .build();

        assertThatExceptionOfType(UnknownTopicPathException.class)
                .isThrownBy(() -> ImmutableJsonifiableAdaptable.fromJson(invalidJsonObject))
                .satisfies(unknownTopicPathException -> {
                    try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
                        softly.assertThat(unknownTopicPathException.getDittoHeaders())
                                .as("DittoHeaders")
                                .isEqualTo(KNOWN_HEADERS);
                        softly.assertThat(unknownTopicPathException.getDescription())
                                .as("description")
                                .hasValue("The topic path has no entity name part.");
                        softly.assertThat(unknownTopicPathException.getCause())
                                .as("cause")
                                .isInstanceOf(NoSuchElementException.class);
                    }
                });
    }

}
