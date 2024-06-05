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

    private final boolean enforcePresenceOfModeledFeatures;
    private final boolean allowNonModeledFeatures;
    private final boolean enforceProperties;
    private final boolean allowNonModeledProperties;
    private final boolean enforceDesiredProperties;
    private final boolean allowNonModeledDesiredProperties;
    private final boolean enforceInboxMessages;
    private final boolean allowNonModeledInboxMessages;
    private final boolean enforceOutboxMessages;
    private final boolean allowNonModeledOutboxMessages;

    private DefaultFeatureValidationConfig(final ScopedConfig scopedConfig) {
        enforcePresenceOfModeledFeatures =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_PRESENCE_OF_MODELED_FEATURES.getConfigPath());
        allowNonModeledFeatures =
                scopedConfig.getBoolean(ConfigValue.ALLOW_NON_MODELED_FEATURES.getConfigPath());
        enforceProperties =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_PROPERTIES.getConfigPath());
        allowNonModeledProperties =
                scopedConfig.getBoolean(ConfigValue.ALLOW_NON_MODELED_PROPERTIES.getConfigPath());
        enforceDesiredProperties =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_DESIRED_PROPERTIES.getConfigPath());
        allowNonModeledDesiredProperties =
                scopedConfig.getBoolean(ConfigValue.ALLOW_NON_MODELED_DESIRED_PROPERTIES.getConfigPath());
        enforceInboxMessages =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_INBOX_MESSAGES.getConfigPath());
        allowNonModeledInboxMessages =
                scopedConfig.getBoolean(ConfigValue.ALLOW_NON_MODELED_INBOX_MESSAGES.getConfigPath());
        enforceOutboxMessages =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_OUTBOX_MESSAGES.getConfigPath());
        allowNonModeledOutboxMessages =
                scopedConfig.getBoolean(ConfigValue.ALLOW_NON_MODELED_OUTBOX_MESSAGES.getConfigPath());
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
    public boolean isEnforcePresenceOfModeledFeatures() {
        return enforcePresenceOfModeledFeatures;
    }

    @Override
    public boolean isAllowNonModeledFeatures() {
        return allowNonModeledFeatures;
    }

    @Override
    public boolean isEnforceProperties() {
        return enforceProperties;
    }

    @Override
    public boolean isAllowNonModeledProperties() {
        return allowNonModeledProperties;
    }

    @Override
    public boolean isEnforceDesiredProperties() {
        return enforceDesiredProperties;
    }

    @Override
    public boolean isAllowNonModeledDesiredProperties() {
        return allowNonModeledDesiredProperties;
    }

    @Override
    public boolean isEnforceInboxMessages() {
        return enforceInboxMessages;
    }

    @Override
    public boolean isAllowNonModeledInboxMessages() {
        return allowNonModeledInboxMessages;
    }

    @Override
    public boolean isEnforceOutboxMessages() {
        return enforceOutboxMessages;
    }

    @Override
    public boolean isAllowNonModeledOutboxMessages() {
        return allowNonModeledOutboxMessages;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultFeatureValidationConfig that = (DefaultFeatureValidationConfig) o;
        return enforcePresenceOfModeledFeatures == that.enforcePresenceOfModeledFeatures &&
                allowNonModeledFeatures == that.allowNonModeledFeatures &&
                enforceProperties == that.enforceProperties &&
                allowNonModeledProperties == that.allowNonModeledProperties &&
                enforceDesiredProperties == that.enforceDesiredProperties &&
                allowNonModeledDesiredProperties == that.allowNonModeledDesiredProperties &&
                enforceInboxMessages == that.enforceInboxMessages &&
                allowNonModeledInboxMessages == that.allowNonModeledInboxMessages &&
                enforceOutboxMessages == that.enforceOutboxMessages &&
                allowNonModeledOutboxMessages == that.allowNonModeledOutboxMessages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforcePresenceOfModeledFeatures, allowNonModeledFeatures, enforceProperties,
                allowNonModeledProperties, enforceDesiredProperties, allowNonModeledDesiredProperties,
                enforceInboxMessages, allowNonModeledInboxMessages, enforceOutboxMessages,
                allowNonModeledOutboxMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforcePresenceOfModeledFeatures=" + enforcePresenceOfModeledFeatures +
                ", allowNonModeledFeatures=" + allowNonModeledFeatures +
                ", enforceProperties=" + enforceProperties +
                ", allowNonModeledProperties=" + allowNonModeledProperties +
                ", enforceDesiredProperties=" + enforceDesiredProperties +
                ", allowNonModeledDesiredProperties=" + allowNonModeledDesiredProperties +
                ", enforceInboxMessages=" + enforceInboxMessages +
                ", allowNonModeledInboxMessages=" + allowNonModeledInboxMessages +
                ", enforceOutboxMessages=" + enforceOutboxMessages +
                ", allowNonModeledOutboxMessages=" + allowNonModeledOutboxMessages +
                "]";
    }
}
