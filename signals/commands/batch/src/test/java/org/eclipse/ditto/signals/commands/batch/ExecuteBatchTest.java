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
package org.eclipse.ditto.signals.commands.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommandRegistry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ExecuteBatch}.
 */
public final class ExecuteBatchTest {

    private static final String KNOWN_BATCH_ID = UUID.randomUUID().toString();

    private static final List<Command> KNOWN_COMMANDS = Arrays.asList(
            ModifyThing.of("org.eclipse.ditto.test:myThing1", Thing.newBuilder()
                            .setId("org.eclipse.ditto.test:myThing1")
                            .build(), null, DittoHeaders.empty()),
            ModifyThing.of("org.eclipse.ditto.test:myThing1", Thing.newBuilder()
                            .setId("org.eclipse.ditto.test:myThing2")
                            .build(), null, DittoHeaders.empty()),
            ModifyThing.of("org.eclipse.ditto.test:myThing1", Thing.newBuilder()
                            .setId("org.eclipse.ditto.test:myThing3")
                            .build(), null, DittoHeaders.empty()));

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, ExecuteBatch.TYPE)
            .set(BatchCommand.JsonFields.BATCH_ID, KNOWN_BATCH_ID)
            .set(ExecuteBatch.JSON_COMMANDS, JsonArray.newBuilder()
                    .add(JsonFactory.newObjectBuilder()
                            .set(ExecuteBatch.JSON_COMMAND,
                                    KNOWN_COMMANDS.get(0).toJson(JsonSchemaVersion.V_2,
                                            FieldType.regularOrSpecial()))
                            .set(ExecuteBatch.JSON_DITTO_HEADERS,
                                    KNOWN_COMMANDS.get(0).getDittoHeaders().toJson())
                            .build()
                    )
                    .add(JsonFactory.newObjectBuilder()
                            .set(ExecuteBatch.JSON_COMMAND,
                                    KNOWN_COMMANDS.get(1).toJson(JsonSchemaVersion.V_2,
                                            FieldType.regularOrSpecial()))
                            .set(ExecuteBatch.JSON_DITTO_HEADERS, KNOWN_COMMANDS.get(1)
                                    .getDittoHeaders().toJson())
                            .build()
                    )
                    .add(JsonFactory.newObjectBuilder()
                            .set(ExecuteBatch.JSON_COMMAND,
                                    KNOWN_COMMANDS.get(2).toJson(JsonSchemaVersion.V_2,
                                            FieldType.regularOrSpecial()))
                            .set(ExecuteBatch.JSON_DITTO_HEADERS, KNOWN_COMMANDS.get(2)
                                    .getDittoHeaders().toJson())
                            .build()
                    )
                    .build())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ExecuteBatch.class, areImmutable(),
                provided(Command.class, DittoHeaders.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ExecuteBatch.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullBatchId() {
        ExecuteBatch.of(null, KNOWN_COMMANDS, DittoHeaders.empty());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullCommands() {
        ExecuteBatch.of(KNOWN_BATCH_ID, null, DittoHeaders.empty());
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDittoHeaders() {
        ExecuteBatch.of(KNOWN_BATCH_ID, KNOWN_COMMANDS, null);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ExecuteBatch underTest = ExecuteBatch.of(KNOWN_BATCH_ID, KNOWN_COMMANDS, DittoHeaders.empty());
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ExecuteBatch underTest = ExecuteBatch.fromJson(KNOWN_JSON.toString(), DittoHeaders.empty(),
                ThingCommandRegistry.newInstance());

        assertThat(underTest).isNotNull();
        assertThat(underTest.getBatchId()).isEqualTo(KNOWN_BATCH_ID);
        assertThat(underTest.getCommands()).isEqualTo(KNOWN_COMMANDS);
    }

    @Test
    public void retrieveCommandName() {
        final String name =
                ExecuteBatch.fromJson(KNOWN_JSON.toString(), DittoHeaders.empty(),
                        ThingCommandRegistry.newInstance()).getName();
        assertThat(name).isEqualTo(ExecuteBatch.NAME);
    }

}
