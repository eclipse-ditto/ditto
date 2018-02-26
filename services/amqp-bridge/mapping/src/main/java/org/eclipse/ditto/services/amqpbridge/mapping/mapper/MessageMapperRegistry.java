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

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;

import com.google.common.base.Converter;

/**
 * A registry for instantiated mappers.
 *
 * TODO extract interface with public methods and implement this class as "Default"
 */
public final class MessageMapperRegistry {

    private final Map<String, MessageMapper> registry;

    private final MessageMapper defaultMapper;


    private MessageMapperRegistry(final MessageMapper defaultMapper, final Map<String, MessageMapper> registry) {
        this.defaultMapper = defaultMapper; // TODO null check
        this.registry = Collections.unmodifiableMap(new HashMap<>(registry));  // TODO null check
    }

    /**
     * Constructs a mapper with the given params.
     * @param defaultMapper the default mapper
     * @param mappers the mappers
     */
    public static MessageMapperRegistry of(final MessageMapper defaultMapper, final MessageMapper... mappers) {
        this(defaultMapper);
        addAll(mappers);
    }

    /**
     * Returns the default mapper if present
     * @return the default mapper or null
     */
    public MessageMapper getDefaultMapper() {
        return defaultMapper;
    }

    /**
     *
     * @param message
     * @return
     */
    public Optional<MessageMapper> findMapper(final ExternalMessage message) {
        return MessageMapper.findContentType(message).map(registry::get);
    }

    /**
     * Selects a mapper for this message. If no explicit mapper is found for the message and a default mapper is
     * present. The default mapper will be returned.
     *
     * @param message the message
     * @return the selected mapper
     */
    public Optional<MessageMapper> selectMapper(final ExternalMessage message) {
        Optional<MessageMapper> mapper = findMapper(message);
        return mapper.isPresent() ? mapper : Optional.ofNullable(getDefaultMapper());
    }

    private Optional<Converter<Adaptable, ExternalMessage>> findMapper(final Adaptable adaptable) {
        return MessageMapper.findContentType(adaptable).map(registry::get).map(MessageMapper::reverse);
    }

    /**
     * Selects a mapper for this adaptable. If no explicit mapper is found for the adaptable and a default mapper is
     * present. The default mapper will be returned.
     *
     * @param adaptable the adaptable
     * @return the selected mapper
     */
    public Optional<Converter<Adaptable, ExternalMessage>> selectMapper(final Adaptable adaptable) {
        Optional<Converter<Adaptable, ExternalMessage>> mapper = findMapper(adaptable);
        return mapper.isPresent() ? mapper : Optional.ofNullable(getDefaultMapper()).map(MessageMapper::reverse);
    }

}
