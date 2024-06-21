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
package org.eclipse.ditto.wot.api.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.wot.validation.config.FeatureValidationConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the WoT (Web of Things) {@link org.eclipse.ditto.wot.validation.config.FeatureValidationConfig}.
 */
@Immutable
final class DefaultFeatureValidationConfig implements FeatureValidationConfig {

    private static final String CONFIG_PATH = "feature";

    private final boolean enforceFeatureDescriptionModification;
    private final boolean enforcePresenceOfModeledFeatures;
    private final boolean forbidNonModeledFeatures;
    private final boolean enforceProperties;
    private final boolean forbidNonModeledProperties;
    private final boolean enforceDesiredProperties;
    private final boolean forbidNonModeledDesiredProperties;
    private final boolean enforceInboxMessagesInput;
    private final boolean enforceInboxMessagesOutput;
    private final boolean forbidNonModeledInboxMessages;
    private final boolean enforceOutboxMessages;
    private final boolean forbidNonModeledOutboxMessages;

    private DefaultFeatureValidationConfig(final ScopedConfig scopedConfig) {
        enforceFeatureDescriptionModification =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_FEATURE_DESCRIPTION_MODIFICATION.getConfigPath());
        enforcePresenceOfModeledFeatures =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_PRESENCE_OF_MODELED_FEATURES.getConfigPath());
        forbidNonModeledFeatures =
                scopedConfig.getBoolean(ConfigValue.FORBID_NON_MODELED_FEATURES.getConfigPath());
        enforceProperties =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_PROPERTIES.getConfigPath());
        forbidNonModeledProperties =
                scopedConfig.getBoolean(ConfigValue.FORBID_NON_MODELED_PROPERTIES.getConfigPath());
        enforceDesiredProperties =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_DESIRED_PROPERTIES.getConfigPath());
        forbidNonModeledDesiredProperties =
                scopedConfig.getBoolean(ConfigValue.FORBID_NON_MODELED_DESIRED_PROPERTIES.getConfigPath());
        enforceInboxMessagesInput =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_INBOX_MESSAGES_INPUT.getConfigPath());
        enforceInboxMessagesOutput =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_INBOX_MESSAGES_OUTPUT.getConfigPath());
        forbidNonModeledInboxMessages =
                scopedConfig.getBoolean(ConfigValue.FORBID_NON_MODELED_INBOX_MESSAGES.getConfigPath());
        enforceOutboxMessages =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_OUTBOX_MESSAGES.getConfigPath());
        forbidNonModeledOutboxMessages =
                scopedConfig.getBoolean(ConfigValue.FORBID_NON_MODELED_OUTBOX_MESSAGES.getConfigPath());
    }

    /**
     * Returns an instance of the thing config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the thing config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultFeatureValidationConfig of(final Config config) {
        return new DefaultFeatureValidationConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                ConfigValue.values()));
    }

    @Override
    public boolean isEnforceFeatureDescriptionModification() {
        return enforceFeatureDescriptionModification;
    }

    @Override
    public boolean isEnforcePresenceOfModeledFeatures() {
        return enforcePresenceOfModeledFeatures;
    }

    @Override
    public boolean isForbidNonModeledFeatures() {
        return forbidNonModeledFeatures;
    }

    @Override
    public boolean isEnforceProperties() {
        return enforceProperties;
    }

    @Override
    public boolean isForbidNonModeledProperties() {
        return forbidNonModeledProperties;
    }

    @Override
    public boolean isEnforceDesiredProperties() {
        return enforceDesiredProperties;
    }

    @Override
    public boolean isForbidNonModeledDesiredProperties() {
        return forbidNonModeledDesiredProperties;
    }

    @Override
    public boolean isEnforceInboxMessagesInput() {
        return enforceInboxMessagesInput;
    }

    @Override
    public boolean isEnforceInboxMessagesOutput() {
        return enforceInboxMessagesOutput;
    }

    @Override
    public boolean isForbidNonModeledInboxMessages() {
        return forbidNonModeledInboxMessages;
    }

    @Override
    public boolean isEnforceOutboxMessages() {
        return enforceOutboxMessages;
    }

    @Override
    public boolean isForbidNonModeledOutboxMessages() {
        return forbidNonModeledOutboxMessages;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultFeatureValidationConfig that = (DefaultFeatureValidationConfig) o;
        return enforcePresenceOfModeledFeatures == that.enforcePresenceOfModeledFeatures &&
                forbidNonModeledFeatures == that.forbidNonModeledFeatures &&
                enforceProperties == that.enforceProperties &&
                forbidNonModeledProperties == that.forbidNonModeledProperties &&
                enforceDesiredProperties == that.enforceDesiredProperties &&
                forbidNonModeledDesiredProperties == that.forbidNonModeledDesiredProperties &&
                enforceInboxMessagesInput == that.enforceInboxMessagesInput &&
                forbidNonModeledInboxMessages == that.forbidNonModeledInboxMessages &&
                enforceOutboxMessages == that.enforceOutboxMessages &&
                forbidNonModeledOutboxMessages == that.forbidNonModeledOutboxMessages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforcePresenceOfModeledFeatures, forbidNonModeledFeatures, enforceProperties,
                forbidNonModeledProperties, enforceDesiredProperties, forbidNonModeledDesiredProperties,
                enforceInboxMessagesInput, enforceInboxMessagesOutput, forbidNonModeledInboxMessages,
                enforceOutboxMessages, forbidNonModeledOutboxMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforcePresenceOfModeledFeatures=" + enforcePresenceOfModeledFeatures +
                ", forbidNonModeledFeatures=" + forbidNonModeledFeatures +
                ", enforceProperties=" + enforceProperties +
                ", forbidNonModeledProperties=" + forbidNonModeledProperties +
                ", enforceDesiredProperties=" + enforceDesiredProperties +
                ", forbidNonModeledDesiredProperties=" + forbidNonModeledDesiredProperties +
                ", enforceInboxMessagesInput=" + enforceInboxMessagesInput +
                ", enforceInboxMessagesOutput=" + enforceInboxMessagesOutput +
                ", forbidNonModeledInboxMessages=" + forbidNonModeledInboxMessages +
                ", enforceOutboxMessages=" + enforceOutboxMessages +
                ", forbidNonModeledOutboxMessages=" + forbidNonModeledOutboxMessages +
                "]";
    }
}
