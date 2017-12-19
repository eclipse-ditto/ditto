/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonifiableAdaptable}.
 */
public final class ImmutableJsonifiableAdaptableTest {

    private static final String KNOWN_TOPIC = "org.eclipse.ditto/myThing/things/twin/commands/modify";
    private static final JsonObject KNOWN_HEADERS = JsonObject.newBuilder()
            .set(DittoHeaderDefinition.CORRELATION_ID.getKey(), "cor-id")
            .build();

    private static final JsonPointer KNOWN_PATH = JsonPointer.empty();
    private static final JsonValue KNOWN_VALUE = JsonValue.of("foo");
    private static final HttpStatusCode KNOWN_STATUS = HttpStatusCode.OK;
    private static final long KNOWN_REVISION = 1337;
    private static final JsonFieldSelector KNOWN_FIELDS = JsonFieldSelector.newInstance("/foo");

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
                .set(JsonifiableAdaptable.JsonFields.HEADERS, KNOWN_HEADERS)
                .set(Payload.JsonFields.PATH, KNOWN_PATH.toString())
                .set(Payload.JsonFields.VALUE, KNOWN_VALUE)
                .set(Payload.JsonFields.STATUS, KNOWN_STATUS.toInt())
                .set(Payload.JsonFields.REVISION, KNOWN_REVISION)
                .set(Payload.JsonFields.FIELDS, KNOWN_FIELDS.toString())
                .build();

        final ImmutablePayload payload =
                ImmutablePayload.of(KNOWN_PATH, KNOWN_VALUE, KNOWN_STATUS, KNOWN_REVISION, KNOWN_FIELDS);

        final Adaptable adaptable = ImmutableAdaptable.of(ProtocolFactory.newTopicPath(KNOWN_TOPIC), payload,
                DittoHeaders.newBuilder(KNOWN_HEADERS).build());
        final JsonifiableAdaptable jsonifiableAdaptable = ImmutableJsonifiableAdaptable.of(adaptable);

        final JsonObject actual = jsonifiableAdaptable.toJson().asObject();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void jsonDeserializationWorksAsExpected() {
        final ImmutablePayload payload =
                ImmutablePayload.of(KNOWN_PATH, KNOWN_VALUE, KNOWN_STATUS, KNOWN_REVISION, KNOWN_FIELDS);

        final Adaptable adaptable = ImmutableAdaptable.of(ProtocolFactory.newTopicPath(KNOWN_TOPIC), payload,
                DittoHeaders.newBuilder(KNOWN_HEADERS).build());
        final JsonifiableAdaptable expected = ImmutableJsonifiableAdaptable.of(adaptable);

        final JsonObject payloadJsonObject = JsonObject.newBuilder()
                .set(JsonifiableAdaptable.JsonFields.TOPIC, KNOWN_TOPIC)
                .set(JsonifiableAdaptable.JsonFields.HEADERS, KNOWN_HEADERS)
                .set(Payload.JsonFields.PATH, KNOWN_PATH.toString())
                .set(Payload.JsonFields.VALUE, KNOWN_VALUE)
                .set(Payload.JsonFields.STATUS, KNOWN_STATUS.toInt())
                .set(Payload.JsonFields.REVISION, KNOWN_REVISION)
                .set(Payload.JsonFields.FIELDS, KNOWN_FIELDS.toString())
                .build();

        final ImmutableJsonifiableAdaptable actual = ImmutableJsonifiableAdaptable.fromJson(payloadJsonObject);

        assertThat(actual).isEqualTo(expected);
    }

}
