/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;

import javax.annotation.Nullable;

import java.util.Optional;

/**
 * Interface for Thing-level WoT validation configuration.
 * This represents configuration settings for Thing-level validation.
 *
 * @since 3.8.0
 */
public interface ThingValidationConfig extends Jsonifiable<JsonObject> {

    /**
     * Returns the enforce configuration.
     *
     * @return an optional containing the enforce configuration
     */
    Optional<ThingValidationEnforceConfig> getEnforce();

    /**
     * Returns the forbid configuration.
     *
     * @return an optional containing the forbid configuration
     */
    Optional<ThingValidationForbidConfig> getForbid();

    /**
     * Creates a new instance of {@link ThingValidationConfig}.
     */
    static ThingValidationConfig of(
            @Nullable final ThingValidationEnforceConfig enforce,
            @Nullable final ThingValidationForbidConfig forbid) {
        return ImmutableThingValidationConfig.of(enforce, forbid);
    }

    /**
     * Creates a new instance of {@link ThingValidationConfig} from the given JSON object.
     *
     */
    static ThingValidationConfig fromJson(final JsonObject json) {
        return ImmutableThingValidationConfig.fromJson(json);
    }
} 