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
package org.eclipse.ditto.base.model.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.base.model.common.Placeholders}.
 */
public final class PlaceholdersTest {

    private static final String REPLACER_KEY_1 = "my:arbitrary:replacer1";
    private static final String REPLACER_1 = "{{ " + REPLACER_KEY_1 + " }}";
    private static final String LEGACY_REPLACER_KEY = "request.subjectId";
    private static final String LEGACY_REPLACER = "${" + LEGACY_REPLACER_KEY + "}";

    @Test
    public void containsReturnsTrueWhenInputContainsPlaceholder() {
        final String input = "a" + REPLACER_1 + "z";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isTrue();
    }

    @Test
    public void containsReturnsTrueWhenInputContainsLegacyPlaceholder() {
        final String input = "a" + LEGACY_REPLACER + "z";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isTrue();
    }

    @Test
    public void containsReturnsTrueWhenInputContainsBothNewAndLegacyPlaceholders() {
        final String input = "a" + REPLACER_1 + "b" + LEGACY_REPLACER + "c";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isTrue();
    }

    @Test
    public void containsReturnsFalseWhenInputContainsPlaceholderStartOnly() {
        final String input = "a{{z";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isFalse();
    }

    @Test
    public void containsReturnsFalseWhenInputContainsPlaceholderEndOnly() {
        final String input = "a}}z";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isFalse();
    }

    @Test
    public void containsReturnsFailsWhenInputDoesNotContainAnyPlaceholder() {
        final String input = "abc";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isFalse();
    }
}
