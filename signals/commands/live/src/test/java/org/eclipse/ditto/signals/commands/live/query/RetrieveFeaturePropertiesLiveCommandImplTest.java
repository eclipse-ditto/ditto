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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link RetrieveFeaturePropertiesLiveCommandImpl}.
 */
public final class RetrieveFeaturePropertiesLiveCommandImplTest {

    private RetrieveFeatureProperties retrieveFeaturePropertiesTwinCommand;
    private RetrieveFeaturePropertiesLiveCommand underTest;

    /** */
    @Before
    public void setUp() {
        retrieveFeaturePropertiesTwinCommand = RetrieveFeatureProperties.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.JSON_FIELD_SELECTOR_ATTRIBUTES,
                DittoHeaders.empty());
        underTest = RetrieveFeaturePropertiesLiveCommandImpl.of(retrieveFeaturePropertiesTwinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeaturePropertiesLiveCommandImpl.class, areImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeaturePropertiesLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingQueryCommand", "featureId")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetRetrieveFeaturePropertiesLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveFeaturePropertiesLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetRetrieveFeaturePropertiesLiveCommandForCreateAttributeCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> RetrieveFeaturePropertiesLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(MessageFormat.format("cannot be cast to {0}",
                        RetrieveFeatureProperties.class.getName()))
                .withNoCause();
    }

    /** */
    @Test
    public void getRetrieveFeaturePropertiesLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(retrieveFeaturePropertiesTwinCommand.getType())
                .withDittoHeaders(retrieveFeaturePropertiesTwinCommand.getDittoHeaders())
                .withId(retrieveFeaturePropertiesTwinCommand.getThingId())
                .withManifest(retrieveFeaturePropertiesTwinCommand.getManifest())
                .withResourcePath(retrieveFeaturePropertiesTwinCommand.getResourcePath());
        assertThat(underTest.getFeatureId()).isEqualTo(retrieveFeaturePropertiesTwinCommand.getFeatureId());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final RetrieveFeaturePropertiesLiveCommand newRetrieveFeaturePropertiesLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newRetrieveFeaturePropertiesLiveCommand).withDittoHeaders(emptyDittoHeaders);
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
                .contains(retrieveFeaturePropertiesTwinCommand.toString());
    }

}