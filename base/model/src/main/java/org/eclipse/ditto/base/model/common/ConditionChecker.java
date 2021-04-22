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
package org.eclipse.ditto.base.model.common;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Utility class for common pre- or post condition checks.
 */
@Immutable
public final class ConditionChecker {

    private ConditionChecker() {
        throw new AssertionError();
    }

    /**
     * Checks that the specified object reference is not {@code null} and throws a {@code NullPointerException} if it
     * is.
     *
     * @param argument the object to be checked.
     * @param <T> the type of the checked object.
     * @return {@code argument} if not {@code null}.
     * @throws NullPointerException if {@code argument} is {@code null}.
     */
    public static <T> T checkNotNull(@Nullable final T argument) {
        return requireNonNull(argument, () -> null);
    }

    /**
     * Checks that the specified object reference is not {@code null} and throws a customized
     * {@code NullPointerException} if it is.
     *
     * @param argument the object to be checked.
     * @param argumentName the name or description of the checked object. This is used to build the message of the
     * potential NullPointerException.
     * @param <T> the type of the checked object.
     * @return {@code argument} if not {@code null}.
     * @throws NullPointerException if {@code argument} is {@code null}.
     */
    public static <T> T checkNotNull(@Nullable final T argument, final String argumentName) {
        return requireNonNull(argument, () -> MessageFormat.format("The {0} must not be null!", argumentName));
    }

    /**
     * Checks that the specified {@code CharSequence} is not empty and throws a customized
     * {@code IllegalArgumentException} if it is.
     *
     * @param argument the char sequence to be checked.
     * @param argumentName the name or description of the checked char sequence. This is used to build the message of
     * the potential IllegalArgumentException.
     * @param <T> the type of the checked char sequence.
     * @return {@code argument} if not {@code null} and not empty.
     * @throws IllegalArgumentException if {@code argument} is empty.
     * @throws NullPointerException if {@code argument} is {@code null}.
     */
    public static <T extends CharSequence> T checkNotEmpty(final T argument, final String argumentName) {
        if (0 == argument.length()) {
            throw new IllegalArgumentException(MessageFormat.format("The {0} must not be empty!", argumentName));
        }
        return argument;
    }

    /**
     * Checks that the specified {@code Collection} is not empty and throws a customized {@code
     * IllegalArgumentException} if it is.
     *
     * @param argument the collection to be checked.
     * @param argumentName the name or description of the checked collection. This is used to build the message of the
     * potential IllegalArgumentException.
     * @param <T> the type of the checked collection.
     * @return {@code argument} if not {@code null}.
     * @throws IllegalArgumentException if {@code argument} is empty.
     */
    public static <T extends Collection> T checkNotEmpty(final T argument, final String argumentName) {
        if (argument.isEmpty()) {
            throw new IllegalArgumentException(MessageFormat.format("The {0} must not be empty!", argumentName));
        }
        return argument;
    }

    /**
     * Tests the specified Predicate with the specified argument. If the predicate evaluates to {@code false} an
     * {@link IllegalArgumentException} is thrown. For example, the check if a string is not empty looks like:
     * <pre>
     * String theString = "";
     * ConditionChecker.checkArgument(theString, s -&gt; !s.isEmpty());
     * </pre>
     * This example would lead to an IllegalArgumentException.
     *
     * @param argument the argument to be checked.
     * @param argumentPredicate the predicate to test {@code argument} with.
     * @param <T> the type of the argument object.
     * @return the provided argument if {@code argumentPredicate} evaluates to {@code true}.
     * @throws IllegalArgumentException if the application {@code argumentPredicate} on {@code argument} evaluates to
     * {@code false}.
     * @see java.util.function.Predicate
     */
    @Nullable
    public static <T> T checkArgument(@Nullable final T argument, final Predicate<T> argumentPredicate) {
        if (!argumentPredicate.test(argument)) {
            throw new IllegalArgumentException("The argument is invalid!");
        }
        return argument;
    }

