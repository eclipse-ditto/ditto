/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.placeholders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Factory that creates instances of {@link Placeholder}, {@link PlaceholderResolver}s and {@link ExpressionResolver}s.
 */
public final class PlaceholderFactory {

    /**
     * @return new instance of the {@link HeadersPlaceholder}
     */
    public static HeadersPlaceholder newHeadersPlaceholder() {
        return ImmutableHeadersPlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link ThingPlaceholder}
     */
    public static ThingPlaceholder newThingPlaceholder() {
        return ImmutableThingPlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link SourceAddressPlaceholder}
     */
    public static SourceAddressPlaceholder newSourceAddressPlaceholder() {
        return ImmutableSourceAddressPlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link TopicPathPlaceholder}
     */
    public static TopicPathPlaceholder newTopicPathPlaceholder() {
        return ImmutableTopicPathPlaceholder.INSTANCE;
    }

    /**
     * Creates a new PlaceholderResolver instance based on the given {@link Placeholder} and a placeholder source for
     * looking up placeholder names in.
     *
     * @param placeholder the placeholder.
     * @param placeholderSource the placeholder source for looking up placeholder names in.
     * @param <T> the type of the placeholder source
     * @return the created PlaceholderResolver instance
     */
    public static <T> PlaceholderResolver<T> newPlaceholderResolver(final Placeholder<T> placeholder,
            @Nullable final T placeholderSource) {
        return new ImmutablePlaceholderResolver<>(placeholder, placeholderSource, false);
    }

    /**
     * Creates a new PlaceholderResolver instance for validation based on the given {@link Placeholder}. As for
     * validation no lookup in a placeholder source has to be made, the source must not be provided.
     *
     * @param placeholder the placeholder.
     * @param <T> the type of the placeholder source
     * @return the created PlaceholderResolver instance
     */
    public static <T> PlaceholderResolver<T> newPlaceholderResolverForValidation(final Placeholder<T> placeholder) {
        return new ImmutablePlaceholderResolver<>(placeholder, null, true);
    }

    /**
     * Creates a new ExpressionResolver instance initialized with the passed in {@code placeholderResolvers} for looking
     * up {@link Placeholder}s.
     *
     * @param placeholderResolvers the PlaceholderResolvers to use in order to lookup placeholders in expressions.
     * @return the created ExpressionResolver instance
     */
    public static ExpressionResolver newExpressionResolver(
            final PlaceholderResolver<?>... placeholderResolvers) {
        return newExpressionResolver(Arrays.asList(placeholderResolvers));
    }

    /**
     * Creates a new ExpressionResolver instance initialized with the passed in {@code placeholderResolvers} for looking
     * up {@link Placeholder}s.
     *
     * @param placeholderResolvers the PlaceholderResolvers to use in order to lookup placeholders in expressions.
     * @return the created ExpressionResolver instance
     */
    public static ExpressionResolver newExpressionResolver(final List<PlaceholderResolver<?>> placeholderResolvers) {
        return new ImmutableExpressionResolver(placeholderResolvers);
    }

    /**
     * Creates a new ExpressionResolver instance initialized with a single {@code placeholder} and
     * {@code placeholderSource} for looking up {@link Placeholder}s.
     *
     * @param placeholder the placeholder.
     * @param placeholderSource the placeholder source for looking up placeholder names in.
     * @return the created ExpressionResolver instance
     */
    public static <T> ExpressionResolver newExpressionResolver(final Placeholder<T> placeholder,
            @Nullable final T placeholderSource) {
        return newExpressionResolver(Collections.singletonList(newPlaceholderResolver(placeholder, placeholderSource)));
    }

    /**
     * Creates a new ExpressionResolver instance for validation initialized with a single {@code placeholder}.
     *
     * @param placeholder the placeholder.
     * @return the created ExpressionResolver instance
     */
    public static ExpressionResolver newExpressionResolverForValidation(final Placeholder<?> placeholder) {
        return newExpressionResolver(Collections.singletonList(newPlaceholderResolverForValidation(placeholder)));
    }

    private PlaceholderFactory() {
        throw new AssertionError();
    }
}
