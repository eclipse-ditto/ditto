/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.correlationid;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An ID for tracing test requests throughout the back-end.
 * The most common forming consists of the qualified name of the test method that made the requests.
 * By appending suffixes the base correlation ID could be made more specific.
 */
@Immutable
public final class CorrelationId implements CharSequence, Comparable<CorrelationId> {

    private final String value;

    private CorrelationId(final String value) {
        this.value = value;
    }

    /**
     * Returns a CorrelationId consisting of the qualified name of the test method as well as optional
     * suffixes.
     *
     * @param testClass provides the qualified name of the test method's class.
     * @param testMethod provides the name of the test method.
     * @return the correlation ID.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CorrelationId of(final Class<?> testClass, final Method testMethod) {
        checkNotNull(testClass, "testClass");
        checkNotNull(testMethod, "testMethod");
        return new CorrelationId(testClass.getName() + "." + testMethod.getName());
    }

    /**
     * Returns a CorrelationId consisting of the specified value.
     *
     * @param value the value of the returned correlation ID.
     * @return the correlation ID.
     * @throws NullPointerException if {@code value} is {@code null}.
     * @throws IllegalArgumentException if {@code value} is blank.
     */
    public static CorrelationId of(final CharSequence value) {
        if (value instanceof CorrelationId) {
            return (CorrelationId) value;
        } else {
            checkNotNull(value, "value");
            if (0 == value.length()) {
                throw new IllegalArgumentException("The value of a correlation ID must not be blank!");
            }
            return new CorrelationId(value.toString());
        }
    }

    /**
     * Returns a CorrelationId consisting of random UUID.
     *
     * @return the correlation ID.
     */
    public static CorrelationId random() {
        return new CorrelationId(String.valueOf(UUID.randomUUID()));
    }

    public CorrelationId withSuffix(final CharSequence suffix, final CharSequence... moreSuffixes) {
        checkNotNull(suffix, "suffix");
        checkNotNull(moreSuffixes, "moreSuffixes");
        return new CorrelationId(value + suffix + String.join("", moreSuffixes));
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public char charAt(final int i) {
        return value.charAt(i);
    }

    @Override
    public CharSequence subSequence(final int i, final int i1) {
        return value.subSequence(i, i1);
    }

    @Override
    public int compareTo(final CorrelationId correlationId) {
        return value.compareTo(correlationId.toString());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CorrelationId that = (CorrelationId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * Returns the plain value of this CorrelationId.
     *
     * @return the value.
     */
    @Override
    public String toString() {
        return value;
    }

}
