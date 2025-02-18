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


import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.LiveTwinTest;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinitionResponse;
import org.junit.Before;
import org.junit.Test;

public class ThingDefinitionMigrateThingDefinitionCommandResponseAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingDefinitionMigrateCommandResponseAdapter underTest;
    private final JsonObject patch = JsonFactory.newObjectBuilder()
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
    @Before
    public void setUp() {
        underTest = ThingDefinitionMigrateCommandResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }
    @Test
    public void migrateThingResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MIGRATE);
        final MigrateThingDefinitionResponse migrateResponse = MigrateThingDefinitionResponse.applied(
                TestConstants.THING_ID,
                patch,
                TestConstants.DITTO_HEADERS_V_2
        );

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(migrateResponse.getEntity().get())
                        .withStatus(HttpStatus.OK)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MigrateThingDefinitionResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(migrateResponse);
    }

    @Test
    public void migrateThingResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MIGRATE);
        final JsonPointer path = JsonPointer.empty();
        final MigrateThingDefinitionResponse migrateResponse = MigrateThingDefinitionResponse.applied(
                TestConstants.THING_ID,
                patch,
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(migrateResponse.getEntity().get())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final Adaptable actualMigrate = underTest.toAdaptable(migrateResponse, channel);
        System.out.println(actualMigrate);
        assertWithExternalHeadersThat(actualMigrate).isEqualTo(expected);
    }
}