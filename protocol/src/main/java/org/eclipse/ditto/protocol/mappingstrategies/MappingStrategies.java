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
package org.eclipse.ditto.protocol.mappingstrategies;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Container for strategies that map from {@link org.eclipse.ditto.protocol.Adaptable}s to
 * {@link org.eclipse.ditto.base.model.signals.Signal}s.
 *
 * @param <T> the type of the mapped signals
 */
public interface MappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>> {

    /**
     * Finds the correct {@link JsonifiableMapper} for the given type.
     *
     * @param type the type of the {@link org.eclipse.ditto.protocol.Adaptable}
     * @return the {@link JsonifiableMapper} or {@code null} if no mapper was found
     */
    @Nullable
    JsonifiableMapper<T> find(String type);
}
