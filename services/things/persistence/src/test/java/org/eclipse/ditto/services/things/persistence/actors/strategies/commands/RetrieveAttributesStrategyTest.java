/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.things.TestConstants.Thing.ATTRIBUTES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveAttributesStrategy}.
 */
public final class RetrieveAttributesStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveAttributesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveAttributesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAttributesStrategy.class, areImmutable());
    }

    @Test
    public void retrieveAttributesWithoutSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveAttributes command = RetrieveAttributes.of(THING_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveAttributesResponse.of(THING_ID, ATTRIBUTES.toJson(command.getImplementedSchemaVersion()),
                        DittoHeaders.empty()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveAttributesWithSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext();
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector("maker");
        final RetrieveAttributes command = RetrieveAttributes.of(THING_ID, selectedFields, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveAttributesResponse.of(THING_ID,
                        ATTRIBUTES.toJson(command.getImplementedSchemaVersion(), selectedFields),
                        DittoHeaders.empty()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveAttributesFromThingWithoutAttributes() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveAttributes command = RetrieveAttributes.of(THING_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeAttributes(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(ExceptionFactory.attributesNotFound(THING_ID, DittoHeaders.empty()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
