/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.api.commands.sudo;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SudoCountThings}.
 */
public final class SudoCountThingsTest {

    private static final String KNOWN_FILTER_STR = "eq(thingId,4711)";

    private static final String JSON_ALL_FIELDS_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, SudoCountThings.TYPE)
            .set(SudoCountThings.JSON_FILTER, KNOWN_FILTER_STR)
            .build().toString();

    private static final String JSON_MINIMAL_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, SudoCountThings.TYPE)
            .build().toString();

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SudoCountThings.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoCountThings.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonWithAllFieldsSetV2() {
        final SudoCountThings command = SudoCountThings.of(KNOWN_FILTER_STR, DittoHeaders.empty());

        final String json = command.toJsonString();
        assertThat(json).isEqualTo(JSON_ALL_FIELDS_V2);
    }

    @Test
    public void toJsonWithOnlyRequiredFieldsSetV2() {
        final SudoCountThings command = SudoCountThings.of(DittoHeaders.empty());

        final String json = command.toJsonString();

        assertThat(json).isEqualTo(JSON_MINIMAL_V2);
    }

    @Test
    public void fromJsonWithAllFieldsSetV2() {
        assertAllFieldsSet(SudoCountThings.fromJson(JSON_ALL_FIELDS_V2, DittoHeaders.empty()));
    }

    private static void assertAllFieldsSet(final SudoCountThings command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).contains(KNOWN_FILTER_STR);
    }

    @Test
    public void fromJsonWithOnlyRequiredFieldsSetV2() {
        assertMinimal(SudoCountThings.fromJson(JSON_MINIMAL_V2, DittoHeaders.empty()));
    }

    private static void assertMinimal(final SudoCountThings command) {
        assertThat(command).isNotNull();
        assertThat(command.getFilter()).isEmpty();
    }

}
