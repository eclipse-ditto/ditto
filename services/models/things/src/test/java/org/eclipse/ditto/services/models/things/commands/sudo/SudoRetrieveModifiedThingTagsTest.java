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
package org.eclipse.ditto.services.models.things.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrieveModifiedThingTags}.
 */
public final class SudoRetrieveModifiedThingTagsTest {

    private static final Duration KNOWN_TIMESPAN = Duration.ofMinutes(5);
    private static final Duration KNOWN_OFFSET = Duration.ofMinutes(1);

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SudoRetrieveModifiedThingTags.TYPE)
            .set(SudoRetrieveModifiedThingTags.JSON_TIMESPAN, KNOWN_TIMESPAN.toString())
            .set(SudoRetrieveModifiedThingTags.JSON_OFFSET, KNOWN_OFFSET.toString())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveModifiedThingTags.class,
                areImmutable(),
                provided(AuthorizationContext.class, JsonFieldSelector.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveModifiedThingTags.class)
                .withRedefinedSuperclass()
                .verify();
    }

    /** */
    @Test
    public void toJsonReturnsExpected() {
        final SudoRetrieveModifiedThingTags underTest =
                SudoRetrieveModifiedThingTags.of(KNOWN_TIMESPAN, KNOWN_OFFSET, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    /** */
    @Test
    public void createInstanceFromValidJson() {
        final SudoRetrieveModifiedThingTags underTest =
                SudoRetrieveModifiedThingTags.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getTimespan()).isEqualTo(KNOWN_TIMESPAN);
    }

    /** */
    @Test
    public void checkSudoCommandTypeWorks() {
        final SudoRetrieveModifiedThingTags sudoRetrieveModifiedThingTags =
                SudoRetrieveModifiedThingTags.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final SudoCommand sudoCommand =
                SudoCommandRegistry.newInstance().parse(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveModifiedThingTags).isEqualTo(sudoCommand);
    }

}
