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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link RetrieveAttributeLiveCommandImpl}.
 */
public final class RetrieveAttributeLiveCommandImplTest {

    private RetrieveAttribute retrieveAttributeTwinCommand;
    private RetrieveAttributeLiveCommand underTest;

    /** */
    @Before
    public void setUp() {
        retrieveAttributeTwinCommand = RetrieveAttribute.of(TestConstants.Thing.THING_ID,
                TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER, DittoHeaders.empty());
        underTest = RetrieveAttributeLiveCommandImpl.of(retrieveAttributeTwinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAttributeLiveCommandImpl.class,
                areImmutable(),
                provided(JsonPointer.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveAttributeLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingQueryCommand", "attributePointer")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetRetrieveAttributeLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveAttributeLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetRetrieveAttributeLiveCommandForCreateAttributeCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> RetrieveAttributeLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(MessageFormat.format("cannot be cast to {0}", RetrieveAttribute.class.getName()))
                .withNoCause();
    }

    /** */
    @Test
    public void getRetrieveAttributeLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(retrieveAttributeTwinCommand.getType())
                .withDittoHeaders(retrieveAttributeTwinCommand.getDittoHeaders())
                .withId(retrieveAttributeTwinCommand.getThingId())
                .withManifest(retrieveAttributeTwinCommand.getManifest())
                .withResourcePath(retrieveAttributeTwinCommand.getResourcePath());
        assertThat(underTest.getAttributePointer()).isEqualTo(retrieveAttributeTwinCommand.getAttributePointer());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final RetrieveAttributeLiveCommand newRetrieveAttributeLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newRetrieveAttributeLiveCommand).withDittoHeaders(emptyDittoHeaders);
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
                .contains(retrieveAttributeTwinCommand.toString());
    }

}