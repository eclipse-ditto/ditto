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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

public final class PublicKeyProviderUnavailableExceptionTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(PublicKeyProviderUnavailableException.class, areImmutable());
    }

    @Test
    public void toJsonFromJsonResultsInEqualObject() {
        final PublicKeyProviderUnavailableException originalException =
                PublicKeyProviderUnavailableException.newBuilder()
                        .message("Test!")
                        .description("Test2!")
                        .cause(new IllegalStateException("Hello!"))
                        .build();

        final JsonObject jsonException = originalException.toJson();
        final PublicKeyProviderUnavailableException deserializedException =
                PublicKeyProviderUnavailableException.fromJson(jsonException, DittoHeaders.empty());

        assertThat(deserializedException).isEqualTo(originalException);
    }

}
