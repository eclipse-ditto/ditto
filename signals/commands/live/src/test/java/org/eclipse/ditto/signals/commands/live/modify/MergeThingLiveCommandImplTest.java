/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.live.modify;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MergeThingLiveCommandImpl}.
 */
public final class MergeThingLiveCommandImplTest {

    private MergeThing twinCommand;
    private MergeThingLiveCommand underTest;

    /**
     *
     */
    @Before
    public void setUp() {
        twinCommand = MergeThing.of(TestConstants.Thing.THING_ID, TestConstants.PATH, TestConstants.VALUE,
                DittoHeaders.empty());
        underTest = MergeThingLiveCommandImpl.of(twinCommand);
    }

    /**
     *
     */
    @Test
    public void assertImmutability() {
        assertInstancesOf(MergeThingLiveCommandImpl.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class).areAlsoImmutable());
    }

    /**
     *
     */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MergeThingLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingModifyCommand", "path", "value")
                .verify();
    }

    /**
     *
     */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetMergeThingLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> MergeThingLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /**
     *
     */
    @Test
    public void getMergeThingLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(twinCommand.getType())
                .withDittoHeaders(twinCommand.getDittoHeaders())
                .withId(twinCommand.getThingEntityId())
                .withManifest(twinCommand.getManifest())
                .withResourcePath(twinCommand.getResourcePath());
        assertThat(underTest.getPath()).isEqualTo(TestConstants.PATH);
        assertThat(underTest.getValue()).isEqualTo(TestConstants.VALUE);
    }

    /**
     *
     */
    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final MergeThingLiveCommand newMergeThingLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newMergeThingLiveCommand).withDittoHeaders(emptyDittoHeaders);
    }

    /**
     *
     */
    @Test
    public void answerReturnsNotNull() {
        assertThat(underTest.answer()).isNotNull();
    }

    /**
     *
     */
    @Test
    public void toStringReturnsExpected() {
        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains("command=")
                .contains(twinCommand.toString());
    }

}
