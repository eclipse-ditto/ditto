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
 * Interface for Feature-level enforce configuration in WoT validation.
 * This represents configuration settings for enforcing Feature-level validation rules.
 *
 * @since 3.8.0
 */
public interface FeatureValidationEnforceConfig extends Jsonifiable<JsonObject> {

    /**
     * Returns whether feature description modification is enforced.
     *
     * @return an optional containing whether feature description modification is enforced
     */
    Optional<Boolean> isFeatureDescriptionModification();

    /**
     * Returns whether presence of modeled features is enforced.
     *
     * @return an optional containing whether presence of modeled features is enforced
     */
    Optional<Boolean> isPresenceOfModeledFeatures();

    /**
     * Returns whether properties are enforced.
     *
     * @return an optional containing whether properties are enforced
     */
    Optional<Boolean> isProperties();

    /**
     * Returns whether desired properties are enforced.
     *
     * @return an optional containing whether desired properties are enforced
     */
    Optional<Boolean> isDesiredProperties();

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
     * Creates a new instance of {@link FeatureValidationEnforceConfig} with the specified parameters.
     *
     * @param featureDescriptionModification whether feature description modification is enforced, may be {@code null}
     * @param presenceOfModeledFeatures whether presence of modeled features is enforced, may be {@code null}
     * @param properties whether properties are enforced, may be {@code null}
     * @param desiredProperties whether desired properties are enforced, may be {@code null}
     * @param inboxMessagesInput whether inbox messages input is enforced, may be {@code null}
     * @param inboxMessagesOutput whether inbox messages output is enforced, may be {@code null}
     * @param outboxMessages whether outbox messages are enforced, may be {@code null}
     * @return a new instance of {@link FeatureValidationEnforceConfig}
     */
    static FeatureValidationEnforceConfig of(
            @Nullable final Boolean featureDescriptionModification,
            @Nullable final Boolean presenceOfModeledFeatures,
            @Nullable final Boolean properties,
            @Nullable final Boolean desiredProperties,
            @Nullable final Boolean inboxMessagesInput,
            @Nullable final Boolean inboxMessagesOutput,
            @Nullable final Boolean outboxMessages) {
        return ImmutableFeatureValidationEnforceConfig.of(
                featureDescriptionModification, presenceOfModeledFeatures, properties, desiredProperties,
                inboxMessagesInput, inboxMessagesOutput, outboxMessages);
    }
} 