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
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link RetrieveThingsLiveCommandImpl}.
 */
public final class RetrieveThingsLiveCommandImplTest {

    private static List<String> thingIds;

    private RetrieveThings retrieveThingTwinCommand;
    private RetrieveThingsLiveCommand underTest;

    /** */
    @BeforeClass
    public static void initThingIds() {
        thingIds = new ArrayList<>();
        thingIds.add(":foo");
        thingIds.add(":bar");
        thingIds.add(":baz");
    }

    /** */
    @Before
    public void setUp() {
        retrieveThingTwinCommand = RetrieveThings.getBuilder(thingIds)
                .selectedFields(TestConstants.JSON_FIELD_SELECTOR_ATTRIBUTES)
                .dittoHeaders(DittoHeaders.empty())
                .build();
        underTest = RetrieveThingsLiveCommandImpl.of(retrieveThingTwinCommand);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveThingsLiveCommandImpl.class,
                areImmutable(),
                assumingFields("thingIds").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveThingsLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingQueryCommand", "thingIds", "namespace")
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetRetrieveThingsLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveThingsLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetRetrieveThingsLiveCommandForCreateAttributeCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> RetrieveThingsLiveCommandImpl.of(commandMock))
                .withMessageEndingWith(MessageFormat.format("cannot be cast to {0}", RetrieveThings.class.getName()))
                .withNoCause();
    }

    /** */
    @Test
    public void getRetrieveThingsLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(retrieveThingTwinCommand.getType())
                .withDittoHeaders(retrieveThingTwinCommand.getDittoHeaders())
                .withId(retrieveThingTwinCommand.getThingId())
                .withManifest(retrieveThingTwinCommand.getManifest())
                .withResourcePath(retrieveThingTwinCommand.getResourcePath());
        assertThat(underTest.getThingIds()).isEqualTo(retrieveThingTwinCommand.getThingIds());
    }

    /** */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final RetrieveThingsLiveCommand newRetrieveThingsLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newRetrieveThingsLiveCommand).withDittoHeaders(emptyDittoHeaders);
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
                .contains(retrieveThingTwinCommand.toString())
                .contains("namespace=null");
    }

}