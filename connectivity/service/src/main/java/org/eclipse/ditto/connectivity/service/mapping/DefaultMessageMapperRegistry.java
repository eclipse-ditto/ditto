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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.PayloadMapping;

/**
 * The default implementation of a {@link MessageMapperRegistry}.
 */
public final class DefaultMessageMapperRegistry implements MessageMapperRegistry {

    private final MessageMapper defaultMapper;
    private final Map<String, MessageMapper> customMappers;
    private final Map<String, MessageMapper> fallbackMappers;

    private DefaultMessageMapperRegistry(final MessageMapper defaultMapper,
            final Map<String, MessageMapper> customMappers,
            final Map<String, MessageMapper> fallbackMappers) {
        this.defaultMapper = checkNotNull(defaultMapper);
        this.customMappers = customMappers;
        this.fallbackMappers = fallbackMappers;
    }

    /**
     * Creates a new instance of {@code DefaultMessageMapperRegistry} of the parameter values.
     *
     * @param defaultMapper the default mapper.
     * @param customMappers the list of custom mappers.
     * @param fallbackMappers fallback mappers.
     * @return the instance.
     */
    public static DefaultMessageMapperRegistry of(final MessageMapper defaultMapper,
            final Map<String, MessageMapper> customMappers,
            final Map<String, MessageMapper> fallbackMappers) {

        return new DefaultMessageMapperRegistry(defaultMapper, customMappers, fallbackMappers);
    }

    @Override
    public MessageMapper getDefaultMapper() {
        return defaultMapper;
    }

    @Override
    public List<MessageMapper> getMappers(final PayloadMapping payloadMapping) {
        return payloadMapping.getMappings().stream()
                .map(this::resolveMessageMapper)
                .map(resolvedMapper -> null == resolvedMapper ? defaultMapper : resolvedMapper)
                .toList();
    }

    @Nullable
    private MessageMapper resolveMessageMapper(final String mapper) {
        // first try to find a custom mapper with the given id
        final MessageMapper customMapper = customMappers.get(mapper);
        if (customMapper != null) {
            return customMapper;
        } else {
            // if no custom mapper is found try to find a fallback mapper with the given id
            final MessageMapper fallbackMapper = fallbackMappers.get(mapper);
            if (fallbackMapper != null) {
                return fallbackMapper;
            } else {
                // if no fallback mapper is found use the default mapper
                return defaultMapper;
            }
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultMessageMapperRegistry that = (DefaultMessageMapperRegistry) o;
        return Objects.equals(defaultMapper, that.defaultMapper) &&
                Objects.equals(customMappers, that.customMappers) &&
                Objects.equals(fallbackMappers, that.fallbackMappers);
    }

    @Override
    public void validatePayloadMapping(final PayloadMapping payloadMapping) {
        payloadMapping.getMappings().forEach(this::validateMessageMapper);
    }

    private void validateMessageMapper(final String mapper) {
        @Nullable MessageMapper resolvedMapper = customMappers.get(mapper);
        if (null == resolvedMapper) {
            resolvedMapper = fallbackMappers.get(mapper);
        }

        if (null == resolvedMapper) {
            throw ConnectionConfigurationInvalidException
                    .newBuilder("The mapper <" + mapper + "> could not be loaded.")
                    .description(MessageFormat.format(
                            "Make sure to only use either the specified mappingDefinitions names {0} or fallback mapper names {1}.",
                            customMappers.keySet(), fallbackMappers.keySet()))
                    .build();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultMapper, customMappers, fallbackMappers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "defaultMapper=" + defaultMapper +
                ", customMappers=" + customMappers +
                ", fallbackMappers=" + fallbackMappers +
                "]";
    }
}
