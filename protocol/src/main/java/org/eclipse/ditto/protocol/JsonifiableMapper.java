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
package org.eclipse.ditto.protocol;

import org.eclipse.ditto.base.model.json.Jsonifiable;

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
