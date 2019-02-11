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

import org.eclipse.ditto.model.placeholders.internal.PipelineFactory;

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
     * @return TODO TJ doc
     */
    public static <T> PlaceholderResolver<T> newPlaceholderResolver(final Placeholder<T> placeholder,
            @Nullable final T value) {
        return PipelineFactory.newPlaceholderResolver(placeholder, value);
    }

    /**
     * @return TODO TJ doc
     */
    public static <T> PlaceholderResolver<T> newPlaceholderResolverForValidation(final Placeholder<T> placeholder) {
        return PipelineFactory.newPlaceholderResolverForValidation(placeholder);
    }

    /**
     * @return TODO TJ doc
     */
    public static <T> ExpressionResolver newExpressionResolver(final Placeholder<T> placeholder,
            @Nullable final T value) {
        return newExpressionResolver(Collections.singletonList(newPlaceholderResolver(placeholder, value)));
    }

    /**
     * @return TODO TJ doc
     */
    public static ExpressionResolver newExpressionResolverForValidation(final Placeholder<?> placeholder) {
        return newExpressionResolver(Collections.singletonList(newPlaceholderResolverForValidation(placeholder)));
    }

    /**
     * @return TODO TJ doc
     */
    public static ExpressionResolver newExpressionResolver(
            final PlaceholderResolver<?>... placeholderResolvers) {
        return newExpressionResolver(Arrays.asList(placeholderResolvers));
    }

    /**
     * @return TODO TJ doc
     */
    public static ExpressionResolver newExpressionResolver(
            final List<PlaceholderResolver<?>> placeholderResolvers) {
        return PipelineFactory.newExpressionResolver(placeholderResolvers);
    }

    private PlaceholderFactory() {
        throw new AssertionError();
    }
}
