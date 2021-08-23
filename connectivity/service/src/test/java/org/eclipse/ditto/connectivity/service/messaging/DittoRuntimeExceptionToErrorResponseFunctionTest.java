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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.junit.Test;

/**
 * Tests {@link DittoRuntimeExceptionToErrorResponseFunction}.
 */
public final class DittoRuntimeExceptionToErrorResponseFunctionTest {

    @Test
    public void transformsInvalidThingId() {
        final var exception = ThingIdInvalidException.newBuilder("invalid")
                .dittoHeaders(DittoHeaders.newBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), "invalid")
                        .build())
                .build();

        final var underTest = DittoRuntimeExceptionToErrorResponseFunction.of(1024);

        assertThat(underTest.apply(exception, null))
                .isInstanceOf(ThingErrorResponse.class)
                .extracting(response -> ((ThingErrorResponse) response).getEntityId())
                .isEqualTo(ThingId.of("unknown:unknown"));
    }

    @Test
    public void transformsInvalidPolicyId() {
        final var exception = PolicyIdInvalidException.newBuilder("invalid")
                .dittoHeaders(DittoHeaders.newBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), "invalid")
                        .build())
                .build();

        final var underTest = DittoRuntimeExceptionToErrorResponseFunction.of(1024);

        assertThat(underTest.apply(exception, null))
                .isInstanceOf(PolicyErrorResponse.class)
                .extracting(response -> ((PolicyErrorResponse) response).getEntityId())
                .isEqualTo(PolicyId.of("unknown:unknown"));
    }
}
