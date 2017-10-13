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
package org.eclipse.ditto.signals.events.batch;

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
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link BatchExecutionFinished}.
 */
public final class BatchExecutionFinishedTest {

    private static final String KNOWN_BATCH_ID = UUID.randomUUID().toString();

    private static final List<CommandResponse> KNOWN_RESPONSES = Arrays.asList(
            ModifyThingResponse.created(Thing.newBuilder()
                            .setId("org.eclipse.ditto.test:myThing1")
                            .build(),
                    DittoHeaders.empty()),
            ModifyThingResponse.created(Thing.newBuilder()
                            .setId("org.eclipse.ditto.test:myThing2")
                            .build(),
                    DittoHeaders.empty()),
            ModifyThingResponse.created(Thing.newBuilder()
                            .setId("org.eclipse.ditto.test:myThing3")
                            .build(),
                    DittoHeaders.empty()));

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TYPE, BatchExecutionFinished.TYPE)
            .set(Event.JsonFields.TIMESTAMP, null)
            .set(BatchExecutionFinished.JsonFields.BATCH_ID, KNOWN_BATCH_ID)
            .set(BatchExecutionFinished.JsonFields.RESPONSES, JsonArray.newBuilder()
                    .add(JsonFactory.newObjectBuilder()
                            .set(BatchExecutionFinished.JsonFields.RESPONSE,
                                    KNOWN_RESPONSES.get(0).toJson(JsonSchemaVersion.V_2,
                                            FieldType.regularOrSpecial()))
                            .set(BatchExecutionFinished.JsonFields.DITTO_HEADERS,
                                    KNOWN_RESPONSES.get(0).getDittoHeaders().toJson())
                            .build()
                    )
                    .add(JsonFactory.newObjectBuilder()
                            .set(BatchExecutionFinished.JsonFields.RESPONSE,
                                    KNOWN_RESPONSES.get(1).toJson(JsonSchemaVersion.V_2,
                                            FieldType.regularOrSpecial()))
                            .set(BatchExecutionFinished.JsonFields.DITTO_HEADERS, KNOWN_RESPONSES.get(1)
                                    .getDittoHeaders().toJson())
                            .build()
                    )
                    .add(JsonFactory.newObjectBuilder()
                            .set(BatchExecutionFinished.JsonFields.RESPONSE,
                                    KNOWN_RESPONSES.get(2).toJson(JsonSchemaVersion.V_2,
                                            FieldType.regularOrSpecial()))
                            .set(BatchExecutionFinished.JsonFields.DITTO_HEADERS, KNOWN_RESPONSES.get(2)
                                    .getDittoHeaders().toJson())
                            .build()
                    )
                    .build())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(BatchExecutionFinished.class, areImmutable(),
                provided(CommandResponse.class, DittoHeaders.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(BatchExecutionFinished.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullBatchId() {
        BatchExecutionFinished.of(null, KNOWN_RESPONSES, DittoHeaders.empty());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullResponses() {
        BatchExecutionFinished.of(KNOWN_BATCH_ID, null, DittoHeaders.empty());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDittoHeaders() {
        BatchExecutionFinished.of(KNOWN_BATCH_ID, KNOWN_RESPONSES, null);
    }

    @Test
    public void toJsonReturnsExpected() {
        final BatchExecutionFinished underTest =
                BatchExecutionFinished.of(KNOWN_BATCH_ID, KNOWN_RESPONSES, DittoHeaders.empty());
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final BatchExecutionFinished underTest =
                BatchExecutionFinished.fromJson(KNOWN_JSON.toString(), DittoHeaders.empty(),
                        ThingCommandResponseRegistry.newInstance());

        assertThat(underTest).isNotNull();
        assertThat(underTest.getBatchId()).isEqualTo(KNOWN_BATCH_ID);
        assertThat(underTest.getCommandResponses()).isEqualTo(KNOWN_RESPONSES);
    }

    @Test
    public void retrieveEventName() {
        final String name =
                BatchExecutionFinished.fromJson(KNOWN_JSON.toString(), DittoHeaders.empty(),
                        ThingCommandResponseRegistry.newInstance()).getName();
        assertThat(name).isEqualTo(BatchExecutionFinished.NAME);
    }

}
