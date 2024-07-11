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
 * Provides configuration settings for WoT (Web of Things) based validation of Things.
 *
 * @since 3.6.0
 */
@Immutable
public interface ThingValidationConfig {

    /**
     * @return whether to enforce/validate a thing whenever its {@code description} is modified.
     */
    boolean isEnforceThingDescriptionModification();

    /**
     * @return whether to enforce/validate attributes of a thing following the defined WoT properties.
     */
    boolean isEnforceAttributes();

    /**
     * @return whether to forbid persisting attributes which are not defined as properties in the WoT model.
     */
    boolean isForbidNonModeledAttributes();

    /**
     * @return whether to enforce/validate inbox messages to a thing following the defined WoT action "input".
     */
    boolean isEnforceInboxMessagesInput();

    /**
     * @return whether to enforce/validate inbox message responses to a thing following the defined WoT action "output".
     */
    boolean isEnforceInboxMessagesOutput();

    /**
     * @return whether to forbid dispatching of inbox messages which are not defined as actions in the WoT model.
     */
    boolean isForbidNonModeledInboxMessages();

    /**
     * @return whether to enforce/validate outbox messages from a thing following the defined WoT actions.
     */
    boolean isEnforceOutboxMessages();

    /**
     * @return whether to forbid dispatching of outbox messages which are not defined as actions in the WoT model.
     */
    boolean isForbidNonModeledOutboxMessages();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ThingValidationConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        ENFORCE_THING_DESCRIPTION_MODIFICATION("enforce-thing-description-modification", false),

        ENFORCE_ATTRIBUTES("enforce-attributes", true),

        FORBID_NON_MODELED_ATTRIBUTES("forbid-non-modeled-attributes", true),

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
