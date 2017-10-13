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
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link RetrieveFeatureLiveCommandImpl}.
 */
public final class RetrieveFeatureLiveCommandImplTest {

    private RetrieveFeature retrieveFeatureTwinCommand;
    private RetrieveFeatureLiveCommand underTest;

    /** */
    @Before
    public void setUp() {
        retrieveFeatureTwinCommand = RetrieveFeature.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.JSON_FIELD_SELECTOR_ATTRIBUTES,
                DittoHeaders.empty());
        underTest = RetrieveFeatureLiveCommandImpl.of(retrieveFeatureTwinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureLiveCommandImpl.class, areImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeatureLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingQueryCommand", "featureId")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetRetrieveFeatureLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveFeatureLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetRetrieveFeatureLiveCommandForCreateAttributeCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> RetrieveFeatureLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(MessageFormat.format("cannot be cast to {0}", RetrieveFeature.class.getName()))
                .withNoCause();
    }

    /** */
    @Test
    public void getRetrieveFeatureLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(retrieveFeatureTwinCommand.getType())
                .withDittoHeaders(retrieveFeatureTwinCommand.getDittoHeaders())
                .withId(retrieveFeatureTwinCommand.getThingId())
                .withManifest(retrieveFeatureTwinCommand.getManifest())
                .withResourcePath(retrieveFeatureTwinCommand.getResourcePath());
        assertThat(underTest.getFeatureId()).isEqualTo(retrieveFeatureTwinCommand.getFeatureId());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final RetrieveFeatureLiveCommand newRetrieveFeatureLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newRetrieveFeatureLiveCommand).withDittoHeaders(emptyDittoHeaders);
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
                .contains(retrieveFeatureTwinCommand.toString());
    }

}