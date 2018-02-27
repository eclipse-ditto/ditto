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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The default implementation of a {@link MessageMapperRegistry}.
 */
public final class DefaultMessageMapperRegistry implements MessageMapperRegistry {

    private final Map<String, MessageMapper> mappers;
    private final MessageMapper defaultMapper;

    private DefaultMessageMapperRegistry(final MessageMapper defaultMapper, final Collection<MessageMapper> mappers) {
        this.defaultMapper = checkNotNull(defaultMapper);
        this.mappers = Collections.unmodifiableMap(
                mappers.stream()
                        .filter(m -> !m.getContentType().isPresent())
                        .collect(Collectors.toMap(m -> m.getContentType().orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Mappers contains a mapper without content type: " + m)),
                                m -> m))
        );
    }

    @Override
    public Collection<MessageMapper> getMappers() {
        return Collections.unmodifiableSet(new HashSet<>(mappers.values()));
    }

    @Override
    public MessageMapper getDefaultMapper() {
        return defaultMapper;
    }

    @Override
    public Optional<MessageMapper> findMapper(final String contentType) {
        return Optional.ofNullable(mappers.get(contentType));
    }

    /**
     * Creates a new registry of the parameter values.
     *
     * @param defaultMapper the default mapper
     * @param mappers the mappers
     */
    public static DefaultMessageMapperRegistry of(final MessageMapper defaultMapper,
            final Collection<MessageMapper> mappers) {
        return new DefaultMessageMapperRegistry(defaultMapper, mappers);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultMessageMapperRegistry that = (DefaultMessageMapperRegistry) o;
        return Objects.equals(mappers, that.mappers) &&
                Objects.equals(defaultMapper, that.defaultMapper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mappers, defaultMapper);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mappers=" + mappers +
                ", defaultMapper=" + defaultMapper +
                "]";
    }
}
