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
 * TODO TJ docs
 */
public interface PlaceholderResolver<T> extends Placeholder<T> {

    boolean isForValidation();

    Optional<T> getValueToResolveFrom();

    default Optional<String> resolve(final String name) {
        if (isForValidation()) {
            return Optional.of("valid");
        }
        return getValueToResolveFrom()
                .flatMap(value -> resolve(value, name));
    }
}
