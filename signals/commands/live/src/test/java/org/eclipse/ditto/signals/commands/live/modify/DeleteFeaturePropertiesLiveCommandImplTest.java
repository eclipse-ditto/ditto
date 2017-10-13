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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link DeleteFeaturePropertiesLiveCommandImpl}.
 */
public final class DeleteFeaturePropertiesLiveCommandImplTest {

    private DeleteFeatureProperties twinCommand;
    private DeleteFeaturePropertiesLiveCommand underTest;

    /** */
    @Before
    public void setUp() {
        twinCommand = DeleteFeatureProperties.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID, DittoHeaders.empty());
        underTest = DeleteFeaturePropertiesLiveCommandImpl.of(twinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeaturePropertiesLiveCommandImpl.class, areImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DeleteFeaturePropertiesLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingModifyCommand", "featureId")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetDeleteFeaturePropertiesLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> DeleteFeaturePropertiesLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetDeleteFeaturePropertiesLiveCommandForCreateFeaturePropertiesCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> DeleteFeaturePropertiesLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(MessageFormat.format("cannot be cast to {0}",
                        DeleteFeatureProperties.class.getName()))
                .withNoCause();
    }

    /** */
    @Test
    public void getDeleteFeaturePropertiesLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(twinCommand.getType())
                .withDittoHeaders(twinCommand.getDittoHeaders())
                .withId(twinCommand.getThingId())
                .withManifest(twinCommand.getManifest())
                .withResourcePath(twinCommand.getResourcePath());
        assertThat(underTest.getFeatureId()).isEqualTo(twinCommand.getFeatureId());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final DeleteFeaturePropertiesLiveCommand newDeleteFeaturePropertiesLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newDeleteFeaturePropertiesLiveCommand).withDittoHeaders(emptyDittoHeaders);
    }

    /** */
    @Test
    public void answerReturnsNotNull() {
        assertThat(underTest.answer()).isNotNull();
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