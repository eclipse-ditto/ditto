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

        if (isForValidation()) {
            return Optional.of("valid");
        }
        return getPlaceholderSource()
                .flatMap(placeholderSource -> resolve(placeholderSource, name));
    }
}
