/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement.pre;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

public final class ModifyToCreateThingTransformerTest {

    private static final ActorSystem system = ActorSystem.create("test", ConfigFactory.load("test"));

    private ModifyToCreateThingTransformer underTest;

    @Before
    public void setup() {
        underTest = new ModifyToCreateThingTransformer(system, system.settings().config());
    }

    @Test
    public void modifyThingStaysModifyThingWhenAlreadyExisting() {
        new TestKit(system) {{
            final var thingId = ThingId.generateRandom();
            final var modifyThing = ModifyThing.of(thingId, Thing.newBuilder().setId(thingId).build(), null, null,
                    DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(modifyThing, getRef()).toCompletableFuture();
            expectMsgClass(SudoRetrieveThing.class);
            reply(SudoRetrieveThingResponse.of(JsonObject.empty(), DittoHeaders.empty()));

            final var result = resultFut.join();

            assertThat(result).isSameAs(modifyThing);
        }};
    }

    @Test
    public void mergeThingBecomesCreateThingPolicyWhenNotYetExisting() {
        new TestKit(system) {{
            final var thingId = ThingId.generateRandom();
            final var mergeThing = MergeThing.withThing(thingId, Thing.newBuilder().setId(thingId).build(),
                    DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(mergeThing, getRef()).toCompletableFuture();
            expectMsgClass(SudoRetrieveThing.class);
            reply(ThingNotAccessibleException.newBuilder(thingId).build());
            final Signal<?> result = resultFut.join();

            assertThat(result).isInstanceOf(CreateThing.class);
            final CreateThing createThing = (CreateThing) result;
            assertThat(createThing.getEntityId().toString()).hasToString(thingId.toString());
            assertThat(createThing.getThing()).isEqualTo(ThingsModelFactory.newThing(mergeThing.getValue().asObject()));
            assertThat(createThing.getDittoHeaders()).isSameAs(mergeThing.getDittoHeaders());
        }};
    }

    @Test
    public void mergeThingWithNullsBecomesCreateThingRemovingNullsWhenNotYetExisting() {
        new TestKit(system) {{
            final var thingId = ThingId.generateRandom();
            final var attributes = Attributes.newBuilder()
                    .set("one", 1)
                    .set("oops_null", JsonValue.nullLiteral())
                    .set("null_value", "null")
                    .build();
            final var mergeThing = MergeThing.withThing(thingId, Thing.newBuilder()
                            .setId(thingId)
                            .setAttributes(attributes)
                            .build(),
                    DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(mergeThing, getRef()).toCompletableFuture();
            expectMsgClass(SudoRetrieveThing.class);
            reply(ThingNotAccessibleException.newBuilder(thingId).build());
            final Signal<?> result = resultFut.join();

            assertThat(result).isInstanceOf(CreateThing.class);
            final CreateThing createThing = (CreateThing) result;
            assertThat(createThing.getEntityId().toString()).hasToString(thingId.toString());
            final Thing thingWithoutNullValues = ThingsModelFactory.newThingBuilder(mergeThing.getValue().asObject())
                    .removeAttribute(JsonPointer.of("oops_null"))
                    .build();
            assertThat(createThing.getThing()).isEqualTo(thingWithoutNullValues);
            assertThat(createThing.getDittoHeaders()).isSameAs(mergeThing.getDittoHeaders());
        }};
    }

    @Test
    public void mergeThingWithNestedNullsInFeaturesBecomesCreateThingRemovingNullsWhenNotYetExisting() {
        new TestKit(system) {{
            final var thingId = ThingId.generateRandom();
            final var attributes = Attributes.newBuilder()
                    .set("one", 1)
                    .set("oops_null", JsonValue.nullLiteral())
                    .set("null_value", "null")
                    .build();
            final var features = Features.newBuilder()
                    .set(Feature.newBuilder()
                            .properties(FeatureProperties.newBuilder()
                                    .set("some_property", "123")
                                    .set("some_property_null", JsonValue.nullLiteral())
                                    .set("some_object", JsonObject.newBuilder()
                                            .set("some_nested_int", 123)
                                            .set("some_nested_null", JsonValue.nullLiteral())
                                            .build()
                                    )
                                    .build()
                            )
                            .withId("feature1")
                            .build()
                    )
                    .set(Feature.newBuilder()
                            .desiredProperties(FeatureProperties.newBuilder()
                                    .set("some_desired_property", "123")
                                    .set("some_desired_property_null", JsonValue.nullLiteral())
                                    .set("some_desired_array", JsonArray.newBuilder()
                                            .add(JsonObject.newBuilder()
                                                    .set("some_desired_array_nested_int", 123)
                                                    .set("some_desired_array_nested_null", JsonValue.nullLiteral())
                                                    .build())
                                            .build()
                                    )
                                    .build()
                            )
                            .withId("feature2")
                            .build()
                    )
                    .build();
            final var mergeThing = MergeThing.withThing(thingId, Thing.newBuilder()
                            .setId(thingId)
                            .setAttributes(attributes)
                            .setFeatures(features)
                            .build(),
                    DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(mergeThing, getRef()).toCompletableFuture();
            expectMsgClass(SudoRetrieveThing.class);
            reply(ThingNotAccessibleException.newBuilder(thingId).build());
            final Signal<?> result = resultFut.join();

            assertThat(result).isInstanceOf(CreateThing.class);
            final CreateThing createThing = (CreateThing) result;
            assertThat(createThing.getEntityId().toString()).hasToString(thingId.toString());
            final Thing thingWithoutNullValues = ThingsModelFactory.newThingBuilder(mergeThing.getValue().asObject())
                    .removeAttribute(JsonPointer.of("oops_null"))
                    .removeFeatureProperty("feature1", JsonPointer.of("some_property_null"))
                    .removeFeatureProperty("feature1", JsonPointer.of("some_object/some_nested_null"))
                    .removeFeatureDesiredProperty("feature2", JsonPointer.of("some_desired_property_null"))
                    .setFeatureDesiredProperty("feature2", JsonPointer.of("some_desired_array"),
                                    JsonArray.of(JsonObject.newBuilder().set("some_desired_array_nested_int", 123).build())
                    )
                    .build();
            assertThat(createThing.getThing()).isEqualTo(thingWithoutNullValues);
            assertThat(createThing.getDittoHeaders()).isSameAs(mergeThing.getDittoHeaders());
        }};
    }

    @Test
    public void mergeThingStaysMergeThingWhenAlreadyExisting() {
        new TestKit(system) {{
            final var thingId = ThingId.generateRandom();
            final var mergeThing = MergeThing.withThing(thingId, Thing.newBuilder().setId(thingId).build(),
                    DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(mergeThing, getRef()).toCompletableFuture();
            expectMsgClass(SudoRetrieveThing.class);
            reply(SudoRetrieveThingResponse.of(JsonObject.empty(), DittoHeaders.empty()));
            final Signal<?> result = resultFut.join();

            assertThat(result).isSameAs(mergeThing);
        }};
    }

    @Test
    public void modifyThingBecomesCreateThingPolicyWhenNotYetExisting() {
        new TestKit(system) {{
            final var thingId = ThingId.generateRandom();
            final var modifyThing = ModifyThing.of(thingId, Thing.newBuilder().setId(thingId).build(), null, null,
                    DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(modifyThing, getRef()).toCompletableFuture();
            expectMsgClass(SudoRetrieveThing.class);
            reply(ThingNotAccessibleException.newBuilder(thingId).build());
            final Signal<?> result = resultFut.join();

            assertThat(result).isInstanceOf(CreateThing.class);
            final CreateThing createThing = (CreateThing) result;
            assertThat(createThing.getEntityId().toString()).hasToString(thingId.toString());
            assertThat(createThing.getThing()).isEqualTo(modifyThing.getThing());
            assertThat(createThing.getDittoHeaders()).isSameAs(modifyThing.getDittoHeaders());
        }};
    }

    @Test
    public void otherCommandsThanModifyThingAreJustPassedThrough() {
        new TestKit(system) {{
            final var thingId = ThingId.generateRandom();
            final var retrieveThing = RetrieveThing.of(thingId, DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(retrieveThing, getRef()).toCompletableFuture();
            expectNoMessage();
            final Signal<?> result = resultFut.join();

            assertThat(result).isSameAs(retrieveThing);
        }};
    }

}
