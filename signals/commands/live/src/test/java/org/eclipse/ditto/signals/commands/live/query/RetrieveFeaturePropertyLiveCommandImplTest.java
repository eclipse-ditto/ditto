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
package org.eclipse.ditto.signals.commands.live.query;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link RetrieveFeaturePropertyLiveCommandImpl}.
 */
public final class RetrieveFeaturePropertyLiveCommandImplTest {

    private RetrieveFeatureProperty retrieveFeaturePropertyTwinCommand;
    private RetrieveFeaturePropertyLiveCommand underTest;

    /** */
    @Before
    public void setUp() {
        retrieveFeaturePropertyTwinCommand = RetrieveFeatureProperty.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER,
                DittoHeaders.empty());
        underTest = RetrieveFeaturePropertyLiveCommandImpl.of(retrieveFeaturePropertyTwinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeaturePropertyLiveCommandImpl.class,
                areImmutable(),
                provided(JsonPointer.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeaturePropertyLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingQueryCommand", "featureId", "propertyPointer")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetRetrieveFeaturePropertyLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveFeaturePropertyLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetRetrieveFeaturePropertyLiveCommandForCreateAttributeCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> RetrieveFeaturePropertyLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(MessageFormat.format("cannot be cast to {0}",
                        RetrieveFeatureProperty.class.getName()))
                .withNoCause();
    }

    /** */
    @Test
    public void getRetrieveFeaturePropertyLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(retrieveFeaturePropertyTwinCommand.getType())
                .withDittoHeaders(retrieveFeaturePropertyTwinCommand.getDittoHeaders())
                .withId(retrieveFeaturePropertyTwinCommand.getThingId())
                .withManifest(retrieveFeaturePropertyTwinCommand.getManifest())
                .withResourcePath(retrieveFeaturePropertyTwinCommand.getResourcePath());
        assertThat(underTest.getFeatureId()).isEqualTo(retrieveFeaturePropertyTwinCommand.getFeatureId());
        assertThat(underTest.getPropertyPointer())
                .isEqualTo(retrieveFeaturePropertyTwinCommand.getPropertyPointer());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final RetrieveFeaturePropertyLiveCommand newRetrieveFeaturePropertyLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newRetrieveFeaturePropertyLiveCommand).withDittoHeaders(emptyDittoHeaders);
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
                .contains(retrieveFeaturePropertyTwinCommand.toString());
    }

}