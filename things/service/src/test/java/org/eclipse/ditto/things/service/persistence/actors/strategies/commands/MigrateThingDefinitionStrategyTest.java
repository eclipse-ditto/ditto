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

import java.util.Collections;

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
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionMigrated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link MigrateThingDefinitionStrategy} with injected mock of WotThingSkeletonGenerator.
 */
public final class MigrateThingDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private MigrateThingDefinitionStrategy underTest;

    @Before
    public void setUp() throws Exception {
        final ActorSystem actorSystem = ActorSystem.create("test", ConfigFactory.load("test"));
        underTest = new MigrateThingDefinitionStrategy(actorSystem);
    }


    @Test
    public void migrateExistingThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();
        final Thing existingThing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();

        final JsonObject migrationPayload = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder().set("manufacturer", "New Corp").build())
                .build();

        final String thingDefinitionUrl = "https://eclipse.dev/ditto/wot/example-models/dimmable-colored-lamp-1.0.0.tm.jsonld";

        final MigrateThingDefinition command = MigrateThingDefinition.of(
                thingId,
                thingDefinitionUrl,
                migrationPayload,
                Collections.emptyMap(),
                true,
                DittoHeaders.empty()
        );

        final MigrateThingDefinitionResponse expectedResponse = ETagTestUtils.migrateThingDefinitionResponse(thingId,
                getThingJson(thingDefinitionUrl),
                getMergedThing(thingDefinitionUrl),
                command.getDittoHeaders());

        final Result<ThingEvent<?>> result = underTest.apply(context, existingThing, NEXT_REVISION, command);

        result.mapStages(completionStage -> {
            completionStage.toCompletableFuture().join();
            return completionStage;
        });

        assertStagedModificationResult(result, ThingDefinitionMigrated.class, expectedResponse, false);
    }

    private static JsonObject getThingJson(String thingDefinitionUrl) {
        return JsonFactory.newObjectBuilder()
                .set("definition", thingDefinitionUrl)
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("manufacturer", "New Corp")
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


    private Thing getMergedThing(final String thingDefinitionUrl) {
        return ThingsModelFactory.newThingBuilder(getThingJson(thingDefinitionUrl))
                .setRevision(ThingRevision.newInstance(NEXT_REVISION))
                .build();
    }

}
