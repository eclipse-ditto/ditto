/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Typed map wrapper for {@link MessageMapper} configuration. Offers some convenience to access values.
 *
 * TODO TJ why wrap the configuration map - seems over engineered
 */
@Immutable
public final class DefaultMessageMapperOptions implements PayloadMapperOptions {

    private final Map<String, String> configuration;


    private DefaultMessageMapperOptions(final Map<String, String> configuration) {
        // TODO null check
        this.configuration = Collections.unmodifiableMap(new HashMap<>(configuration));
    }

    /**
     *
     * @return
     */
    public static DefaultMessageMapperOptions empty() {
        return from(Collections.emptyMap());
    }

    /**
     * Constructs a new {@link DefaultMessageMapperOptions} from the given map.
     * @param configuration the map holding configuration properties
     */
    public static DefaultMessageMapperOptions from(final Map<String, String> configuration) {
        return new DefaultMessageMapperOptions(configuration);
    }

    public Optional<String> findProperty(final String property) {
        return Optional.ofNullable(configuration.get(property)).filter(s -> !s.isEmpty());
    }

    @Override
    public Map<String, String> getAsMap() {
        return configuration;
    }

    @Override
    public Optional<String> getContentType() {
        return Optional.empty();
    }
}
