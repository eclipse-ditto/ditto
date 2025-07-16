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
 * Interface for Thing-level enforce configuration in WoT validation.
 * This represents configuration settings for enforcing Thing-level validation rules.
 *
 * @since 3.8.0
 */
public interface ThingValidationEnforceConfig extends Jsonifiable<JsonObject> {

    /**
     * Returns whether thing description modification is enforced.
     *
     * @return an optional containing whether thing description modification is enforced
     */
    Optional<Boolean> isThingDescriptionModification();

    /**
     * Returns whether attributes modification is enforced.
     *
     * @return an optional containing whether attributes modification is enforced
     */
    Optional<Boolean> isAttributes();

    /**
     * Returns whether inbox messages input is enforced.
     *
     * @return an optional containing whether inbox messages input is enforced
     */
    Optional<Boolean> isInboxMessagesInput();

    /**
     * Returns whether inbox messages output is enforced.
     *
     * @return an optional containing whether inbox messages output is enforced
     */
    Optional<Boolean> isInboxMessagesOutput();

    /**
     * Returns whether outbox messages are enforced.
     *
     * @return an optional containing whether outbox messages are enforced
     */
    Optional<Boolean> isOutboxMessages();

    /**
     * Creates a new instance of {@link ThingValidationEnforceConfig}.
     */
    static ThingValidationEnforceConfig of(
            @Nullable final Boolean thingDescriptionModification,
            @Nullable final Boolean attributes,
            @Nullable final Boolean inboxMessagesInput,
            @Nullable final Boolean inboxMessagesOutput,
            @Nullable final Boolean outboxMessages) {
        return ImmutableThingValidationEnforceConfig.of(thingDescriptionModification, attributes,
                inboxMessagesInput, inboxMessagesOutput, outboxMessages);
    }
} 