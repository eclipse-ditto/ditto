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
package org.eclipse.ditto.connectivity.model.mqtt;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Representation of the MQTT 5 Receive Maximum.
 * The Server uses this value to limit the number of QoS 1 and QoS 2 publications that it is willing to process
 * concurrently for the Client.
 * It does not provide a mechanism to limit the QoS 0 publications that the Client might try to send.
 */
@Immutable
public final class ReceiveMaximum implements Comparable<ReceiveMaximum> {

    /**
     * The minimum value the Receive Maximum as defined by protocol specification.
     */
    static final int MIN_VALUE = 1;

    /**
     * The maximum value (65535) for the Receive Maximum as defined by protocol specification.
     */
    static final int MAX_VALUE = 0xFFFF;

    /**
     * The default value for the Receive Maximum.
     */
    public static final int DEFAULT_VALUE = MAX_VALUE;

    private final int value;

    private ReceiveMaximum(final int value) {
        this.value = value;
    }

    /**
     * Returns an instance of {@code ReceiveMaximum} for the specified integer value.
     *
     * @param value the value of the Receive Maximum. It must be between (inclusive) {@value #MIN_VALUE} and
     * {@value #DEFAULT_VALUE}.
     * @return the ReceiveMaximum for {@code value}.
     * @throws IllegalReceiveMaximumValueException if {@code value} is less than {@value #MIN_VALUE} or greater than
     * {@value #DEFAULT_VALUE}.
     */
    public static ReceiveMaximum of(final int value) throws IllegalReceiveMaximumValueException {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalReceiveMaximumValueException(
                    String.format("Expected value to be within [%d, %d] but it was <%d>.",
                            MIN_VALUE,
                            MAX_VALUE,
                            value),
                    null
            );
        } else {
            return new ReceiveMaximum(value);
        }
    }

    /**
     * Returns an instance of {@code ReceiveMaximum} with the default value of {@value #DEFAULT_VALUE}.
     * This is the default and value that is determined by MQTT 5 protocol specification.
     *
     * @return the default Receive Maximum.
     */
    public static ReceiveMaximum defaultReceiveMaximum() {
        return new ReceiveMaximum(DEFAULT_VALUE);
    }

    /**
     * Returns the value of this Receive Maximum.
     *
     * @return the value.
     */
    public int getValue() {
        return value;
    }

    @Override
    public int compareTo(final ReceiveMaximum o) {
        ConditionChecker.checkNotNull(o, "o");
        return Integer.compare(value, o.value);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ReceiveMaximum that = (ReceiveMaximum) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "value=" + value +
                "]";
    }

}
