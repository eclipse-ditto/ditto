/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.mergeThingResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;
import org.eclipse.ditto.signals.events.things.ThingMerged;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link MergeThingStrategy}.
 */
public final class MergeThingStrategyTest extends AbstractCommandStrategyTest {

    private static final DittoHeaders V1_HEADER =
            DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_1).build();

    private MergeThingStrategy underTest;

    @Before
    public void setUp() {
        underTest = new MergeThingStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MergeThingStrategy.class, areImmutable());
    }

    @Test
    public void mergeThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();

        final Thing existing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();

        final Thing thingToMerge = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setAttribute(JsonPointer.of("newAttribute"), JsonValue.of("attributeValue"))
                .setFeature("newFeature",
                        ThingsModelFactory.newFeaturePropertiesBuilder().set("newIntProperty", 123).build())
                .build();

        final JsonPointer path = JsonPointer.of("/");
        final JsonObject thingJson = thingToMerge.toJson();

        final MergeThing mergeThing = MergeThing.of(thingId, path, thingJson, DittoHeaders.empty());
        final MergeThingResponse expectedCommandResponse =
                mergeThingResponse(existing, path, thingJson, mergeThing.getDittoHeaders());
        assertModificationResult(underTest, existing, mergeThing, ThingMerged.class, expectedCommandResponse);
    }

    @Test
    public void mergeV2ThingWithV1CommandExpectCommandNotSupportedException() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();
        final Thing existing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();
        final JsonPointer path = JsonPointer.empty();
        final MergeThing mergeThing = MergeThing.of(thingId, path, JsonObject.empty(), V1_HEADER);
        assertErrorResult(underTest, existing, mergeThing,
                CommandNotSupportedException.newBuilder(JsonSchemaVersion.V_1.toInt()).build());
    }

    @Test
    public void mergeV1ThingWithV2CommandExpectCommandNotSupportedException() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();
        final Thing existing = THING_V1.toBuilder().setRevision(NEXT_REVISION - 1).build();
        final JsonPointer path = JsonPointer.empty();
        final MergeThing mergeThing = MergeThing.of(thingId, path, JsonObject.empty(), DittoHeaders.empty());
        assertErrorResult(underTest, existing, mergeThing,
                CommandNotSupportedException.newBuilder(JsonSchemaVersion.V_1.toInt()).build());
    }

}
