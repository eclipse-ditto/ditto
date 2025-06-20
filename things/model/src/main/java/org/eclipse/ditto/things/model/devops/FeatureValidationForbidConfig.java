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
 * Interface for Feature-level forbid configuration in WoT validation.
 * This represents configuration settings for forbidding certain Feature-level operations.
 *
 * @since 3.8.0
 */
public interface FeatureValidationForbidConfig extends Jsonifiable<JsonObject> {

    /**
     * Returns whether feature description deletion is forbidden.
     *
     * @return an optional containing whether feature description deletion is forbidden
     */
    Optional<Boolean> isFeatureDescriptionDeletion();

    /**
     * Returns whether non-modeled features are forbidden.
     *
     * @return an optional containing whether non-modeled features are forbidden
     */
    Optional<Boolean> isNonModeledFeatures();

    /**
     * Returns whether non-modeled properties are forbidden.
     *
     * @return an optional containing whether non-modeled properties are forbidden
     */
    Optional<Boolean> isNonModeledProperties();

    /**
     * Returns whether non-modeled desired properties are forbidden.
     *
     * @return an optional containing whether non-modeled desired properties are forbidden
     */
    Optional<Boolean> isNonModeledDesiredProperties();

    /**
     * Returns whether non-modeled inbox messages are forbidden.
     *
     * @return an optional containing whether non-modeled inbox messages are forbidden
     */
    Optional<Boolean> isNonModeledInboxMessages();

    /**
     * Returns whether non-modeled outbox messages are forbidden.
     *
     * @return an optional containing whether non-modeled outbox messages are forbidden
     */
    Optional<Boolean> isNonModeledOutboxMessages();


    /**
     * Creates a new instance of {@link FeatureValidationForbidConfig} with the specified parameters.
     *
     * @param featureDescriptionDeletion whether feature description deletion is forbidden, may be {@code null}
     * @param nonModeledFeatures whether non-modeled features are forbidden, may be {@code null}
     * @param nonModeledProperties whether non-modeled properties are forbidden, may be {@code null}
     * @param nonModeledDesiredProperties whether non-modeled desired properties are forbidden, may be {@code null}
     * @param nonModeledInboxMessages whether non-modeled inbox messages are forbidden, may be {@code null}
     * @param nonModeledOutboxMessages whether non-modeled outbox messages are forbidden, may be {@code null}
     * @return a new instance of {@link FeatureValidationForbidConfig}
     */
    static FeatureValidationForbidConfig of(
            @Nullable final Boolean featureDescriptionDeletion,
            @Nullable final Boolean nonModeledFeatures,
            @Nullable final Boolean nonModeledProperties,
            @Nullable final Boolean nonModeledDesiredProperties,
            @Nullable final Boolean nonModeledInboxMessages,
            @Nullable final Boolean nonModeledOutboxMessages) {
        return ImmutableFeatureValidationForbidConfig.of(
                featureDescriptionDeletion, nonModeledFeatures, nonModeledProperties,
                nonModeledDesiredProperties, nonModeledInboxMessages, nonModeledOutboxMessages);
    }

} 