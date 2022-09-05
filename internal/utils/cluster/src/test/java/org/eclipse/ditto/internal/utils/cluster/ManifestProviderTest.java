/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandNotSupportedException;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ManifestProviderTest}.
 */
public final class ManifestProviderTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test","myThing");

    private static final Thing THING = Thing.newBuilder()
            .setId(THING_ID)
            .build();

    private ManifestProvider underTest = null;


    @Before
    public void setUp() {
        underTest = ManifestProvider.getInstance();
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(ManifestProvider.class, areImmutable());
    }


    @Test
    public void tryToGetManifestForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.apply(null))
                .withFailMessage("The %s must not be null!", "object")
                .withNoCause();
    }


    @Test
    public void getManifestFromCommandNotSupportedException() {
        final DittoRuntimeException exception = CommandNotSupportedException.newBuilder(1).build();
        final String manifest = underTest.apply(exception);

        assertThat(manifest).isEqualTo(exception.getErrorCode());
    }


    @Test
    public void getManifestFromThingCommand() {
        final ThingId thingId = THING_ID;
        final Command<RetrieveThing> retrieveThing = RetrieveThing.of(thingId, DittoHeaders.empty());
        final String manifest = underTest.apply(retrieveThing);

        assertThat(manifest).isEqualTo(retrieveThing.getType());
    }


    @Test
    public void getManifestFromThingCommandResponse() {
        final CommandResponse<CreateThingResponse> commandResponse = CreateThingResponse.of(THING,
                DittoHeaders.empty());
        final String manifest = underTest.apply(commandResponse);

        assertThat(manifest).isEqualTo(commandResponse.getType());
    }

}
