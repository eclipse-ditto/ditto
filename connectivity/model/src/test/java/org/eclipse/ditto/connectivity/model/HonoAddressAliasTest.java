/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link HonoAddressAlias}.
 */
public final class HonoAddressAliasTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void forAliasValueWithNullAliasValueReturnsEmptyOptional() {
        assertThat(HonoAddressAlias.forAliasValue(null)).isEmpty();
    }

    @Test
    public void forAliasValueWithUnknownAliasValueReturnsEmptyOptional() {
        assertThat(HonoAddressAlias.forAliasValue(String.valueOf(UUID.randomUUID()))).isEmpty();
    }

    @Test
    public void getAliasValueReturnsExpected() {
        for (final HonoAddressAlias honoAddressAlias : HonoAddressAlias.values()) {
            softly.assertThat(honoAddressAlias.getAliasValue())
                    .as(honoAddressAlias.name())
                    .isEqualTo(honoAddressAlias.name().toLowerCase(Locale.ENGLISH));
        }
    }

    @Test
    public void forAliasValueReturnsExpectedForKnownAliasValue() {
        for (final HonoAddressAlias honoAddressAlias : HonoAddressAlias.values()) {
            softly.assertThat(HonoAddressAlias.forAliasValue(honoAddressAlias.getAliasValue()))
                    .as(honoAddressAlias.getAliasValue())
                    .hasValue(honoAddressAlias);
        }
    }

    @Test
    public void forInvalidAliasValueReturnsEmptyOptional() {
        Stream.concat(
                Stream.of(HonoAddressAlias.values()).map(HonoAddressAlias::name),
                Stream.of("Telemetry", " command")
        ).forEach(invalidAliasValue -> {
            softly.assertThat(HonoAddressAlias.forAliasValue(invalidAliasValue))
                    .as(invalidAliasValue)
                    .isEmpty();;
        });
    }

}