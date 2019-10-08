/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
public final class DefaultMessageMapperConfiguration implements MessageMapperConfiguration {

    private final String id;
    private final Map<String, String> properties;

    private DefaultMessageMapperConfiguration(final String id, final Map<String, String> properties) {
        this.id = id;
        this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
    }

    /**
     * Constructs a new {@code DefaultMessageMapperConfiguration} of the given map.
     *
     * @param id the id of the mapper
     * @param configuration the map holding configuration properties.
     * @return the instance.
     * @throws NullPointerException if {@code configuration} is {@code null}.
     */
    public static DefaultMessageMapperConfiguration of(final String id, final Map<String, String> configuration) {
        checkNotNull(id, "id");
        checkNotNull(configuration, "configuration properties");
        return new DefaultMessageMapperConfiguration(id, configuration);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultMessageMapperConfiguration that = (DefaultMessageMapperConfiguration) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, properties);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "id=" + id +
                ", properties=" + properties +
                "]";
    }
}
