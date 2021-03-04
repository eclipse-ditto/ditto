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
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit test for {@link ImmutableJsonParseOptionsBuilder}.
 */
public class ImmutableJsonParseOptionsBuilderTest {


    @Test
    public void buildWithBuilderAndCheckForApplyUrlDecoding() {
        final JsonParseOptions options = JsonFactory.newParseOptionsBuilder().withUrlDecoding().build();
        assertThat(options.isApplyUrlDecoding()).isTrue();

        final JsonParseOptions optionsFalse = JsonFactory.newParseOptionsBuilder().build();
        assertThat(optionsFalse.isApplyUrlDecoding()).isFalse();

        final JsonParseOptions optionsFalse2 = JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();
        assertThat(optionsFalse2.isApplyUrlDecoding()).isFalse();
    }
}
