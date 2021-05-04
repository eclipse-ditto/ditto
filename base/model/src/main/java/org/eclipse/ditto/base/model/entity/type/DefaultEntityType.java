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
package org.eclipse.ditto.base.model.entity.type;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Default implementation of {@link org.eclipse.ditto.base.model.entity.type.EntityType}.
 *
 * @since 1.1.0
 */
@Immutable
final class DefaultEntityType implements EntityType {

    private final String value;

    private DefaultEntityType(final CharSequence value) {
        this.value = value.toString();
    }

    /**
     * Returns an instance of this class.
     *
     * @param value the value of the entity type.
     * @return the instance.
     * @throws NullPointerException if {@code value} is {@code null}.
     * @throws IllegalArgumentException if {@code value} is empty.
     */
    public static DefaultEntityType of(final CharSequence value) {
        return new DefaultEntityType(argumentNotEmpty(value, "value"));
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultEntityType that = (DefaultEntityType) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public int compareTo(final EntityType o) {
        checkNotNull(o, "o");
        return value.compareTo(o.toString());
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public char charAt(final int index) {
        return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return value.subSequence(start, end);
    }

    @Override
    public String toString() {
        return value;
    }

}
