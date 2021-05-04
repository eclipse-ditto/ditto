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
package org.eclipse.ditto.base.model.acks;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link org.eclipse.ditto.base.model.acks.AcknowledgementLabel}.
 */
@Immutable
final class ImmutableAcknowledgementLabel implements AcknowledgementLabel {

    private final String label;

    private ImmutableAcknowledgementLabel(final String label) {
        this.label = label;
    }

    /**
     * Returns a new AcknowledgementLabel based on the provided string.
     *
     * @param label the character sequence forming the label's value.
     * @return a new AcknowledgementLabel.
     * @throws NullPointerException if {@code label} is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    static ImmutableAcknowledgementLabel of(final CharSequence label) {
        return new ImmutableAcknowledgementLabel(label.toString());
    }

    @Override
    public int length() {
        return label.length();
    }

    @Override
    public char charAt(final int index) {
        return label.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return label.substring(start, end);
    }

    @SuppressWarnings("squid:S2097")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o) {
            return false;
        }
        final Class<? extends ImmutableAcknowledgementLabel> thisClass = getClass();
        final Class<?> otherClass = o.getClass();
        if (thisClass == otherClass) {
            final ImmutableAcknowledgementLabel that = (ImmutableAcknowledgementLabel) o;
            return Objects.equals(label, that.label);
        }
        final Class<?>[] otherInterfaces = otherClass.getInterfaces();
        for (final Class<?> thisInterface : thisClass.getInterfaces()) {
            if (!contains(otherInterfaces, thisInterface)) {
                return false;
            }
        }
        return Objects.equals(toString(), o.toString());
    }

    private static boolean contains(final Class<?>[] interfaceClasses, final Class<?> searchedInterfaceClass) {
        for (final Class<?> interfaceClass : interfaceClasses) {
            if (interfaceClass == searchedInterfaceClass) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

    @Override
    public String toString() {
        return label;
    }

}
