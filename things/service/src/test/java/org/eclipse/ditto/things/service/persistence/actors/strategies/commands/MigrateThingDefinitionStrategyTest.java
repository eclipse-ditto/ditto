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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMigrated;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.eclipse.ditto.wot.api.generator.WotThingSkeletonGenerator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link MigrateThingDefinitionStrategy} with injected mock of WotThingSkeletonGenerator.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MigrateThingDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private MigrateThingDefinitionStrategy underTest;
    private WotThingSkeletonGenerator mockWotThingSkeletonGenerator;

    @Before
    public void setUp() throws Exception {
        final ActorSystem actorSystem = ActorSystem.create("test", ConfigFactory.load("test"));

        mockWotThingSkeletonGenerator = mock(WotThingSkeletonGenerator.class);

        underTest = new MigrateThingDefinitionStrategy(actorSystem);

        injectMock(underTest, "wotThingSkeletonGenerator", mockWotThingSkeletonGenerator);

        when(mockWotThingSkeletonGenerator.provideThingSkeletonForCreation(any(ThingId.class), any(), any(DittoHeaders.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(createMockThingSkeleton())));
    }

    /**
     * Injects a mock into a private field of the given object.
     */
    private void injectMock(Object targetObject, String fieldName, Object mock) throws Exception {
        Field field = null;
        Class<?> clazz = targetObject.getClass();

        while (clazz != Object.class) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }

        if (field == null) {
            throw new NoSuchFieldException("Field " + fieldName + " not found in class hierarchy.");
        }

        field.setAccessible(true);
        field.set(targetObject, mock);
    }


    @Test
    public void migrateExistingThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();
        final Thing existingThing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();

        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder().set("manufacturer", "New Corp").build())
                .build();

        final String mockThingDefinitionUrl = "http://mock-url-for-test.com/model.json";

        final MigrateThingDefinition command = MigrateThingDefinition.of(
                thingId,
                mockThingDefinitionUrl,
                migrationPayload,
                null,
                true,
                DittoHeaders.empty()
        );

        final MigrateThingDefinitionResponse expectedResponse = ETagTestUtils.migrateThingDefinitionResponse(thingId,
                JsonFactory.newObjectBuilder()
                        .set("definition", mockThingDefinitionUrl)
                        .set("attributes", JsonFactory.newObjectBuilder()
                                .set("manufacturer", "New Corp")
                                .build())
                        .build(),
                createMockThingSkeleton(),
                command.getDittoHeaders());

        final Result<ThingEvent<?>> result = underTest.apply(context, existingThing, NEXT_REVISION, command);

        result.mapStages(completionStage -> {
            completionStage.toCompletableFuture().join();
            return completionStage;
        });

        assertStagedModificationResult(result, ThingMigrated.class, expectedResponse, false);
    }

    /**
     * Creates a mock Thing skeleton to avoid real network calls.
     */
    private Thing createMockThingSkeleton() {
        return ThingsModelFactory.newThingBuilder()
                .setAttributes(JsonFactory.newObjectBuilder()
                        .set("manufacturer", "MockCorp")
                        .build())
                .setRevision(ThingRevision.newInstance(NEXT_REVISION))
                .build();
    }

}
