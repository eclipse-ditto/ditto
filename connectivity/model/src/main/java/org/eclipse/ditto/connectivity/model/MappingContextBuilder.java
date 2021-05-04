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
package org.eclipse.ditto.connectivity.model;

import java.util.Map;

import org.eclipse.ditto.json.JsonObject;

/**
 * A builder for a {@link MappingContext} with a fluent API.
 *
 * @since 1.3.0
 */
public interface MappingContextBuilder {

    /**
     * Sets the MappingEngine.
     *
     * @param mappingEngine the MappingEngine
     * @return this builder
     */
    MappingContextBuilder mappingEngine(String mappingEngine);

    /**
     * Sets the options used by the mapper.
     *
     * @param options the options
     * @return this builder
     */
    MappingContextBuilder options(JsonObject options);

    /**
     * Sets the conditions for mapping incoming messages.
     *
     * @param incomingConditions the conditions.
     * @return this builder
     */
    MappingContextBuilder incomingConditions(Map<String, String> incomingConditions);

    /**
     * Sets the conditions for mapping outgoing messages.
     *
     * @param outgoingConditions the conditions.
     * @return this builder
     */
    MappingContextBuilder outgoingConditions(Map<String, String> outgoingConditions);

    /**
     * Build the {@link MappingContext} instance.
     *
     * @return the new {@link MappingContext} instance
     */
    MappingContext build();

}