    /**
     * Tests the specified Predicate with the specified argument. If the predicate evaluates to {@code false} an
     * {@link IllegalArgumentException} is thrown. For example, the check if a string is not empty looks like:
     * <pre>
     * String theString = "";
     * ConditionChecker.checkArgument(theString, s -&gt; !s.isEmpty(), () -&gt; "The string must not be empty!");
     * </pre>
     * This example would lead to an IllegalArgumentException.
     *
     * @param argument the argument to be checked.
     * @param argumentPredicate the predicate to test {@code argument} with.
     * @param messageSupplier supplier of the detail message to be used in the event that {@code argumentPredicate}
     * evaluates to {@code false}.
     * @param <T> the type of the argument object.
     * @return the provided argument if {@code argumentPredicate} evaluates to {@code true}.
     * @throws IllegalArgumentException if the application {@code argumentPredicate} on {@code argument} evaluates to
     * {@code false}.
     * @see java.util.function.Predicate
     */
    @Nullable
    public static <T> T checkArgument(@Nullable final T argument, final Predicate<T> argumentPredicate,
            final Supplier<String> messageSupplier) {

        if (!argumentPredicate.test(argument)) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
        return argument;
    }

    /**
     * Ensures that the specified object is not {@code null}, otherwise an {@link IllegalArgumentException} is thrown.
     *
     * @param argument the object to be checked.
     * @param <T> the type of the argument object.
     * @return the provided argument if not {@code null}.
     * @throws IllegalArgumentException if {@code argument} is {@code null}.
     */
    public static <T> T argumentNotNull(@Nullable final T argument) {
        return checkArgument(argument, Objects::nonNull, () -> "The argument must not be null!");
    }

    /**
     * Ensures that the specified object is not {@code null}, otherwise an {@link IllegalArgumentException} is thrown.
     *
     * @param argument the object to be checked.
     * @param argumentName the name or description of the checked object. This is used to build the message of the
     * potential IllegalArgumentException.
     * @param <T> the type of the argument object.
     * @return the provided argument if not {@code null}.
     * @throws IllegalArgumentException if {@code argument} is {@code null}.
     */
    public static <T> T argumentNotNull(@Nullable final T argument, final String argumentName) {
        return checkArgument(argument, Objects::nonNull,
                () -> MessageFormat.format("The argument ''{0}'' must not be null!", argumentName));
    }

    /**
     * Ensures that the specified char sequence is not empty, otherwise an {@link IllegalArgumentException} is thrown.
     *
     * @param <T> the type of the argument
     * @param argument the char sequence to be checked.
     * @return the provided char sequence if it is not empty.
     * @throws NullPointerException if {@code argument} is null.
     * @throws IllegalArgumentException if {@code argument} is empty.
     */
    public static <T extends CharSequence> T argumentNotEmpty(final T argument) {
        checkNotNull(argument);

        return checkArgument(argument, s -> 0 < s.length(), () -> "The char sequence argument must not be empty!");
    }

    /**
     * Ensures that the specified char sequence is not empty, otherwise an {@link IllegalArgumentException} is thrown.
     *
     * @param <T> the type of the argument
     * @param argument the char sequence to be checked.
     * @param argumentName the name or description of the checked object. This is used to build the message of the
     * potential IllegalArgumentException.
     * @return the provided char sequence if it is not empty.
     * @throws NullPointerException if {@code argument} is null.
     * @throws IllegalArgumentException if {@code argument} is empty.
     */
    public static <T extends CharSequence> T argumentNotEmpty(final T argument, final String argumentName) {
        checkNotNull(argument, argumentName);

        return checkArgument(argument, s -> 0 < s.length(),
                () -> MessageFormat.format("The argument ''{0}'' must not be empty!", argumentName));
    }

    /**
     * Ensures that the specified collection is not empty, otherwise an {@link IllegalArgumentException} is thrown.
     *
     * @param <T> the type of the collection entries.
     * @param argument the collection to be checked.
     * @return the provided collection if it is not empty.
     * @throws NullPointerException if {@code argument} is null.
     * @throws IllegalArgumentException if {@code argument} is empty.
     */
    public static <T extends Collection> T argumentNotEmpty(final T argument) {
        checkNotNull(argument);

        return checkArgument(argument, c -> !c.isEmpty(), () -> "The collection argument must not be empty!");
    }

    /**
     * Ensures that the specified collection is not empty, otherwise an {@link IllegalArgumentException} is thrown.
     *
     * @param <T> the type of the collection entries.
     * @param argument the collection to be checked.
     * @param argumentName the name or description of the checked object. This is used to build the message of the
     * potential IllegalArgumentException.
     * @return the provided collection if it is not empty.
     * @throws NullPointerException if {@code argument} is null.
     * @throws IllegalArgumentException if {@code argument} is empty.
     */
    public static <T extends Collection> T argumentNotEmpty(final T argument, final String argumentName) {
        checkNotNull(argument, argumentName);

        return checkArgument(argument, c -> !c.isEmpty(),
                () -> MessageFormat.format("The argument ''{0}'' must not be empty!", argumentName));
    }

}
