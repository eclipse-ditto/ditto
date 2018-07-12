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
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.CommandStrategy.Context;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.CommandStrategy.Result;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link RetrieveAclStrategy}.
 */
public final class RetrieveAclStrategyTest {

    private static final long NEXT_REVISION = 42L;

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
        final Thing thing = TestConstants.Thing.THING_V1;

        final JsonObject aclJson = thing.getAccessControlList()
                .map(acl -> acl.toJson(JsonSchemaVersion.V_1))
                .orElse(JsonFactory.newObject());

        final ThingSnapshotter thingSnapshotter = Mockito.mock(ThingSnapshotter.class);
        final DiagnosticLoggingAdapter log = Mockito.mock(DiagnosticLoggingAdapter.class);
        final Context context = DefaultContext.getInstance(THING_ID, thing, NEXT_REVISION, log, thingSnapshotter);
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final RetrieveAcl command = RetrieveAcl.of(THING_ID, dittoHeaders);

        final Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(RetrieveAclResponse.of(THING_ID, aclJson, dittoHeaders));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void resultContainsEmptyJsonObject() {
        final Thing thing = TestConstants.Thing.THING_V2;

        final ThingSnapshotter thingSnapshotter = Mockito.mock(ThingSnapshotter.class);
        final DiagnosticLoggingAdapter log = Mockito.mock(DiagnosticLoggingAdapter.class);
        final Context context = DefaultContext.getInstance(THING_ID, thing, NEXT_REVISION, log, thingSnapshotter);
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final RetrieveAcl command = RetrieveAcl.of(THING_ID, dittoHeaders);

        final Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveAclResponse.of(THING_ID, JsonFactory.newObject(), dittoHeaders));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}