/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.junit.Assert.assertEquals;


import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MigrateThingDefinitionResponse}.
 */
public final class MigrateThingDefinitionResponseTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MigrateThingDefinitionResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testSerialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final ThingId thingId = ThingId.of("org.eclipse.ditto:some-thing-1");
        final JsonObject patch = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder().set("manufacturer", "New Corp").build())
                .set("features", JsonFactory.newObjectBuilder()
                        .set("sensor", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("status", JsonFactory.newObjectBuilder()
                                                .set("temperature", JsonFactory.newObjectBuilder()
                                                        .set("value", 25.0)
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        final MigrateThingDefinitionResponse originalResponse =
                MigrateThingDefinitionResponse.applied(thingId, patch, dittoHeaders);

        final JsonObject serializedJson = originalResponse.toJson();

        assertEquals("APPLIED", serializedJson.getValueOrThrow(MigrateThingDefinitionResponse.JsonFields.JSON_MERGE_STATUS));

        final MigrateThingDefinitionResponse deserializedResponse =
                MigrateThingDefinitionResponse.fromJson(serializedJson, dittoHeaders);

        assertThat(deserializedResponse).isEqualTo(originalResponse);
        assertEquals(MigrateThingDefinitionResponse.MergeStatus.APPLIED, deserializedResponse.getMergeStatus());
    }

    @Test
    public void testDryRunSerialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final ThingId thingId = ThingId.of("org.eclipse.ditto:some-thing-2");
        final JsonObject patch = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder().set("location", "Room 101").build())
                .build();

        final MigrateThingDefinitionResponse dryRunResponse =
                MigrateThingDefinitionResponse.dryRun(thingId, patch, dittoHeaders);

        final JsonObject serializedJson = dryRunResponse.toJson();

        assertEquals("DRY_RUN", serializedJson.getValueOrThrow(MigrateThingDefinitionResponse.JsonFields.JSON_MERGE_STATUS));

        final MigrateThingDefinitionResponse deserializedResponse =
                MigrateThingDefinitionResponse.fromJson(serializedJson, dittoHeaders);

        assertThat(deserializedResponse).isEqualTo(dryRunResponse);
        assertEquals(MigrateThingDefinitionResponse.MergeStatus.DRY_RUN, deserializedResponse.getMergeStatus());
    }

    @Test
    public void testToString() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final ThingId thingId = ThingId.of("org.eclipse.ditto:some-thing-3");
        final JsonObject patch = JsonFactory.newObjectBuilder().build();

        final MigrateThingDefinitionResponse response =
                MigrateThingDefinitionResponse.applied(thingId, patch, dittoHeaders);

        final String responseString = response.toString();

        assertThat(responseString).contains("MigrateThingDefinitionResponse");
        assertThat(responseString).contains("mergeStatus=APPLIED");
    }
}
