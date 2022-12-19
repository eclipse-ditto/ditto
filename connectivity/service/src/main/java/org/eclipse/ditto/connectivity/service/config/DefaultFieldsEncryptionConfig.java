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
import org.eclipse.ditto.internal.utils.config.DittoConfigError;

/**
 * Default implementation of {@link FieldsEncryptionConfig}.
 */
@Immutable
public final class DefaultFieldsEncryptionConfig implements FieldsEncryptionConfig {

    private static final String CONFIG_PATH = "encryption";
    private final boolean isEncryptionEnabled;
    private final String symmetricalKey;
    private final List<String> jsonPointers;


    private DefaultFieldsEncryptionConfig(final ConfigWithFallback config) {
        this.isEncryptionEnabled = config.getBoolean(ConfigValue.ENCRYPTION_ENABLED.getConfigPath());
        this.symmetricalKey = config.getString(ConfigValue.SYMMETRICAL_KEY.getConfigPath());
        this.jsonPointers = Collections.unmodifiableList(
                new ArrayList<>(config.getStringList(ConfigValue.JSON_POINTERS.getConfigPath())));
        if (isEncryptionEnabled && symmetricalKey.trim().isEmpty()) {
            throw new DittoConfigError("Missing Symmetric key. It is mandatory when encryption is enabled for connections!");
        }
    }

    public static DefaultFieldsEncryptionConfig of(final Config config) {
        final var fieldEncryptionConfig =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, FieldsEncryptionConfig.ConfigValue.values());

        return new DefaultFieldsEncryptionConfig(fieldEncryptionConfig);
    }

    @Override
    public boolean isEncryptionEnabled() {
        return this.isEncryptionEnabled;
    }

    @Override
    public String getSymmetricalKey() {
        return this.symmetricalKey;
    }

    @Override
    public List<String> getJsonPointers() {
        return this.jsonPointers;
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
        return isEncryptionEnabled == that.isEncryptionEnabled &&
                Objects.equals(symmetricalKey, that.symmetricalKey) &&
                Objects.equals(jsonPointers, that.jsonPointers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isEncryptionEnabled, symmetricalKey, jsonPointers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "enabled=" + isEncryptionEnabled +
                ", symmetricalKey='***'" +
                ", jsonPointers=" + jsonPointers +
                ']';
    }

}
