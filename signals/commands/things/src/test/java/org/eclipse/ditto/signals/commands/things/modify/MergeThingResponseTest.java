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

package org.eclipse.ditto.signals.commands.things.modify;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.signals.commands.things.TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER;
import static org.eclipse.ditto.signals.commands.things.TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE;
import static org.eclipse.ditto.signals.commands.things.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse}.
 */
public class MergeThingResponseTest {

    private static final DittoHeaders DITTO_HEADERS =
            DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, MergeThingResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, THING_ID.toString())
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.NO_CONTENT.toInt())
            .set(MergeThing.JsonFields.JSON_PATH, ABSOLUTE_LOCATION_ATTRIBUTE_POINTER.toString())
            .build();

    private static final MergeThingResponse KNOWN_MERGE_THING_RESPONSE = MergeThingResponse.of(THING_ID,
            ABSOLUTE_LOCATION_ATTRIBUTE_POINTER, DITTO_HEADERS);

    @Test
    public void assertImmutability() {
        assertInstancesOf(MergeThingResponse.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class, ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MergeThingResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final MergeThingResponse mergeThing = MergeThingResponse.fromJson(KNOWN_JSON, DITTO_HEADERS);
        assertThat(mergeThing).isEqualTo(KNOWN_MERGE_THING_RESPONSE);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = KNOWN_MERGE_THING_RESPONSE.toJson();
        DittoJsonAssertions.assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void ensureSchemaVersion() {
        final ThingId thingId = ThingId.of("foo", "bar");
        assertThatThrownBy(() -> MergeThingResponse.of(thingId, JsonPointer.empty(),
                DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_1).build()))
                .isInstanceOf(CommandNotSupportedException.class);
    }
}
