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
 * Public API for feature-level configuration for WoT validation.
 * <p>
 * This interface exposes configuration options for feature validation, including enforce and forbid rules.
 * Implementations must be immutable and thread-safe.
 * </p>
 *
 * @since 3.8.0
 */
public interface FeatureValidationConfig extends Jsonifiable<JsonObject> {
    /**
     * Returns the enforce configuration.
     *
     * @return an optional containing the enforce configuration.
     */
    Optional<FeatureValidationEnforceConfig> getEnforce();

    /**
     * Returns the forbid configuration.
     *
     * @return an optional containing the forbid configuration.
     */
    Optional<FeatureValidationForbidConfig> getForbid();

    /**
     * Returns this configuration as JSON object.
     *
     * @return the JSON object representation of this configuration.
     */
    JsonObject toJson();

    /**
     * Creates a new instance of {@link FeatureValidationConfig} with the specified enforce and forbid configurations.
     *
     * @param enforce optional enforce configuration
     * @param forbid optional forbid configuration
     * @return a new instance with the specified values
     */
    static FeatureValidationConfig of(
            @Nullable final FeatureValidationEnforceConfig enforce,
            @Nullable final FeatureValidationForbidConfig forbid)  {
        return ImmutableFeatureValidationConfig.of(enforce, forbid);
    }

    /**
     * Creates a new instance of {@link FeatureValidationConfig} from the given JSON object.
     *
     * @param json the JSON object to create the configuration from
     * @return a new instance of {@link FeatureValidationConfig}
     */
    static FeatureValidationConfig fromJson(final JsonObject json) {
        return ImmutableFeatureValidationConfig.fromJson(json);
    }
}