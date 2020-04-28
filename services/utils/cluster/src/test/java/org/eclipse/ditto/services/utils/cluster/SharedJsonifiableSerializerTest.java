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

import java.util.Map;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ExtendedActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link JsonJsonifiableSerializer} and {@link CborJsonifiableSerializer}.
 */
@RunWith(Enclosed.class)
public final class SharedJsonifiableSerializerTest {

    private static enum SerializerImplementation {

        JSONIFIABLE_SERIALIZER {
            @Override
            public AbstractJsonifiableWithDittoHeadersSerializer getInstance(final ExtendedActorSystem actorSystem) {
                return new JsonJsonifiableSerializer(actorSystem);
            }
        },
        CBOR_JSONIFIABLE_SERIALIZER {
            @Override
            public AbstractJsonifiableWithDittoHeadersSerializer getInstance(final ExtendedActorSystem actorSystem) {
                return new CborJsonifiableSerializer(actorSystem);
            }
        };

        abstract AbstractJsonifiableWithDittoHeadersSerializer getInstance(ExtendedActorSystem actorSystem);

    }

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance("authSubject")))
            .correlationId("correlationId")
            .schemaVersion(JsonSchemaVersion.LATEST)
            .build();

    private static ExtendedActorSystem getActorSystem(final Class<?> implClass) {
        final Config cfg = ConfigFactory.parseMap(Map.of("ditto.mapping-strategy.implementation", implClass.getName()));
        return (ExtendedActorSystem) ExtendedActorSystem.create("test", cfg);
    }

    @RunWith(Parameterized.class)
    public static final class ThingCommandsStrategyTest {

        private static ThingId thingId;
        private static Thing thing;
        private static ExtendedActorSystem actorSystem;

        @Parameterized.Parameter
        public SerializerImplementation serializerImplementation;

        private AbstractJsonifiableWithDittoHeadersSerializer underTest;

        @Parameterized.Parameters(name = "{0}")
        public static SerializerImplementation[] getSerializers() {
            return SerializerImplementation.values();
        }

        @BeforeClass
        public static void setUpClass() {
            thingId = ThingId.generateRandom();
            thing = Thing.newBuilder().setId(thingId).build();
            actorSystem = getActorSystem(ThingCommandsStrategy.class);
        }

        @AfterClass
        public static void tearDownClass() {
            TestKit.shutdownActorSystem(actorSystem);
        }

        @Before
        public void setUp() {
            underTest = serializerImplementation.getInstance(actorSystem);
        }

        @Test
        public void thingCommandSerializationWorksAsExpected() {
            final CreateThing createThing = CreateThing.of(thing, null, DITTO_HEADERS);

            final byte[] serialized = underTest.toBinary(createThing);
            final Object deserialized = underTest.fromBinary(serialized, underTest.manifest(createThing));

            assertThat(deserialized).isEqualTo(createThing);
        }

        @Test
        public void thingCommandResponseSerializationWorksAsExpected() {
            final CreateThingResponse createThingResponse = CreateThingResponse.of(thing, DITTO_HEADERS);

            final byte[] serialized = underTest.toBinary(createThingResponse);
            final Object deserialized = underTest.fromBinary(serialized, underTest.manifest(createThingResponse));

            assertThat(deserialized).isEqualTo(createThingResponse);
        }

        @Test
        public void shardedMessageEnvelopeSerializationWorksAsExpected() {
            final EntityId id = DefaultEntityId.generateRandom();
            final DittoHeaders dittoHeaders = DittoHeaders.empty();
            final RetrieveThings retrieveThings = RetrieveThings.getBuilder(thingId)
                    .dittoHeaders(dittoHeaders)
                    .build();
            final JsonObject jsonObject = retrieveThings.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial());

            final ShardedMessageEnvelope shardedMessageEnvelope =
                    ShardedMessageEnvelope.of(id, RetrieveThings.TYPE, jsonObject, dittoHeaders);

            final byte[] serialized = underTest.toBinary(shardedMessageEnvelope);
            final Object deserialized = underTest.fromBinary(serialized, underTest.manifest(shardedMessageEnvelope));

            try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
                softly.assertThat(deserialized)
                        .as("expected instance type")
                        .isInstanceOf(ShardedMessageEnvelope.class);
                softly.assertThat((ShardedMessageEnvelope) deserialized).satisfies(actual -> {
                    softly.assertThat((CharSequence) actual.getEntityId())
                            .as("entity ID")
                            .isEqualTo(shardedMessageEnvelope.getEntityId());
                    softly.assertThat(actual.getType())
                            .as("type")
                            .isEqualTo(shardedMessageEnvelope.getType());
                    softly.assertThat(actual.getMessage())
                            .as("message")
                            .isEqualTo(shardedMessageEnvelope.getMessage());
                    softly.assertThat(actual.getDittoHeaders())
                            .as("DittoHeaders")
                            .isEqualTo(shardedMessageEnvelope.getDittoHeaders());
                });
            }
        }

        private static final class ThingCommandsStrategy extends MappingStrategies {

            ThingCommandsStrategy() {
                super(MappingStrategiesBuilder.newInstance()
                        .add(GlobalErrorRegistry.getInstance())
                        .add(GlobalCommandRegistry.getInstance())
                        .add(GlobalCommandResponseRegistry.getInstance())
                        .add(Thing.class, ThingsModelFactory::newThing)
                        .add(ShardedMessageEnvelope.class, ShardedMessageEnvelope::fromJson)
                        .build());
            }

        }

    }

    @RunWith(Parameterized.class)
    public static final class DittoHeadersStrategyTest {

        private static ExtendedActorSystem actorSystem;

        @Parameterized.Parameter
        public SerializerImplementation serializerImplementation;

        private AbstractJsonifiableWithDittoHeadersSerializer underTest;

        @Parameterized.Parameters(name = "{0}")
        public static SerializerImplementation[] getSerializers() {
            return SerializerImplementation.values();
        }

        @BeforeClass
        public static void setUpClass() {
            actorSystem = getActorSystem(DittoHeadersStrategy.class);
        }

        @AfterClass
        public static void tearDownClass() {
            TestKit.shutdownActorSystem(actorSystem);
        }

        @Before
        public void setUp() {
            underTest = serializerImplementation.getInstance(actorSystem);
        }

        @Test
        public void ensureSimpleMappingStrategyWithOnlyDittoHeadersWorks() {
            final byte[] bytes = underTest.toBinary(DITTO_HEADERS);
            final Object o = underTest.fromBinary(bytes, DittoHeaders.class.getSimpleName());

            assertThat(o).isEqualTo(DITTO_HEADERS);
        }

        private static final class DittoHeadersStrategy extends MappingStrategies {

            DittoHeadersStrategy() {
                super(MappingStrategiesBuilder.newInstance()
                        .add(DittoHeaders.class, jsonObject -> DittoHeaders.newBuilder(jsonObject).build())
                        .build());
            }

        }

    }

}
