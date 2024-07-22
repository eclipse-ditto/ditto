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

    private final boolean enforceThingDescriptionModification;
    private final boolean forbidThingDescriptionDeletion;
    private final boolean enforceAttributes;
    private final boolean forbidNonModeledAttributes;
    private final boolean enforceInboxMessagesInput;
    private final boolean enforceInboxMessagesOutput;
    private final boolean forbidNonModeledInboxMessages;
    private final boolean enforceOutboxMessages;
    private final boolean forbidNonModeledOutboxMessages;

    private DefaultThingValidationConfig(final ScopedConfig scopedConfig) {
        enforceThingDescriptionModification =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_THING_DESCRIPTION_MODIFICATION.getConfigPath());
        forbidThingDescriptionDeletion =
                scopedConfig.getBoolean(ConfigValue.FORBID_THING_DESCRIPTION_DELETION.getConfigPath());
        enforceAttributes =
                scopedConfig.getBoolean(ConfigValue.ENFORCE_ATTRIBUTES.getConfigPath());
        forbidNonModeledAttributes =
                scopedConfig.getBoolean(ConfigValue.FORBID_NON_MODELED_ATTRIBUTES.getConfigPath());
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
    public static DefaultThingValidationConfig of(final Config config) {
        return new DefaultThingValidationConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                ConfigValue.values()));
    }

    @Override
    public boolean isEnforceThingDescriptionModification() {
        return enforceThingDescriptionModification;
    }

    @Override
    public boolean isForbidThingDescriptionDeletion() {
        return forbidThingDescriptionDeletion;
    }

    @Override
    public boolean isEnforceAttributes() {
        return enforceAttributes;
    }

    @Override
    public boolean isForbidNonModeledAttributes() {
        return forbidNonModeledAttributes;
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
        final DefaultThingValidationConfig that = (DefaultThingValidationConfig) o;
        return enforceThingDescriptionModification == that.enforceThingDescriptionModification &&
                forbidThingDescriptionDeletion == that.forbidThingDescriptionDeletion &&
                enforceAttributes == that.enforceAttributes &&
                forbidNonModeledAttributes == that.forbidNonModeledAttributes &&
                enforceInboxMessagesInput == that.enforceInboxMessagesInput &&
                forbidNonModeledInboxMessages == that.forbidNonModeledInboxMessages &&
                enforceOutboxMessages == that.enforceOutboxMessages &&
                forbidNonModeledOutboxMessages == that.forbidNonModeledOutboxMessages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforceThingDescriptionModification, forbidThingDescriptionDeletion, enforceAttributes,
                forbidNonModeledAttributes, enforceInboxMessagesInput, enforceInboxMessagesOutput,
                forbidNonModeledInboxMessages, enforceOutboxMessages, forbidNonModeledOutboxMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforceThingDescriptionModification=" + enforceThingDescriptionModification +
                ", forbidThingDescriptionDeletion=" + forbidThingDescriptionDeletion +
                ", enforceAttributes=" + enforceAttributes +
                ", forbidNonModeledAttributes=" + forbidNonModeledAttributes +
                ", enforceInboxMessagesInput=" + enforceInboxMessagesInput +
                ", enforceInboxMessagesOutput=" + enforceInboxMessagesOutput +
                ", forbidNonModeledInboxMessages=" + forbidNonModeledInboxMessages +
                ", enforceOutboxMessages=" + enforceOutboxMessages +
                ", forbidNonModeledOutboxMessages=" + forbidNonModeledOutboxMessages +
                "]";
    }
}
