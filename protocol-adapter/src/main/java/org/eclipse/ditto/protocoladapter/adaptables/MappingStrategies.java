/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

/* Copyright (c) 2011-2018 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.protocoladapter.adaptables;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;

/**
 * Container for strategies that map from {@link org.eclipse.ditto.protocoladapter.Adaptable}s to
 * {@link org.eclipse.ditto.signals.base.Signal}s.
 *
 * @param <T> the type of the mapped signals
 */
public interface MappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>> {

    /**
     * Finds the correct {@link JsonifiableMapper} for the given type.
     *
     * @param type the type of the {@link org.eclipse.ditto.protocoladapter.Adaptable}
     * @return the {@link JsonifiableMapper} or {@code null} if no mapper was found
     */
    @Nullable
    JsonifiableMapper<T> find(final String type);
}