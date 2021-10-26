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
package org.eclipse.ditto.internal.models.signal.correlation;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Represents the result of validating whether two particular signals are correlated to each other.
 *
 * @since 2.2.0
 */
@Immutable
@SuppressWarnings("java:S1610")
public abstract class MatchingValidationResult {

    private MatchingValidationResult() {
        super();
    }

    /**
     * Returns an instance of a successful {@code MatchingValidationResult}.
     *
     * @return the instance.
     */
    public static MatchingValidationResult success() {
        return new Success();
    }

    /**
     * Returns an instance of a failed {@code MatchingValidationResult}.
     *
     * @param detailMessage the detail message of the failure.
     * @return the instance.
     * @throws NullPointerException if {@code detailMessage} is {@code null}.
     * @throws IllegalArgumentException if {@code detailMessage} is empty.
     */
    public static MatchingValidationResult failure(final CharSequence detailMessage) {
        ConditionChecker.argumentNotEmpty(detailMessage, "detailMessage");
        return new Failure(detailMessage.toString());
    }

    /**
     * Indicates whether this {@code MatchingValidationResult} represents a successful validation.
     *
     * @return {@code true} if this {@code MatchingValidationResult} is a success, {@code false} else.
     */
    public abstract boolean isSuccess();

    /**
     * Returns the detail message if this {@code MatchingValidationResult} is a failure.
     *
     * @return the detail message.
     * @throws IllegalStateException if this {@code MatchingValidationResult} is a success.
     * @see #isSuccess()
     */
    public abstract String getDetailMessageOrThrow() throws IllegalStateException;

    @Immutable
    private static final class Success extends MatchingValidationResult {

        private Success() {
            super();
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public String getDetailMessageOrThrow() {
            throw new IllegalStateException("Validation was successful, hence there is no detail message.");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " []";
        }

    }

    @Immutable
    private static final class Failure extends MatchingValidationResult {

        private final String detailMessage;

        private Failure(final String detailMessage) {
            this.detailMessage = detailMessage;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public String getDetailMessageOrThrow() {
            return detailMessage;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final var failure = (Failure) o;
            return Objects.equals(detailMessage, failure.detailMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(detailMessage);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "detailMessage=" + detailMessage +
                    "]";
        }

    }

}
