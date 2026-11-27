/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.Test;

/**
 * Unit test for {@link InternCache}.
 */
public final class InternCacheTest {

    @Test
    public void returnsTheCachedInstanceForAnEqualValue() {
        final InternCache<String> underTest = new InternCache<>(16);
        final String first = new String("value");
        final String second = new String("value");

        assertThat(underTest.intern(first)).isSameAs(first);
        assertThat(underTest.intern(second)).isSameAs(first);
    }

    @Test
    public void aCollidingValueReplacesTheCachedOne() {
        final InternCache<Key> underTest = new InternCache<>(1);
        final Key a = new Key("a");
        final Key b = new Key("b");

        assertThat(underTest.intern(a)).isSameAs(a);
        assertThat(underTest.intern(b)).isSameAs(b);
        assertThat(underTest.intern(new Key("b"))).isSameAs(b);
        assertThat(underTest.intern(new Key("a"))).isNotSameAs(a).isEqualTo(a);
    }

    @Test
    public void rejectsSizesWhichAreNoPowerOfTwo() {
        assertThatIllegalArgumentException().isThrownBy(() -> new InternCache<>(100));
        assertThatIllegalArgumentException().isThrownBy(() -> new InternCache<>(0));
    }

    private static final class Key {

        private final String value;

        private Key(final String value) {
            this.value = value;
        }

        @Override
        public boolean equals(final Object o) {
            return o instanceof Key && ((Key) o).value.equals(value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

}
