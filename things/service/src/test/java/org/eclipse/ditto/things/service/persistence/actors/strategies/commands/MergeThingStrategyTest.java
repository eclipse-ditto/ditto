/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link MergeThingStrategy}.
 */
public final class MergeThingStrategyTest extends AbstractCommandStrategyTest {

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
                ETagTestUtils.mergeThingResponse(existing, path, mergeThing.getDittoHeaders());
        assertModificationResult(underTest, existing, mergeThing, ThingMerged.class, expectedCommandResponse);
    }

    @Test
    public void mergeThingWithLargeAttributeExpectThingTooLargeException() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();
        final Thing existing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();
        final JsonPointer path = Thing.JsonFields.ATTRIBUTES.getPointer().append(JsonPointer.of("large"));
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
        final MergeThing mergeThing = MergeThing.withAttribute(thingId, path,
                JsonValue.of("~".repeat((int) THING_SIZE_LIMIT_BYTES - 150)), dittoHeaders);
        assertThatExceptionOfType(ThingTooLargeException.class)
                .isThrownBy(() -> underTest.apply(context, existing, NEXT_REVISION, mergeThing))
                .satisfies(e -> assertThat(e.getDittoHeaders()).containsAllEntriesOf(dittoHeaders));
    }
}
