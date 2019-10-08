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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The default implementation of a {@link MessageMapperRegistry}.
 */
public final class DefaultMessageMapperRegistry implements MessageMapperRegistry {

    private final MessageMapper defaultMapper;
    private final Map<String, MessageMapper> mappers;

    private DefaultMessageMapperRegistry(final MessageMapper defaultMapper, final Map<String, MessageMapper> mappers) {
        this.defaultMapper = checkNotNull(defaultMapper);
        this.mappers = mappers;
    }

    /**
     * Creates a new instance of {@code DefaultMessageMapperRegistry} of the parameter values.
     *
     * @param defaultMapper the default mapper.
     * @param mappers the list of custom mappers.
     * @return the instance.
     */
    public static DefaultMessageMapperRegistry of(final MessageMapper defaultMapper,
            final Map<String, MessageMapper> mappers) {

        return new DefaultMessageMapperRegistry(defaultMapper, mappers);
    }

    @Override
    public MessageMapper getDefaultMapper() {
        return defaultMapper;
    }

    @Override
    public List<MessageMapper> getMappers(final List<String> ids) {
        return ids.stream().map(mappers::get).collect(Collectors.toList());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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
                "defaultMapper=" + defaultMapper +
                ", mappers=" + mappers +
                "]";
    }

}
