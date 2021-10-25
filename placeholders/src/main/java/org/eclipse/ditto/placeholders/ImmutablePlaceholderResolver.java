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
package org.eclipse.ditto.placeholders;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link PlaceholderResolver}.
 */
@Immutable
final class ImmutablePlaceholderResolver<T> implements PlaceholderResolver<T> {

    private final Placeholder<T> placeholder;
    @Nullable private final T placeholderSource;

    ImmutablePlaceholderResolver(final Placeholder<T> placeholder, @Nullable final T placeholderSource) {
        this.placeholder = placeholder;
        this.placeholderSource = placeholderSource;
    }

    @Override
    public Optional<T> getPlaceholderSource() {
        return Optional.ofNullable(placeholderSource);
    }

    @Override
    public Optional<String> resolve(final T placeholderSource, final String name) {
        return placeholder.resolve(placeholderSource, name);
    }

    @Override
    public String getPrefix() {
        return placeholder.getPrefix();
    }

    @Override
    public List<String> getSupportedNames() {
        return placeholder.getSupportedNames();
    }

    @Override
    public boolean supports(final String name) {
        return placeholder.supports(name);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutablePlaceholderResolver)) {
            return false;
        }
        final ImmutablePlaceholderResolver<?> that = (ImmutablePlaceholderResolver<?>) o;
        return Objects.equals(placeholder, that.placeholder) &&
                Objects.equals(placeholderSource, that.placeholderSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placeholder, placeholderSource);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "placeholder=" + placeholder +
                ", value=" + placeholderSource +
                "]";
    }
}
