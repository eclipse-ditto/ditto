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
package org.eclipse.ditto.model.placeholders;

import java.util.Optional;

/**
 * Resolves a passed in placeholder {@code name} from the {@link #getPlaceholderSource() resolver}.
 * If this PlaceholderResolver is only used for validation, a constant value of {@code "valid"} is returned instead
 * of asking the resolver.
 */
public interface PlaceholderResolver<T> extends Placeholder<T> {

    /**
     * @return whether the placeholder is only used for validation (returning {@code true}) or if it is used for actual
     * replacement.
     */
    boolean isForValidation();

    /**
     * @return the source from which to resolve a placeholder with a {@code name}.
     */
    Optional<T> getPlaceholderSource();

    /**
     * Resolves the passed in {@code name} from the {@link #getPlaceholderSource() resolver}. If this PlaceholderResolver is only
     * used for validation, a constant value of {@code "valid"} is returned instead of asking the resolver.
     *
     * @param name the placeholder name to resolve from the resolver.
     * @return the resolved value or an empty optional if it could not be resolved.
     */
    default Optional<String> resolve(final String name) {
        return getPlaceholderSource()
                .flatMap(placeholderSource -> resolve(placeholderSource, name));
    }
}
