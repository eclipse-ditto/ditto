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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Functional interface for mapping an {@link Adaptable} to {@link T}.
 *
 * @param <T> the type of the mapped result.
 */
@FunctionalInterface
public interface JsonifiableMapper<T extends Jsonifiable> {

    /**
     * Maps the given {@code adaptable} to the specified {@code T}.
     *
     * @param adaptable the adaptable to map.
     * @return the mapped {@code T}.
     */
    T map(Adaptable adaptable);

}
