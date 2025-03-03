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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionMigrated;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.eclipse.ditto.wot.api.generator.WotThingSkeletonGenerator;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import org.mockito.Mockito;

/**
 * Unit test for {@link MigrateThingDefinitionStrategy}, verifying correct behavior when using a mocked {@link WotThingSkeletonGenerator}.
 */
public final class MigrateThingDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private static final String TEST_THING_DEFINITION_URL = "https://mock-model-1.0.0.tm.jsonld";

    private MigrateThingDefinitionStrategy underTest;
    private final WotThingSkeletonGenerator skeletonGeneratorMock = Mockito.mock(WotThingSkeletonGenerator.class);

    @Before
    public void setUp() throws Exception {
        final ActorSystem actorSystem = ActorSystem.create("test", ConfigFactory.load("test"));
        underTest = new MigrateThingDefinitionStrategy(actorSystem);
        injectSkeletonGeneratorMock(underTest, skeletonGeneratorMock);
    }

    @Test
    public void migrateExistingThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();
        final Thing existingThing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();

        final Thing skeletonThing = ThingsModelFactory.newThing(getExpectedThingJson());

        Mockito.when(skeletonGeneratorMock.provideThingSkeletonForCreation(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(skeletonThing)));

        final MigrateThingDefinition command = MigrateThingDefinition.of(
                thingId,
                TEST_THING_DEFINITION_URL,
                JsonObject.empty(),
                Collections.emptyMap(),
                true,
                DittoHeaders.empty()
        );

        final MigrateThingDefinitionResponse expectedResponse = ETagTestUtils.migrateThingDefinitionResponse(
                thingId,
                getExpectedThingJson(),
                getExpectedMergedThing(),
                command.getDittoHeaders()
        );

        assertStagedModificationResult(underTest, existingThing, command, ThingDefinitionMigrated.class, expectedResponse);
    }

    private void injectSkeletonGeneratorMock(final MigrateThingDefinitionStrategy strategy,
            final WotThingSkeletonGenerator skeletonGeneratorMock) throws Exception {
        Class<?> clazz = strategy.getClass();
        while (clazz != null && !clazz.equals(Object.class)) {
            try {
                final Field field = clazz.getDeclaredField("wotThingSkeletonGenerator");
                field.setAccessible(true);
                field.set(strategy, skeletonGeneratorMock);
                return;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("wotThingSkeletonGenerator not found in class hierarchy");
    }

    private static JsonObject getExpectedThingJson() {
        return JsonFactory.newObjectBuilder()
                .set("definition", TEST_THING_DEFINITION_URL)
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("on", false)
                        .set("color", JsonFactory.newObjectBuilder()
                                .set("r", 0)
                                .set("g", 0)
                                .set("b", 0)
                                .build())
                        .set("dimmer-level", 0.0)
                        .build())
                .build();
    }

    private Thing getExpectedMergedThing() {
        return ThingsModelFactory.newThingBuilder(getExpectedThingJson())
                .setRevision(ThingRevision.newInstance(NEXT_REVISION))
                .build();
    }

}
