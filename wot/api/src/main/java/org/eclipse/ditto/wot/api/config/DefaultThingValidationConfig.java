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
import org.eclipse.ditto.wot.validation.config.ThingValidationConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the WoT (Web of Things) {@link org.eclipse.ditto.wot.validation.config.ThingValidationConfig}.
 */
@Immutable
final class DefaultThingValidationConfig implements ThingValidationConfig {

    private static final String CONFIG_PATH = "thing";

    private final boolean enforceAttributes;
    private final boolean allowNonModeledAttributes;
    private final boolean enforceInboxMessages;
    private final boolean allowNonModeledInboxMessages;
    private final boolean enforceOutboxMessages;
    private final boolean allowNonModeledOutboxMessages;

    private DefaultThingValidationConfig(final ScopedConfig scopedConfig) {
        enforceAttributes =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_ATTRIBUTES.getConfigPath());
        allowNonModeledAttributes =
                scopedConfig.getBoolean(ConfigValue.ALLOW_NON_MODELED_ATTRIBUTES.getConfigPath());
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
    public static DefaultThingValidationConfig of(final Config config) {
        return new DefaultThingValidationConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                ConfigValue.values()));
    }

    @Override
    public boolean isEnforceAttributes() {
        return enforceAttributes;
    }

    @Override
    public boolean isAllowNonModeledAttributes() {
        return allowNonModeledAttributes;
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
        final DefaultThingValidationConfig that = (DefaultThingValidationConfig) o;
        return enforceAttributes == that.enforceAttributes &&
                allowNonModeledAttributes == that.allowNonModeledAttributes &&
                enforceInboxMessages == that.enforceInboxMessages &&
                allowNonModeledInboxMessages == that.allowNonModeledInboxMessages &&
                enforceOutboxMessages == that.enforceOutboxMessages &&
                allowNonModeledOutboxMessages == that.allowNonModeledOutboxMessages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforceAttributes, allowNonModeledAttributes, enforceInboxMessages,
                allowNonModeledInboxMessages, enforceOutboxMessages, allowNonModeledOutboxMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforceAttributes=" + enforceAttributes +
                ", allowNonModeledAttributes=" + allowNonModeledAttributes +
                ", enforceInboxMessages=" + enforceInboxMessages +
                ", allowNonModeledInboxMessages=" + allowNonModeledInboxMessages +
                ", enforceOutboxMessages=" + enforceOutboxMessages +
                ", allowNonModeledOutboxMessages=" + allowNonModeledOutboxMessages +
                "]";
    }
}
