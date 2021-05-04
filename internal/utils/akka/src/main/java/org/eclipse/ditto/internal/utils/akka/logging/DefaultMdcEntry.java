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
package org.eclipse.ditto.internal.utils.akka.logging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Default implementation of {@link MdcEntry}.
 *
 * @since 1.4.0
 */
@Immutable
final class DefaultMdcEntry implements MdcEntry {

    private final String key;
    @Nullable private final String value;

    private DefaultMdcEntry(final String key, @Nullable final String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Returns an instance of DefaultMdcEntry.
     *
     * @param key the key which identifies the diagnostic value.
     * @param value the diagnostic value which is identified by {@code key}.
     * @return the instance.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    public static DefaultMdcEntry of(final CharSequence key, @Nullable final CharSequence value) {
        return new DefaultMdcEntry(argumentNotEmpty(key, "key").toString(), null != value ? value.toString() : null);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Nullable
    @Override
    public String getValueOrNull() {
        return value;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMdcEntry that = (DefaultMdcEntry) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "key=" + key +
                ", value=" + value +
                "]";
    }

}
