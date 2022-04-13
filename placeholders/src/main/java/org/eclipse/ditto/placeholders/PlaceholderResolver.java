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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves a passed in placeholder {@code name} from the {@link #getPlaceholderSources() sources}.
 * If this PlaceholderResolver is only used for validation, a constant value of {@code "valid"} is returned instead
 * of asking the resolver.
 */
public interface PlaceholderResolver<T> extends Placeholder<T> {

    /**
     * @return the source from which to resolve a placeholder with a {@code name}.
     * @deprecated Since 2.4.0. Use {@link #getPlaceholderSources()} instead.
     */
    @Deprecated
    default Optional<T> getPlaceholderSource() {
        return getPlaceholderSources().stream().findFirst();
    }

    /**
     * Resolves the passed in {@code name} from the {@link #getPlaceholderSource() resolver}. If this PlaceholderResolver is only
     * used for validation, a constant value of {@code "valid"} is returned instead of asking the resolver.
     *
     * @param name the placeholder name to resolve from the resolver.
     * @return the resolved value or an empty optional if it could not be resolved.
     * @deprecated Since 2.4.0. Use {@link #resolveValues(String)} instead.
     */
    @Deprecated
    default Optional<String> resolve(final String name) {
        return getPlaceholderSource().flatMap(placeholderSource -> resolve(placeholderSource, name));
    }

    /**
     * @return the sources from which to resolve a placeholder with a {@code name}.
     * @since 2.4.0
     */
    List<T> getPlaceholderSources();

    /**
     * Resolves the passed in {@code name} from the {@link #getPlaceholderSources() sources}.
     *
     * @param name the placeholder name to resolve from the resolver.
     * @return the resolved value or an empty optional if it could not be resolved.
     * @since 2.4.0
     */
    default List<String> resolveValues(final String name) {
        return getPlaceholderSources().stream()
                .flatMap(source -> resolveValues(source, name).stream())
                .collect(Collectors.toList());
    }

}
