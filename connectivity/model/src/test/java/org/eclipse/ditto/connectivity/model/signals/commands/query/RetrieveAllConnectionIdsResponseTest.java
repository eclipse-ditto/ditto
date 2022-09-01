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
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveAllConnectionIdsResponse}.
 */
public final class RetrieveAllConnectionIdsResponseTest {

    private static final JsonObject KNOWN_JSON;
    private static final Set<String> IDS = new HashSet<>(Arrays.asList("id1", "id2", "id3"));

    static {
        KNOWN_JSON = JsonObject.newBuilder()
                .set(Command.JsonFields.TYPE, RetrieveAllConnectionIdsResponse.TYPE)
                .set(CommandResponse.JsonFields.STATUS, 200)
                .set(RetrieveAllConnectionIdsResponse.CONNECTION_IDS, JsonArray.of(IDS))
                .build();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveAllConnectionIdsResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAllConnectionIdsResponse.class,
                areImmutable());
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveAllConnectionIdsResponse expected =
                RetrieveAllConnectionIdsResponse.of(IDS, DittoHeaders.empty());

        final RetrieveAllConnectionIdsResponse actual =
                RetrieveAllConnectionIdsResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = RetrieveAllConnectionIdsResponse.of(IDS, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

}
