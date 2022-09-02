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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ModifyToCreateThingTransformerTest {

    @Mock
    public ThingExistenceChecker existenceChecker;
    private ModifyToCreateThingTransformer underTest;

    @Before
    public void setup() {
        underTest = new ModifyToCreateThingTransformer(existenceChecker);
    }

    @Test
    public void modifyThingStaysModifyThingWhenAlreadyExisting() {
        final var thingId = ThingId.generateRandom();
        final var modifyThing = ModifyThing.of(thingId, Thing.newBuilder().setId(thingId).build(), null, null,
                DittoHeaders.of(Map.of("foo", "bar")));
        when(existenceChecker.checkExistence(modifyThing)).thenReturn(CompletableFuture.completedStage(true));

        final Signal<?> result = underTest.apply(modifyThing).toCompletableFuture().join();

        assertThat(result).isSameAs(modifyThing);
        verify(existenceChecker).checkExistence(modifyThing);
    }

    @Test
    public void modifyThingBecomesCreateThingPolicyWhenNotYetExisting() {
        final var thingId = ThingId.generateRandom();
        final var modifyThing = ModifyThing.of(thingId, Thing.newBuilder().setId(thingId).build(), null, null,
                DittoHeaders.of(Map.of("foo", "bar")));
        when(existenceChecker.checkExistence(modifyThing)).thenReturn(CompletableFuture.completedStage(false));

        final Signal<?> result = underTest.apply(modifyThing).toCompletableFuture().join();

        assertThat(result).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) result;
        assertThat(createThing.getEntityId().toString()).hasToString(thingId.toString());
        assertThat(createThing.getThing()).isEqualTo(modifyThing.getThing());
        assertThat(createThing.getDittoHeaders()).isSameAs(modifyThing.getDittoHeaders());
        verify(existenceChecker).checkExistence(modifyThing);
    }

    @Test
    public void otherCommandsThanModifyThingAreJustPassedThrough() {
        final var thingId = ThingId.generateRandom();
        final var retrieveThing = RetrieveThing.of(thingId, DittoHeaders.of(Map.of("foo", "bar")));

        final Signal<?> result = underTest.apply(retrieveThing).toCompletableFuture().join();

        assertThat(result).isSameAs(retrieveThing);
        verifyNoInteractions(existenceChecker);
    }

}
