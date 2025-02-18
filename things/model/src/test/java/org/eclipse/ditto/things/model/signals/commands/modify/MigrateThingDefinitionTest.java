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


import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingIdNotExplicitlySettableException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public final class MigrateThingDefinitionTest {

    private static final DittoHeaders DITTO_HEADERS =
            DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();

    private static final JsonObject MIGRATION_PAYLOAD = JsonFactory.newObjectBuilder()
            .set("attributes", JsonFactory.newObjectBuilder().set("manufacturer", "New Corp").build())
            .set("features", JsonFactory.newObjectBuilder()
                    .set("thermostat", JsonFactory.newObjectBuilder()
                            .set("properties", JsonFactory.newObjectBuilder()
                                    .set("status", JsonFactory.newObjectBuilder()
                                            .set("temperature", JsonFactory.newObjectBuilder()
                                                    .set("value", 23.5)
                                                    .set("unit", "DEGREE_CELSIUS")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();

    private static final Map<ResourceKey, String> PATCH_CONDITIONS = new HashMap<>();

    static {
        PATCH_CONDITIONS.put(ResourceKey.newInstance("thing:/features/thermostat"), "not(exists(/features/thermostat))");
    }

    private static final ThingId THING_ID = ThingId.of("namespace", "my-thing");

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MigrateThingDefinition.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void createUpdateThingDefinitionSuccessfully() {
        final MigrateThingDefinition command = MigrateThingDefinition.of(
                THING_ID,
                "https://models.example.com/thing-definition-1.0.0.tm.jsonld",
                MIGRATION_PAYLOAD,
                PATCH_CONDITIONS,
                true,
                DITTO_HEADERS
        );

        assertThat(command.getEntityId().toString()).isEqualTo(THING_ID.toString());
        assertThat(command.getThingDefinitionUrl()).isEqualTo("https://models.example.com/thing-definition-1.0.0.tm.jsonld");
        assertThat(command.getMigrationPayload()).isEqualTo(MIGRATION_PAYLOAD);
        assertThat(command.getPatchConditions()).isEqualTo(PATCH_CONDITIONS);
        assertThat(command.isInitializeMissingPropertiesFromDefaults()).isTrue();
    }

    @Test
    public void createUpdateThingDefinitionWithEmptyPatchConditions() {
        final MigrateThingDefinition command = MigrateThingDefinition.of(
                THING_ID,
                "https://models.example.com/thing-definition-1.0.0.tm.jsonld",
                MIGRATION_PAYLOAD,
                Collections.emptyMap(),
                false,
                DITTO_HEADERS
        );

        assertThat(command.getPatchConditions()).isEmpty();
        assertThat(command.isInitializeMissingPropertiesFromDefaults()).isFalse();
    }

    @Test(expected = NullPointerException.class)
    public void createUpdateThingDefinitionWithNullThingId() {
        MigrateThingDefinition.of(
                null,
                "https://models.example.com/thing-definition-1.0.0.tm.jsonld",
                MIGRATION_PAYLOAD,
                PATCH_CONDITIONS,
                true,
                DITTO_HEADERS
        );
    }

    @Test
    public void testFromJson() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("thingId", THING_ID.toString())
                .set("thingDefinitionUrl", "https://models.example.com/thing-definition-1.0.0.tm.jsonld")
                .set("migrationPayload", MIGRATION_PAYLOAD)
                .set("patchConditions", JsonFactory.newObjectBuilder()
                        .set("thing:/features/thermostat", "not(exists(/features/thermostat))")
                        .build())
                .set("initializeMissingPropertiesFromDefaults", true)
                .build();

        final MigrateThingDefinition command = MigrateThingDefinition.fromJson(json, DITTO_HEADERS);

        assertThat(command.getEntityId().toString()).isEqualTo(THING_ID.toString());
        assertThat(command.getThingDefinitionUrl()).isEqualTo("https://models.example.com/thing-definition-1.0.0.tm.jsonld");
        assertThat(command.getMigrationPayload()).isEqualTo(MIGRATION_PAYLOAD);
        assertThat(command.getPatchConditions()).isEqualTo(PATCH_CONDITIONS);
        assertThat(command.isInitializeMissingPropertiesFromDefaults()).isTrue();
    }

    @Test
    public void toJsonReturnsExpected() {
        final MigrateThingDefinition command = MigrateThingDefinition.of(
                THING_ID,
                "https://models.example.com/thing-definition-1.0.0.tm.jsonld",
                MIGRATION_PAYLOAD,
                PATCH_CONDITIONS,
                true,
                DITTO_HEADERS
        );

        final JsonObject json = command.toJson();

        Assertions.assertThat(json.getValueOrThrow(JsonFieldDefinition.ofString("thingId"))).isEqualTo(THING_ID.toString());
        Assertions.assertThat(json.getValueOrThrow(JsonFieldDefinition.ofString("thingDefinitionUrl"))).isEqualTo("https://models.example.com/thing-definition-1.0.0.tm.jsonld");
        Assertions.assertThat(json.getValueOrThrow(JsonFieldDefinition.ofJsonObject("migrationPayload"))).isEqualTo(MIGRATION_PAYLOAD);
        Assertions.assertThat(json.getValueOrThrow(JsonFieldDefinition.ofBoolean("initializeMissingPropertiesFromDefaults"))).isTrue();
    }

    @Test
    public void testUpdateThingDefinitionEquality() {
        final MigrateThingDefinition command1 = MigrateThingDefinition.of(
                THING_ID,
                "https://models.example.com/thing-definition-1.0.0.tm.jsonld",
                MIGRATION_PAYLOAD,
                PATCH_CONDITIONS,
                true,
                DITTO_HEADERS
        );

        final MigrateThingDefinition command2 = MigrateThingDefinition.of(
                THING_ID,
                "https://models.example.com/thing-definition-1.0.0.tm.jsonld",
                MIGRATION_PAYLOAD,
                PATCH_CONDITIONS,
                true,
                DITTO_HEADERS
        );

        Assertions.assertThat(command1).isEqualTo(command2);
    }
}

