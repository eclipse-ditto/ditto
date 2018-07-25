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
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.CommandStrategy.Context;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.CommandStrategy.Result;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveAclStrategy}.
 */
public final class RetrieveAclStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveAclStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveAclStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAclStrategy.class, areImmutable());
    }

    @Test
    public void resultContainsJsonOfExistingAcl() {
        final Context context = getDefaultContext();
        final RetrieveAcl command = RetrieveAcl.of(context.getThingId(), DittoHeaders.empty());

        final JsonObject expectedAclJson = THING_V1.getAccessControlList()
                .map(acl -> acl.toJson(JsonSchemaVersion.V_1))
                .orElse(JsonFactory.newObject());

        final Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveAclResponse.of(command.getThingId(), expectedAclJson, command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void resultContainsEmptyJsonObject() {
        final Context context = getDefaultContext();
        final RetrieveAcl command = RetrieveAcl.of(context.getThingId(), DittoHeaders.empty());

        final Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveAclResponse.of(command.getThingId(), JsonFactory.newObject(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
