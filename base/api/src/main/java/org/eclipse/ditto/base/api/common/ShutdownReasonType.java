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
package org.eclipse.ditto.base.api.common;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * <p>
 * This interface represents a reason for the issuing of a {@code Shutdown} command.
 * </p>
 * <p>
 * <em>Note: Implementations of this interface are required to be immutable.</em>
 * </p>
 */
@Immutable
public interface ShutdownReasonType extends CharSequence {

    /**
     * Default implementation of a not pre-defined ShutdownReasonType.
     */
    @Immutable
    final class Unknown implements ShutdownReasonType {

        private final String typeName;

        private Unknown(final CharSequence theTypeName) {
            typeName = theTypeName.toString();
        }

        /**
         * Returns an instance of {@code Unknown}.
         *
         * @param typeName the name of the unknown shutdown reason.
         * @return the instance.
         * @throws NullPointerException if {@code typeName} is {@code null}.
         * @throws IllegalArgumentException if {@code typeName} is .
         */
        public static ShutdownReasonType of(final CharSequence typeName) {
            return new Unknown(typeName);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Unknown unknown = (Unknown) o;
            return Objects.equals(typeName, unknown.typeName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeName);
        }

        @Override
        public int length() {
            return typeName.length();
        }

        @Override
        public char charAt(final int index) {
            return typeName.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return typeName.subSequence(start, end);
        }

        /**
         * Returns the name of this type.
         *
         * @return the name of this type.
         */
        @Override
        public String toString() {
            return typeName;
        }

    }

    /**
     * An enumeration of the known types of {@code ShutdownReason}s.
     */
    enum Known implements ShutdownReasonType {

        /**
         * A namespace is going to be purged.
         */
        PURGE_NAMESPACE("purge-namespace"),

        /**
         * Entities are going to be purged.
         */
        PURGE_ENTITIES("purge-entities");

        private final String typeName;

        Known(final String theTypeName) {
            typeName = theTypeName;
        }

        /**
         * Returns the {@code ShutdownReasonType} with the given type name.
         *
         * @param requestedTypeName the name of the type to be retrieved.
         * @return an Optional containing the {@code ShutdownReasonType} constant with the requested type name or an empty
         * Optional.
         * @throws NullPointerException if {@code requestedTypeName} is {@code null}.
         */
        public static Optional<ShutdownReasonType> forTypeName(final CharSequence requestedTypeName) {
            final String requestedTypeNameString = requestedTypeName.toString();
            for (final Known type : values()) {
                if (requestedTypeNameString.equals(type.typeName)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }

        @Override
        public int length() {
            return typeName.length();
        }

        @Override
        public char charAt(final int index) {
            return typeName.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return typeName.subSequence(start, end);
        }

        /**
         * Returns the name of this type.
         *
         * @return the name of this type.
         */
        @Override
        public String toString() {
            return typeName;
        }

    }

}
