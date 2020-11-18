/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.live.query;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredProperties;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link RetrieveFeatureDesiredPropertiesLiveCommandImpl}.
 */
public final class RetrieveFeatureDesiredPropertiesLiveCommandImplTest {

    private RetrieveFeatureDesiredProperties retrieveFeatureDesiredPropertiesTwinCommand;
    private RetrieveFeatureDesiredPropertiesLiveCommand underTest;

    /** */
    @Before
    public void setUp() {
        retrieveFeatureDesiredPropertiesTwinCommand = RetrieveFeatureDesiredProperties.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID, TestConstants.JSON_FIELD_SELECTOR_ATTRIBUTES,
                DittoHeaders.empty());
        underTest = RetrieveFeatureDesiredPropertiesLiveCommandImpl.of(retrieveFeatureDesiredPropertiesTwinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureDesiredPropertiesLiveCommandImpl.class, areImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeatureDesiredPropertiesLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingQueryCommand", "featureId")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetRetrieveFeatureDesiredPropertiesLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveFeatureDesiredPropertiesLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetRetrieveFeatureDesiredPropertiesLiveCommandForCreateAttributeCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> RetrieveFeatureDesiredPropertiesLiveCommandImpl.of(commandMock))
                .withMessageContaining(RetrieveFeatureDesiredProperties.class.getName())
                .withNoCause();
    }

    /** */
    @Test
    public void getRetrieveFeatureDesiredPropertiesLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(retrieveFeatureDesiredPropertiesTwinCommand.getType())
                .withDittoHeaders(retrieveFeatureDesiredPropertiesTwinCommand.getDittoHeaders())
                .withId(retrieveFeatureDesiredPropertiesTwinCommand.getThingEntityId())
                .withManifest(retrieveFeatureDesiredPropertiesTwinCommand.getManifest())
                .withResourcePath(retrieveFeatureDesiredPropertiesTwinCommand.getResourcePath());
        assertThat(underTest.getFeatureId()).isEqualTo(retrieveFeatureDesiredPropertiesTwinCommand.getFeatureId());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final RetrieveFeatureDesiredPropertiesLiveCommand newRetrieveFeatureDesiredPropertiesLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newRetrieveFeatureDesiredPropertiesLiveCommand).withDittoHeaders(emptyDittoHeaders);
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
                .contains(retrieveFeatureDesiredPropertiesTwinCommand.toString());
    }

}
