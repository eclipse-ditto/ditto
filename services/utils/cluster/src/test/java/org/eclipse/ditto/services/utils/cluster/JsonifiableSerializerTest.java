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
package org.eclipse.ditto.services.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.ThingCommandRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingErrorRegistry;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ExtendedActorSystem;

/**
 * Unit test for {@link JsonifiableSerializer}
 */
public final class JsonifiableSerializerTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationSubjects("authSubject")
            .correlationId("correlationId")
            .source("source")
            .schemaVersion(JsonSchemaVersion.LATEST)
            .build();

    private static final String THING_ID = "org.eclipse.ditto.test:myThing";

    private static final Thing THING = Thing.newBuilder()
            .setId(THING_ID)
            .build();

    private JsonifiableSerializer underTestForThingCommands;

    @Before
    public void setUp() throws Exception {
        final ExtendedActorSystem actorSystem =
                (ExtendedActorSystem) ExtendedActorSystem.create("test", ConfigFactory.empty()
                        .withValue("ditto.mapping-strategy.implementation",
                                ConfigValueFactory.fromAnyRef(ThingCommandsStrategy.class.getName())));
        underTestForThingCommands = new JsonifiableSerializer(actorSystem);
    }

    @Test
    public void ensureSimpleMappingStrategyWithOnlyDittoHeadersWorks() {
        final ExtendedActorSystem actorSystem =
                (ExtendedActorSystem) ExtendedActorSystem.create("test", ConfigFactory.empty()
                        .withValue("ditto.mapping-strategy.implementation",
                                ConfigValueFactory.fromAnyRef(DittoHeadersStrategy.class.getName())));
        final JsonifiableSerializer underTest = new JsonifiableSerializer(actorSystem);

        final byte[] bytes = underTest.toBinary(DITTO_HEADERS);
        final Object o = underTest.fromBinary(bytes, DittoHeaders.class.getSimpleName());
        Assertions.assertThat(o).isEqualTo(DITTO_HEADERS);
    }

    static final class DittoHeadersStrategy implements MappingStrategy {

        @Override
        public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
            return MappingStrategiesBuilder.newInstance()
                    .add(DittoHeaders.class, jsonObject -> DittoHeaders.newBuilder(jsonObject).build())
                    .build();
        }

    }

    @Test
    public void thingCommandSerializationWorksAsExpected() {
        final CreateThing createThing = CreateThing.of(THING, null, DITTO_HEADERS);

        final byte[] serialized = underTestForThingCommands.toBinary(createThing);
        final Object deserialized =
                underTestForThingCommands.fromBinary(serialized, underTestForThingCommands.manifest(createThing));

        assertThat(deserialized)
                .isInstanceOf(CreateThing.class)
                .isEqualTo(createThing);
    }

    @Test
    public void thingCommandResponseSerializationWorksAsExpected() {
        final CreateThingResponse createThingResponse = CreateThingResponse.of(THING, DITTO_HEADERS);

        final byte[] serialized = underTestForThingCommands.toBinary(createThingResponse);
        final Object deserialized = underTestForThingCommands.fromBinary(serialized,
                underTestForThingCommands.manifest(createThingResponse));

        assertThat(deserialized)
                .isInstanceOf(CreateThingResponse.class)
                .isEqualTo(createThingResponse);
    }

    @Test
    public void shardedMessageEnvelopeSerializationWorksAsExpected() {
        final String id = UUID.randomUUID().toString();
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final RetrieveThings retrieveThings = RetrieveThings.getBuilder(THING_ID)
                .dittoHeaders(dittoHeaders)
                .build();
        final JsonObject jsonObject = retrieveThings.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial());

        final ShardedMessageEnvelope shardedMessageEnvelope =
                ShardedMessageEnvelope.of(id, RetrieveThings.TYPE, jsonObject, dittoHeaders);

        final byte[] serialized = underTestForThingCommands.toBinary(shardedMessageEnvelope);
        final Object deserialized = underTestForThingCommands.fromBinary(serialized,
                underTestForThingCommands.manifest(shardedMessageEnvelope));

        assertThat(deserialized).isInstanceOf(ShardedMessageEnvelope.class);
        assertThat(((ShardedMessageEnvelope) deserialized).getId()).isEqualTo(shardedMessageEnvelope.getId());
        assertThat(((ShardedMessageEnvelope) deserialized).getType()).isEqualTo(shardedMessageEnvelope.getType());
        assertThat(((ShardedMessageEnvelope) deserialized).getMessage().toString())
                .isEqualTo(shardedMessageEnvelope.getMessage().toString());
        assertThat(((ShardedMessageEnvelope) deserialized).getDittoHeaders())
                .isEqualTo(shardedMessageEnvelope.getDittoHeaders());
    }

    static final class ThingCommandsStrategy implements MappingStrategy {

        @Override
        public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
            return MappingStrategiesBuilder.newInstance()
                    .add(ThingErrorRegistry.newInstance())
                    .add(ThingCommandRegistry.newInstance())
                    .add(ThingCommandResponseRegistry.newInstance())
                    .add(Thing.class, (jsonObject) -> ThingsModelFactory.newThing(jsonObject)) // do not replace with lambda!
                    .add(ShardedMessageEnvelope.class, (jsonObject) -> ShardedMessageEnvelope.fromJson(jsonObject)) // do not replace with lambda!
                    .build();
        }

    }

}
