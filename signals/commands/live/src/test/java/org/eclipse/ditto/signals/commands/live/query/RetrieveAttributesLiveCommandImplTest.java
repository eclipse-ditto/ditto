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
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link RetrieveAttributesLiveCommandImpl}.
 */
public final class RetrieveAttributesLiveCommandImplTest {

    private RetrieveAttributes retrieveAttributesTwinCommand;
    private RetrieveAttributesLiveCommand underTest;

    /** */
    @Before
    public void setUp() {
        retrieveAttributesTwinCommand = RetrieveAttributes.of(TestConstants.Thing.THING_ID,
                TestConstants.JSON_FIELD_SELECTOR_ATTRIBUTES, DittoHeaders.empty());
        underTest = RetrieveAttributesLiveCommandImpl.of(retrieveAttributesTwinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAttributesLiveCommandImpl.class,
                areImmutable(),
                provided(JsonPointer.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveAttributesLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingQueryCommand")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetRetrieveAttributesLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveAttributesLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetRetrieveAttributesLiveCommandForCreateAttributeCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> RetrieveAttributesLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(
                        MessageFormat.format("cannot be cast to {0}", RetrieveAttributes.class.getName()))
                .withNoCause();
    }

    /** */
    @Test
    public void getRetrieveAttributesLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(retrieveAttributesTwinCommand.getType())
                .withDittoHeaders(retrieveAttributesTwinCommand.getDittoHeaders())
                .withId(retrieveAttributesTwinCommand.getThingId())
                .withManifest(retrieveAttributesTwinCommand.getManifest())
                .withResourcePath(retrieveAttributesTwinCommand.getResourcePath());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final RetrieveAttributesLiveCommand newRetrieveAttributesLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newRetrieveAttributesLiveCommand).withDittoHeaders(emptyDittoHeaders);
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
                .contains(retrieveAttributesTwinCommand.toString());
    }

}