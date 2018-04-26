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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Default implementation for a message mapper configuration.
 */
@Immutable
public class DefaultMessageMapperConfiguration implements MessageMapperConfiguration {

    private final Map<String, String> properties;

    protected DefaultMessageMapperConfiguration(final Map<String, String> properties) {
        checkNotNull(properties);
        this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Constructs a new {@link DefaultMessageMapperConfiguration} of the given map.
     * @param configuration the map holding configuration properties
     */
    public static DefaultMessageMapperConfiguration of(final Map<String, String> configuration) {
        return new DefaultMessageMapperConfiguration(configuration);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final DefaultMessageMapperConfiguration that = (DefaultMessageMapperConfiguration) o;
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "properties=" + properties +
                "]";
    }
}
