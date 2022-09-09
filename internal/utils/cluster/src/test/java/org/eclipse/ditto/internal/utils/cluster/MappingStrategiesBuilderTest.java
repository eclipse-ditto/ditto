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
package org.eclipse.ditto.internal.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.JsonParsableRegistry;
import org.eclipse.ditto.base.model.signals.ShardedMessageEnvelope;
import org.eclipse.ditto.internal.utils.akka.PingCommand;
import org.eclipse.ditto.internal.utils.akka.PingCommandResponse;
import org.eclipse.ditto.internal.utils.akka.SimpleCommand;
import org.eclipse.ditto.internal.utils.akka.SimpleCommandResponse;
import org.eclipse.ditto.internal.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;
import org.mutabilitydetector.internal.com.google.common.collect.Sets;

public final class MappingStrategiesBuilderTest {

    private static final JsonObject KNOWN_OBJECT = JsonFactory.newObject(MyJsonifiable.INSTANCE.toJsonString());
    private static final Jsonifiable<JsonObject> KNOWN_JSONIFIABLE = MyJsonifiable.INSTANCE;
    private static final DittoHeaders KNOWN_HEADERS = DittoHeaders.empty();
    private static final Class<MyJsonifiable> KNOWN_CLASS = MyJsonifiable.class;
    private static final String KNOWN_TYPE = KNOWN_CLASS.getSimpleName();

    @Test
    public void addJsonParsableRegistry() {
        final String[] types = new String[]{"a", "b", "c"};
        final JsonParsableRegistry<? extends Jsonifiable> registry = new JsonParsableRegistry() {
            @Override
            public Set<String> getTypes() {
                return Sets.newHashSet(types);
            }

            @Override
            public Object parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
                return null;
            }
        };

        final MappingStrategies strategies = MappingStrategiesBuilder.newInstance()
                .add(registry)
                .build();

        assertThat(strategies).containsKeys(types);
    }

    @Test
    public void addFunctionForClass() {
        final MappingStrategies strategies = MappingStrategiesBuilder.newInstance()
                .add(KNOWN_CLASS, MyJsonifiable::fromJson)
                .build();

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(strategies)
                    .as("contains key")
                    .containsKey(KNOWN_TYPE);
            softly.assertThat(strategies.get(KNOWN_TYPE))
                    .as("contains expected mapping strategy")
                    .satisfies(mappingStrategy -> softly.assertThat(mappingStrategy.parse(KNOWN_OBJECT, KNOWN_HEADERS))
                            .isEqualTo(KNOWN_JSONIFIABLE));
        }
    }

    @Test
    public void addFunctionForType() {
        final MappingStrategies strategies = MappingStrategiesBuilder.newInstance()
                .add(KNOWN_TYPE, MyJsonifiable::fromJson)
                .build();

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(strategies)
                    .as("contains key")
                    .containsKey(KNOWN_TYPE);
            softly.assertThat(strategies.get(KNOWN_TYPE))
                    .as("contains expected mapping strategy")
                    .satisfies(mappingStrategy -> softly.assertThat(mappingStrategy.parse(KNOWN_OBJECT, KNOWN_HEADERS))
                            .isEqualTo(KNOWN_JSONIFIABLE));
        }
    }

    @Test
    public void addBiFunctionForClass() {
        final MappingStrategies strategies = MappingStrategiesBuilder.newInstance()
                .add(KNOWN_CLASS, MyJsonifiable::fromJsonWithHeaders)
                .build();

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(strategies)
                    .as("contains key")
                    .containsKey(KNOWN_TYPE);
            softly.assertThat(strategies.get(KNOWN_TYPE))
                    .as("contains expected mapping strategy")
                    .satisfies(mappingStrategy -> softly.assertThat(mappingStrategy.parse(KNOWN_OBJECT, KNOWN_HEADERS))
                            .isEqualTo(KNOWN_JSONIFIABLE));
        }
    }

    @Test
    public void addBiFunctionForType() {
        final MappingStrategies strategies = MappingStrategiesBuilder.newInstance()
                .add(KNOWN_TYPE, MyJsonifiable::fromJsonWithHeaders)
                .build();

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(strategies)
                    .as("contains key")
                    .containsKey(KNOWN_TYPE);
            softly.assertThat(strategies.get(KNOWN_TYPE))
                    .as("contains expected mapping strategy")
                    .satisfies(mappingStrategy -> softly.assertThat(mappingStrategy.parse(KNOWN_OBJECT, KNOWN_HEADERS))
                            .isEqualTo(KNOWN_JSONIFIABLE));
        }
    }

    @Test
    public void defaultStrategies() {
        final MappingStrategies strategies = MappingStrategiesBuilder.newInstance().build();

        assertThat(strategies)
                .containsOnlyKeys(DittoHeaders.class.getSimpleName(),
                        ShardedMessageEnvelope.class.getSimpleName(),
                        SimpleCommand.class.getSimpleName(),
                        SimpleCommandResponse.class.getSimpleName(),
                        PingCommand.class.getSimpleName(),
                        PingCommandResponse.class.getSimpleName(),
                        StatusInfo.class.getSimpleName(),
                        StreamAck.class.getSimpleName());
    }

    private static final class MyJsonifiable implements Jsonifiable<JsonObject> {

        private static final MyJsonifiable INSTANCE = new MyJsonifiable();
        private static final JsonObject INNER_VALUE = JsonObject.newBuilder().set("text", "I am JSON value").build();

        private static MyJsonifiable fromJson(final JsonObject jsonObject) {
            assertThat(jsonObject).isEqualTo(INNER_VALUE);
            return INSTANCE;
        }

        private static MyJsonifiable fromJsonWithHeaders(final JsonObject jsonObject, final DittoHeaders headers) {
            assertThat(jsonObject).isEqualTo(INNER_VALUE);
            return INSTANCE;
        }

        @Override
        public JsonObject toJson() {
            return INNER_VALUE;
        }

        @Override
        public boolean equals(final Object obj) {
            return super.equals(obj);
        }

    }

}
