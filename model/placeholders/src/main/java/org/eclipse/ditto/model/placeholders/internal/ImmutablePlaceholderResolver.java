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
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.PlaceholderResolver;

/**
 *
 */
final class ImmutablePlaceholderResolver<T> implements PlaceholderResolver<T> {

    private final Placeholder<T> placeholder;
    @Nullable private final T value;
    private final boolean forValidation;

    ImmutablePlaceholderResolver(final Placeholder<T> placeholder, @Nullable final T value, boolean forValidation) {
        this.placeholder = placeholder;
        this.value = value;
        this.forValidation = forValidation;
    }

    @Override
    public Optional<T> getValueToResolveFrom() {
        return Optional.ofNullable(value);
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
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placeholder, value, forValidation);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "placeholder=" + placeholder +
                ", value=" + value +
                ", forValidation=" + forValidation +
                "]";
    }
}
