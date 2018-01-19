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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinition;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveFeatureDefinitionLiveCommandImpl}.
 */
public final class RetrieveFeatureDefinitionLiveCommandImplTest {

    private RetrieveFeatureDefinition retrieveFeatureDefinitionTwinCommand;
    private RetrieveFeatureDefinitionLiveCommand underTest;

    @Before
    public void setUp() {
        retrieveFeatureDefinitionTwinCommand = RetrieveFeatureDefinition.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID, DittoHeaders.empty());
        underTest = RetrieveFeatureDefinitionLiveCommandImpl.of(retrieveFeatureDefinitionTwinCommand);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureDefinitionLiveCommandImpl.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeatureDefinitionLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingQueryCommand", "featureId")
                .verify();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetRetrieveFeatureDefinitionLiveCommandForNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> RetrieveFeatureDefinitionLiveCommandImpl.of(null))
                .withMessage("The %s must not be null!", "command")
                .withNoCause();
    }

    @Test
    public void tryToGetRetrieveFeatureDefinitionLiveCommandForCreateAttributeCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> RetrieveFeatureDefinitionLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(MessageFormat.format("cannot be cast to {0}",
                        RetrieveFeatureDefinition.class.getName()))
                .withNoCause();
    }

    @Test
    public void getRetrieveFeatureDefinitionLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(retrieveFeatureDefinitionTwinCommand.getType())
                .withDittoHeaders(retrieveFeatureDefinitionTwinCommand.getDittoHeaders())
                .withId(retrieveFeatureDefinitionTwinCommand.getThingId())
                .withManifest(retrieveFeatureDefinitionTwinCommand.getManifest())
                .withResourcePath(retrieveFeatureDefinitionTwinCommand.getResourcePath());
        assertThat(underTest.getFeatureId()).isEqualTo(retrieveFeatureDefinitionTwinCommand.getFeatureId());
    }

    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final RetrieveFeatureDefinitionLiveCommand newRetrieveFeatureDefinitionLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newRetrieveFeatureDefinitionLiveCommand).withDittoHeaders(emptyDittoHeaders);
    }

    @Test
    public void answerReturnsNotNull() {
        assertThat(underTest.answer()).isNotNull();
    }

    @Test
    public void toStringReturnsExpected() {
        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains("command=")
                .contains(retrieveFeatureDefinitionTwinCommand.toString());
    }

}
