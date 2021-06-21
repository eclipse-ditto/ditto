/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

/**
 * Interface providing functionality to decode a config entry to a {@code Map<String, String>}.
 */
public interface WithStringMapDecoding {

    /**
     * Decode the value at {@code path} in {@code config} to a {@link Map}.
     *
     * @param config the config.
     * @param path the path at which the Map should be located.
     * @return the {@link Map} containing the values.
     * @throws DittoConfigError if the value at {@code path} was missing or no Map from string to string.
     */
    default Map<String, String> asStringMap(final ScopedConfig config, final String path) {
        try {
            final Config stringMapConfig = config.getConfig(path);
            final Map<String, String> map = stringMapConfig.root().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> (String) entry.getValue().unwrapped()));
            return Collections.unmodifiableMap(map);
        } catch (final ClassCastException | ConfigException e) {
            final String errorMessage =
                    MessageFormat.format("Key <{0}> in config <{1}> must contain a map from string to string",
                            path, config.getConfigPath());
            throw new DittoConfigError(errorMessage);
        }
    }

}
