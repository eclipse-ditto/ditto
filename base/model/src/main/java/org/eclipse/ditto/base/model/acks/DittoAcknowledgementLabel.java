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

/**
 * Defines built-in {@link org.eclipse.ditto.base.model.acks.AcknowledgementLabel}s which are emitted by Ditto itself.
 *
 * @since 1.1.0
 */
/*
 * This is intentionally not an enum as the enum constants would have difficulties to comply to the
 * hashCode/equals contract when comparing with an ImmutableAcknowledgementLabel of the same value.
 */
public final class DittoAcknowledgementLabel implements AcknowledgementLabel {

    /**
     * Label for Acknowledgements indicating that a change to an entity (e. g. a thing) has successfully been persisted
     * to the twin.
     */
    public static final DittoAcknowledgementLabel TWIN_PERSISTED = new DittoAcknowledgementLabel("twin-persisted");

    /**
     * Label for Acknowledgements indicating that a response to a live message/command was received.
     *
     * @since 1.2.0
     */
    public static final DittoAcknowledgementLabel LIVE_RESPONSE = new DittoAcknowledgementLabel("live-response");

    /**
     * Label for Acknowledgements indicating that a change to an entity (e. g. a thing) has successfully been reflected
     * in the search index.
     *
     * @since 2.0.0
     */
    public static final DittoAcknowledgementLabel SEARCH_PERSISTED = new DittoAcknowledgementLabel("search-persisted");

    private final AcknowledgementLabel delegate;

    private DittoAcknowledgementLabel(final CharSequence labelValue) {
        delegate = AcknowledgementLabel.of(labelValue);
    }

    /**
     * Returns an array containing the Ditto acknowledgement labels, in the order they're declared.
     *
     * @return an array containing the Ditto acknowledgement labels, in the order they're declared.
     */
    public static AcknowledgementLabel[] values() {
        return new AcknowledgementLabel[]{TWIN_PERSISTED, LIVE_RESPONSE, SEARCH_PERSISTED};
    }

    /**
     * Indicates whether the given acknowledgement label is a Ditto acknowledgement label.
     *
     * @param acknowledgementLabel the acknowledgement label to be checked.
     * @return {@code true} if the given acknowledgement label is a constant of DittoAcknowledgementLabel.
     */
    public static boolean contains(@Nullable final AcknowledgementLabel acknowledgementLabel) {
        if (null != acknowledgementLabel) {
            for (final AcknowledgementLabel dittoAcknowledgementLabel : values()) {
                if (dittoAcknowledgementLabel.equals(acknowledgementLabel)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("squid:S2097")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == this) {
            return true;
        }
        if (null == o) {
            return false;
        }
        final Class<? extends DittoAcknowledgementLabel> thisClass = getClass();
        final Class<?> otherClass = o.getClass();
        if (thisClass == otherClass) {
            final DittoAcknowledgementLabel that = (DittoAcknowledgementLabel) o;
            return Objects.equals(delegate, that.delegate);
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
        return delegate.hashCode();
    }

    @Override
    public int length() {
        return delegate.length();
    }

    @Override
    public char charAt(final int index) {
        return delegate.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return delegate.subSequence(start, end);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}
