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
package org.eclipse.ditto.signals.commands.live.modify;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyFeaturesLiveCommandImpl}.
 */
public final class ModifyFeaturesLiveCommandImplTest {

    private ModifyFeatures twinCommand;
    private ModifyFeaturesLiveCommand underTest;

    /** */
    @Before
    public void setUp() {
        twinCommand = ModifyFeatures.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FEATURES,
                DittoHeaders.empty());
        underTest = ModifyFeaturesLiveCommandImpl.of(twinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeaturesLiveCommandImpl.class,
                areImmutable(),
                provided(Features.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyFeaturesLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingModifyCommand", "features")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetModifyFeaturesLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ModifyFeaturesLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetModifyFeaturesLiveCommandForCreateThingCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> ModifyFeaturesLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(MessageFormat.format("cannot be cast to {0}", ModifyFeatures.class.getName()))
                .withNoCause();
    }

    /** */
    @Test
    public void getModifyFeaturesLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(twinCommand.getType())
                .withDittoHeaders(twinCommand.getDittoHeaders())
                .withId(twinCommand.getThingId())
                .withManifest(twinCommand.getManifest())
                .withResourcePath(twinCommand.getResourcePath());
        assertThat(underTest.getFeatures()).isEqualTo(twinCommand.getFeatures());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final ModifyFeaturesLiveCommand newModifyFeaturesLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newModifyFeaturesLiveCommand).withDittoHeaders(emptyDittoHeaders);
    }

    /** */
    @Test
    public void answerReturnsNotNull() {
        Assertions.assertThat(underTest.answer()).isNotNull();
    }

    /** */
    @Test
    public void toStringReturnsExpected() {
        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains("command=")
                .contains(twinCommand.toString());
    }

}