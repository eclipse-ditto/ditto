/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.validation.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for WoT (Web of Things) based validation of Features.
 *
 * @since 3.6.0
 */
@Immutable
public interface FeatureValidationConfig {

    /**
     * @return whether to enforce/validate a feature whenever its {@code description} is modified.
     */
    boolean isEnforceFeatureDescriptionModification();

    /**
     * @return whether to forbid deletion of a Feature's {@code description}.
     */
    boolean isForbidFeatureDescriptionDeletion();

    /**
     * @return whether to enforce that all modeled features (submodels referenced in the Thing's {@code definition}'s
     * WoT model) are present.
     */
    boolean isEnforcePresenceOfModeledFeatures();

    /**
     * @return whether to forbid adding features to a Thing which were not defined in its {@code definition}'s
     * WoT model.
     */
    boolean isForbidNonModeledFeatures();

    /**
     * @return whether to enforce/validate properties of a feature following the defined WoT properties.
     */
    boolean isEnforceProperties();

    /**
     * @return whether to forbid persisting properties which are not defined as properties in the WoT model.
     */
    boolean isForbidNonModeledProperties();

    /**
     * @return whether to enforce/validate desired properties of a feature following the defined WoT properties.
     */
    boolean isEnforceDesiredProperties();

    /**
     * @return whether to forbid persisting desired properties which are not defined as properties in the WoT model.
     */
    boolean isForbidNonModeledDesiredProperties();

    /**
     * @return whether to enforce/validate inbox messages to a feature following the defined WoT action "input".
     */
    boolean isEnforceInboxMessagesInput();

    /**
     * @return whether to enforce/validate inbox message responses to a feature following the defined WoT action "output".
     */
    boolean isEnforceInboxMessagesOutput();

    /**
     * @return whether to forbid dispatching of inbox messages which are not defined as actions in the WoT model.
     */
    boolean isForbidNonModeledInboxMessages();

    /**
     * @return whether to enforce/validate outbox messages from a feature following the defined WoT actions.
     */
    boolean isEnforceOutboxMessages();

    /**
     * @return whether to forbid dispatching of outbox messages which are not defined as actions in the WoT model.
     */
    boolean isForbidNonModeledOutboxMessages();


    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code FeatureValidationConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        ENFORCE_FEATURE_DESCRIPTION_MODIFICATION("enforce-feature-description-modification", true),

        FORBID_FEATURE_DESCRIPTION_DELETION("forbid-feature-description-deletion", true),

        ENFORCE_PRESENCE_OF_MODELED_FEATURES("enforce-presence-of-modeled-features", true),

        FORBID_NON_MODELED_FEATURES("forbid-non-modeled-features", true),

        ENFORCE_PROPERTIES("enforce-properties", true),

        FORBID_NON_MODELED_PROPERTIES("forbid-non-modeled-properties", false),

        ENFORCE_DESIRED_PROPERTIES("enforce-desired-properties", true),

        FORBID_NON_MODELED_DESIRED_PROPERTIES("forbid-non-modeled-desired-properties", true),

        ENFORCE_INBOX_MESSAGES_INPUT("enforce-inbox-messages-input", true),

        ENFORCE_INBOX_MESSAGES_OUTPUT("enforce-inbox-messages-output", true),

        FORBID_NON_MODELED_INBOX_MESSAGES("forbid-non-modeled-inbox-messages", true),

        ENFORCE_OUTBOX_MESSAGES("enforce-outbox-messages", true),

        FORBID_NON_MODELED_OUTBOX_MESSAGES("forbid-non-modeled-outbox-messages", true);


        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}
