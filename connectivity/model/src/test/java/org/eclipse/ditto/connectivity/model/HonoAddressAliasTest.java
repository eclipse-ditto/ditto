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

import java.util.Locale;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

/**
 * Unit test for {@link HonoAddressAlias}.
 */
public final class HonoAddressAliasTest {

    @Test
    public void getAliasValueReturnsExpected() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            for (final HonoAddressAlias honoAddressAlias : HonoAddressAlias.values()) {
                softly.assertThat(honoAddressAlias.getAliasValue())
                        .as(honoAddressAlias.name())
                        .isEqualTo(honoAddressAlias.name().toLowerCase(Locale.ENGLISH));
            }
        }
    }

}