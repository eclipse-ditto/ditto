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
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link RetrieveThingLiveCommandImpl}.
 */
public final class RetrieveThingLiveCommandImplTest {

    private RetrieveThing retrieveThingTwinCommand;
    private RetrieveThingLiveCommand underTest;

    /** */
    @Before
    public void setUp() {
        retrieveThingTwinCommand = RetrieveThing.getBuilder(TestConstants.Thing.THING_ID, DittoHeaders.empty())
                .withSelectedFields(TestConstants.JSON_FIELD_SELECTOR_ATTRIBUTES)
                .build();
        underTest = RetrieveThingLiveCommandImpl.of(retrieveThingTwinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveThingLiveCommandImpl.class, areImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveThingLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingQueryCommand")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetRetrieveThingLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveThingLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetRetrieveThingLiveCommandForCreateAttributeCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> RetrieveThingLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(MessageFormat.format("cannot be cast to {0}", RetrieveThing.class.getName()))
                .withNoCause();
    }

    /** */
    @Test
    public void getRetrieveThingLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(retrieveThingTwinCommand.getType())
                .withDittoHeaders(retrieveThingTwinCommand.getDittoHeaders())
                .withId(retrieveThingTwinCommand.getThingId())
                .withManifest(retrieveThingTwinCommand.getManifest())
                .withResourcePath(retrieveThingTwinCommand.getResourcePath());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final RetrieveThingLiveCommand newRetrieveThingLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newRetrieveThingLiveCommand).withDittoHeaders(emptyDittoHeaders);
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
                .contains(retrieveThingTwinCommand.toString());
    }

}