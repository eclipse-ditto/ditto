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
 * Interface for Thing-level forbid configuration in WoT validation.
 * This represents configuration settings for forbidding certain Thing-level operations.
 *
 * @since 3.8.0
 */
public interface ThingValidationForbidConfig extends Jsonifiable<JsonObject> {

    /**
     * Returns whether thing description deletion is forbidden.
     *
     * @return an optional containing whether thing description deletion is forbidden
     */
    Optional<Boolean> isThingDescriptionDeletion();

    /**
     * Returns whether non-modeled attributes are forbidden.
     *
     * @return an optional containing whether non-modeled attributes are forbidden
     */
    Optional<Boolean> isNonModeledAttributes();


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
     * Creates a new instance of {@link ThingValidationForbidConfig}.
     */
    static ThingValidationForbidConfig of(
            @Nullable final Boolean thingDescriptionDeletion,
            @Nullable final Boolean nonModeledAttributes,
            @Nullable final Boolean nonModeledInboxMessages,
            @Nullable final Boolean nonModeledOutboxMessages) {
        return ImmutableThingValidationForbidConfig.of(thingDescriptionDeletion, nonModeledAttributes,
                nonModeledInboxMessages, nonModeledOutboxMessages);
    }
} 