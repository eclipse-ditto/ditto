/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.config;

import java.util.*;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link FieldsEncryptionConfig}.
 */
@Immutable
public class DefaultFieldsEncryptionConfig implements FieldsEncryptionConfig {

    private static final String CONFIG_PATH = "encryption";
    private final boolean enabled;
    private final String symmetricalKey;
    private final List<String> jsonPointers;


    private DefaultFieldsEncryptionConfig(final ConfigWithFallback config) {
        this.enabled = config.getBoolean(ConfigValue.ENABLED.getConfigPath());
        this.symmetricalKey = config.getString(ConfigValue.SYMMETRICAL_KEY.getConfigPath());
        this.jsonPointers = Collections.unmodifiableList(
                new ArrayList<>(config.getStringList(ConfigValue.JSON_POINTERS.getConfigPath())));
    }

    public static DefaultFieldsEncryptionConfig of(final Config config) {
        final var fieldEncryptionConfig =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, FieldsEncryptionConfig.ConfigValue.values());

        return new DefaultFieldsEncryptionConfig(fieldEncryptionConfig);
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public String getSymmetricalKey() {
        return this.symmetricalKey;
    }

    @Override
    public Collection<String> getJsonPointers() {
        return Collections.unmodifiableList(new ArrayList<>(this.jsonPointers));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultFieldsEncryptionConfig that = (DefaultFieldsEncryptionConfig) o;
        return enabled == that.enabled &&
                Objects.equals(symmetricalKey, that.symmetricalKey) &&
                Objects.equals(jsonPointers, that.jsonPointers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, symmetricalKey, jsonPointers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "enabled=" + enabled +
                ", symmetricalKey='" + symmetricalKey + '\'' +
                ", jsonPointers=" + jsonPointers +
                ']';
    }
}
