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
package org.eclipse.ditto.services.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.base.GlobalCommandRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ExtendedActorSystem;

/**
 * Unit test for {@link JsonJsonifiableSerializer}
 */
@RunWith(Parameterized.class)
public final class SharedJsonifiableSerializerTest {

    private enum SerializerImplementation{
        JsonifiableSerializer,
        CborJsonifiableSerializer
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<SerializerImplementation> serializerImplementationsToTest() {
        return Arrays.asList(SerializerImplementation.JsonifiableSerializer, SerializerImplementation.CborJsonifiableSerializer);
    }

    @Parameterized.Parameter
    public SerializerImplementation serializerClass;

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationSubjects("authSubject")
            .correlationId("correlationId")
            .schemaVersion(JsonSchemaVersion.LATEST)
            .build();

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "myThing");

    private static final Thing THING = Thing.newBuilder()
            .setId(THING_ID)
            .build();

    private AbstractJsonifiableWithDittoHeadersSerializer underTestForThingCommands;

    @Before
    public void setUp() {
        final ExtendedActorSystem actorSystem =
                (ExtendedActorSystem) ExtendedActorSystem.create("test", ConfigFactory.empty()
                        .withValue("ditto.mapping-strategy.implementation",
                                ConfigValueFactory.fromAnyRef(ThingCommandsStrategy.class.getName())));
        underTestForThingCommands = createNewSerializer(actorSystem);
    }

    private AbstractJsonifiableWithDittoHeadersSerializer createNewSerializer(ExtendedActorSystem actorSystem){
        switch (serializerClass) {
            case JsonifiableSerializer:
                return new JsonJsonifiableSerializer(actorSystem);
            case CborJsonifiableSerializer:
                return new CborJsonifiableSerializer(actorSystem);
            default:
                throw new IllegalArgumentException("No test logic provided for serializer" + serializerClass.getClass());
        }
    }

    @Test
    public void ensureSimpleMappingStrategyWithOnlyDittoHeadersWorks() {
        final ExtendedActorSystem actorSystem =
                (ExtendedActorSystem) ExtendedActorSystem.create("test", ConfigFactory.empty()
                        .withValue("ditto.mapping-strategy.implementation",
                                ConfigValueFactory.fromAnyRef(DittoHeadersStrategy.class.getName())));
        final AbstractJsonifiableWithDittoHeadersSerializer underTest = createNewSerializer(actorSystem);

        final byte[] bytes = underTest.toBinary(DITTO_HEADERS);
        final Object o = underTest.fromBinary(bytes, DittoHeaders.class.getSimpleName());
        Assertions.assertThat(o).isEqualTo(DITTO_HEADERS);
    }

    static final class DittoHeadersStrategy extends AbstractMappingStrategies {

        protected DittoHeadersStrategy() {
            super(MappingStrategiesBuilder.newInstance()
                    .add(DittoHeaders.class, jsonObject -> DittoHeaders.newBuilder(jsonObject).build())
                    .build().getStrategies());
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
        final EntityId id = DefaultEntityId.generateRandom();
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
        assertThat((CharSequence) ((ShardedMessageEnvelope) deserialized).getEntityId())
                .isEqualTo(shardedMessageEnvelope.getEntityId());
        assertThat(((ShardedMessageEnvelope) deserialized).getType()).isEqualTo(shardedMessageEnvelope.getType());
        assertThat(((ShardedMessageEnvelope) deserialized).getMessage().toString())
                .isEqualTo(shardedMessageEnvelope.getMessage().toString());
        assertThat(((ShardedMessageEnvelope) deserialized).getDittoHeaders())
                .isEqualTo(shardedMessageEnvelope.getDittoHeaders());
    }

    static final class ThingCommandsStrategy extends AbstractMappingStrategies {

        protected ThingCommandsStrategy() {
            super(MappingStrategiesBuilder.newInstance()
                    .add(GlobalErrorRegistry.getInstance())
                    .add(GlobalCommandRegistry.getInstance())
                    .add(GlobalCommandResponseRegistry.getInstance())
                    .add(Thing.class,
                            (jsonObject) -> ThingsModelFactory.newThing(jsonObject)) // do not replace with lambda!
                    .add(ShardedMessageEnvelope.class,
                            (jsonObject) -> ShardedMessageEnvelope.fromJson(jsonObject)) // do not replace with lambda!
                    .build()
                    .getStrategies());
        }

    }

}
