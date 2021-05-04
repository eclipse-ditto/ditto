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
package org.eclipse.ditto.connectivity.api.placeholders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.junit.Test;

public final class ImmutableConnectionIdPlaceholderTest {

    private final ImmutableConnectionIdPlaceholder underTest = ImmutableConnectionIdPlaceholder.INSTANCE;
    private final List<String> SUPPORTED_PLACEHOLDERS = Collections.singletonList("id");

    @Test
    public void getPrefix() {
        assertThat(underTest.getPrefix()).isEqualTo("connection");
    }

    @Test
    public void getSupportedNames() {
        assertThat(underTest.getSupportedNames()).isEqualTo(SUPPORTED_PLACEHOLDERS);
    }

    @Test
    public void supports() {
        for (final String supportedPlaceholder : SUPPORTED_PLACEHOLDERS) {
            assertThat(underTest.supports(supportedPlaceholder)).isTrue();
        }
    }

    @Test
    public void resolve() {
        final String expectedResolvingResult = "myTestId";
        final Optional<String> result = underTest.resolve(ConnectionId.of(expectedResolvingResult), "id");
        assertThat(result).contains(expectedResolvingResult);
    }

    @Test
    public void resolveWithUnsupportedPlaceholder() {
        final Optional<String> result = underTest.resolve(ConnectionId.of("myTestId"), "unsupported");
        assertThat(result).isEmpty();
    }

}
