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
package org.eclipse.ditto.base.model.auth;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Abstract class for type information of {@code AuthorizationContext} instances used for defining what "kind" of
 * authorization an authorization context represents.
 *
 * @since 1.1.0
 */
@Immutable
public abstract class AuthorizationContextType implements CharSequence, Comparable<AuthorizationContextType> {

    private final String type;

    /**
     * Abstract constructor for creating the type based on the passed {@code type} String.
     *
     * @param type the type String to back this instance.
     * @throws NullPointerException if {@code type} is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty.
     */
    protected AuthorizationContextType(final String type) {
        this.type = argumentNotEmpty(type, "type");
    }

    @Override
    public int length() {
        return type.length();
    }

    @Override
    public char charAt(final int index) {
        return type.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return type.subSequence(start, end);
    }

    @Override
    public int compareTo(final AuthorizationContextType authorizationContextType) {
        return toString().compareTo(authorizationContextType.toString());
    }

    @SuppressWarnings("squid:S2097")
    @Override
    public final boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o) {
            return false;
        }
        final Class<? extends AuthorizationContextType> thisClass = getClass();
        final Class<?> otherClass = o.getClass();
        if (thisClass == otherClass) {
            final AuthorizationContextType that = (AuthorizationContextType) o;
            return Objects.equals(type, that.type);
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
    public final int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public final String toString() {
        return type;
    }

}
