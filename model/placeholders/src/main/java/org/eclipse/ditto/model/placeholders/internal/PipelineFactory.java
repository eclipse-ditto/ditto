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
package org.eclipse.ditto.model.placeholders.internal;

import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.PlaceholderResolver;

/**
 * Factory that creates .. TODO TJ doc
 */
public final class PipelineFactory {

    /**
     * @return TODO TJ doc
     */
    public static <T> PlaceholderResolver<T> newPlaceholderResolver(final Placeholder<T> placeholder,
            @Nullable final T value) {
        return new ImmutablePlaceholderResolver<>(placeholder, value, false);
    }

    /**
     * @return TODO TJ doc
     */
    public static <T> PlaceholderResolver<T> newPlaceholderResolverForValidation(final Placeholder<T> placeholder) {
        return new ImmutablePlaceholderResolver<>(placeholder, null, true);
    }

    /**
     * @return TODO TJ doc
     */
    public static ExpressionResolver newExpressionResolver(final List<PlaceholderResolver<?>> placeholderResolvers) {
        return new ImmutableExpressionResolver(placeholderResolvers);
    }

    private PipelineFactory() {
        throw new AssertionError();
    }
}
