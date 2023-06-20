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
     * @return whether to enforce/validate properties of a feature following the defined WoT properties.
     */
    boolean isEnforceProperties();

    /**
     * @return whether to allow/accept persisting properties which are not defined as properties in the WoT model.
     */
    boolean isAllowNonModeledProperties();

    /**
     * @return whether to enforce/validate desired properties of a feature following the defined WoT properties.
     */
    boolean isEnforceDesiredProperties();

    /**
     * @return whether to allow/accept persisting desired properties which are not defined as properties in the WoT model.
     */
    boolean isAllowNonModeledDesiredProperties();

    /**
     * @return whether to enforce/validate inbox messages to a feature following the defined WoT actions.
     */
    boolean isEnforceInboxMessages();

    /**
     * @return whether to allow/accept dispatching of inbox messages which are not defined as actions in the WoT model.
     */
    boolean isAllowNonModeledInboxMessages();

    /**
     * @return whether to enforce/validate outbox messages from a feature following the defined WoT actions.
     */
    boolean isEnforceOutboxMessages();

    /**
     * @return whether to allow/accept dispatching of outbox messages which are not defined as actions in the WoT model.
     */
    boolean isAllowNonModeledOutboxMessages();


    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code FeatureValidationConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        ENFORCE_PROPERTIES("enforce-properties", true),

        ALLOW_NON_MODELED_PROPERTIES("allow-non-modeled-properties", false),

        ENFORCE_DESIRED_PROPERTIES("enforce-desired-properties", true),

        ALLOW_NON_MODELED_DESIRED_PROPERTIES("allow-non-modeled-desired-properties", false),

        ENFORCE_INBOX_MESSAGES("enforce-inbox-messages", true),

        ALLOW_NON_MODELED_INBOX_MESSAGES("allow-non-modeled-inbox-messages", false),

        ENFORCE_OUTBOX_MESSAGES("enforce-outbox-messages", true),

        ALLOW_NON_MODELED_OUTBOX_MESSAGES("allow-non-modeled-outbox-messages", false);


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
