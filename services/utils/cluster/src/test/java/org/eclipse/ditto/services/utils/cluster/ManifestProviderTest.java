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
package org.eclipse.ditto.services.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ManifestProviderTest}.
 */
public final class ManifestProviderTest {

    private static final String THING_ID = "org.eclipse.ditto.test:myThing";

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
        final String thingId = THING_ID;
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
