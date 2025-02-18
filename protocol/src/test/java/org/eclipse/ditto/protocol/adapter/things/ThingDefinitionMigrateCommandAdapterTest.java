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
package org.eclipse.ditto.protocol.adapter.things;


import java.util.Collections;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.LiveTwinTest;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingDefinitionMigrateCommandAdapter}.
 */
public final class ThingDefinitionMigrateCommandAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingDefinitionMigrateCommandAdapter underTest;

    private TopicPath topicPath;

    @Before
    public void setUp() {
        underTest = ThingDefinitionMigrateCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
        topicPath = topicPath(TopicPath.Action.MIGRATE);
    }

    @Test
    public void migrateThingDefinitionFromAdaptable() {
        final ThingId thingId = TestConstants.THING_ID;
        final String definitionUrl = "https://example.com/model.tm.jsonld";
        final JsonObject migrationPayload = JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder().set("foo", "bar").build())
                .build();

        final MigrateThingDefinition expectedCommand = MigrateThingDefinition.of(
                thingId,
                definitionUrl,
                migrationPayload,
                Collections.emptyMap(),
                false,
                TestConstants.DITTO_HEADERS_V_2
        );

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(expectedCommand.toJson(JsonSchemaVersion.V_2))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MigrateThingDefinition actualCommand = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actualCommand).isEqualTo(expectedCommand);
    }

    @Test
    public void migrateThingDefinitionToAdaptable() {
        final ThingId thingId = TestConstants.THING_ID;
        final String definitionUrl = "https://example.com/model.tm.jsonld";
        final JsonObject migrationPayload = JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder().set("foo", "bar").build())
                .build();
        final MigrateThingDefinition command = MigrateThingDefinition.of(
                thingId,
                definitionUrl,
                migrationPayload,
                Collections.emptyMap(),
                true,
                TestConstants.DITTO_HEADERS_V_2
        );

        final Adaptable expectedAdaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(command.toJson())
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2)
                .build();

        final Adaptable actualAdaptable = underTest.toAdaptable(command, channel);

        assertWithExternalHeadersThat(actualAdaptable).isEqualTo(expectedAdaptable);
    }


}
