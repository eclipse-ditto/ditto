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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.connectivity.model.HonoAddressAlias.COMMAND_RESPONSE;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

/**
 * Unit test for {@link HonoAddressAlias}.
 */
public final class HonoAddressAliasTest {

    @Test
    public void aliasValuesReturnsExpected() {
        final HonoAddressAlias[] honoAddressAliases = HonoAddressAlias.values();
        final Collection<String> expectedAliasValues = new LinkedHashSet<>(honoAddressAliases.length);
        for (final HonoAddressAlias honoAddressAlias : honoAddressAliases) {
            expectedAliasValues.add(honoAddressAlias.getAliasValue());
        }
        assertThat(HonoAddressAlias.aliasValues()).hasSameElementsAs(expectedAliasValues);
    }

    @Test
    public void forAliasValueWithNullAliasValueReturnsEmptyOptional() {
        assertThat(HonoAddressAlias.forAliasValue(null)).isEmpty();
    }

    @Test
    public void forAliasValueWithUnknownAliasValueReturnsEmptyOptional() {
        assertThat(HonoAddressAlias.forAliasValue(String.valueOf(UUID.randomUUID()))).isEmpty();
    }

    @Test
    public void forAliasValueIsTolerantForSmallDiscrepancyInSpecifiedAliasValue() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(HonoAddressAlias.forAliasValue("    TeLeMeTrY  ")).hasValue(HonoAddressAlias.TELEMETRY);
            softly.assertThat(HonoAddressAlias.forAliasValue(COMMAND_RESPONSE.name()))
                    .as(COMMAND_RESPONSE.name())
                    .hasValue(COMMAND_RESPONSE);
        }
    }

    @Test
    public void forAliasValueReturnsExpectedForKnownAliasValue() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            for (final HonoAddressAlias honoAddressAlias : HonoAddressAlias.values()) {
                softly.assertThat(HonoAddressAlias.forAliasValue(honoAddressAlias.getAliasValue()))
                        .as(honoAddressAlias.getAliasValue())
                        .hasValue(honoAddressAlias);
            }
        }
    }

    @Test
    public void resolveAddressWithNullTenantIdThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> HonoAddressAlias.EVENT.resolveAddress(null))
                .withMessage("The tenantId must not be null!")
                .withNoCause();
    }

    @Test
    public void resolveAddressWithNonEmptyTenantIdReturnsExpected() {
        final HonoAddressAlias honoAddressAlias = HonoAddressAlias.EVENT;
        final String tenantId = "myTenant";

        final String resolvedAddress = honoAddressAlias.resolveAddress(tenantId);

        assertThat(resolvedAddress).isEqualTo("hono." + honoAddressAlias.getAliasValue() + "." + tenantId);
    }

    @Test
    public void resolveAddressWithEmptyTenantIdReturnsExpected() {
        final HonoAddressAlias honoAddressAlias = HonoAddressAlias.EVENT;

        final String resolvedAddress = honoAddressAlias.resolveAddress("");

        assertThat(resolvedAddress).isEqualTo("hono." + honoAddressAlias.getAliasValue());
    }

    @Test
    public void resolveAddressWithThingIdSuffixReturnsExpected() {
        final HonoAddressAlias honoAddressAlias = HonoAddressAlias.EVENT;
        final String tenantId = "myTenant";

        final String resolvedAddress = honoAddressAlias.resolveAddressWithThingIdSuffix(tenantId);

        assertThat(resolvedAddress)
                .isEqualTo("hono." + honoAddressAlias.getAliasValue() + "." + tenantId + "/{{thing:id}}");
    }

}