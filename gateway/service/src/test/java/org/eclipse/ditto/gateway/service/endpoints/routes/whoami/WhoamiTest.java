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

package org.eclipse.ditto.gateway.service.endpoints.routes.whoami;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Whoami}.
 */
public final class WhoamiTest {

    @Test
    public void fromJsonToJson() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .correlationId("any")
                .build();
        final Whoami whoami = Whoami.of(headers);
        final JsonObject serialized = whoami.toJson();
        final Whoami deserialized = Whoami.fromJson(serialized, headers);
        assertThat(deserialized).isEqualTo(whoami);
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(Whoami.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(Whoami.class, areImmutable());
    }

}
