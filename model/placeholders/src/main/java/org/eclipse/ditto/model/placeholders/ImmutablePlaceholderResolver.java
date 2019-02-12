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
    private final boolean forValidation;

    ImmutablePlaceholderResolver(final Placeholder<T> placeholder, @Nullable final T placeholderSource,
            boolean forValidation) {
        this.placeholder = placeholder;
        this.placeholderSource = placeholderSource;
        this.forValidation = forValidation;
    }

    @Override
    public Optional<T> getPlaceholderSource() {
        return Optional.ofNullable(placeholderSource);
    }

    @Override
    public boolean isForValidation() {
        return forValidation;
    }

    @Override
    public Optional<String> resolve(final T source, final String name) {
        return placeholder.resolve(source, name);
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
        return forValidation == that.forValidation &&
                Objects.equals(placeholder, that.placeholder) &&
                Objects.equals(placeholderSource, that.placeholderSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placeholder, placeholderSource, forValidation);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "placeholder=" + placeholder +
                ", value=" + placeholderSource +
                ", forValidation=" + forValidation +
                "]";
    }
}
