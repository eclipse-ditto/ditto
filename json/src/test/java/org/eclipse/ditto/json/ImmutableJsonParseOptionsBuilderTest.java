/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
