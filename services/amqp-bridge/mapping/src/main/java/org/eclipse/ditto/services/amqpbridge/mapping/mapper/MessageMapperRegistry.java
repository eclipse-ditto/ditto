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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;

import com.google.common.base.Converter;

/**
 * A registry for instantiated mappers.
 */
public class MessageMapperRegistry implements Collection<MessageMapper> {

    private final Map<String, MessageMapper> registry;

    @Nullable
    private MessageMapper defaultMapper;


    private MessageMapperRegistry(@Nullable final MessageMapper defaultMapper) {
        registry = new HashMap<>();
        setDefaultMapper(defaultMapper);
    }

    /**
     * Constructs a mapper with the given params.
     * @param defaultMapper the default mapper
     * @param mappers the mappers
     */
    MessageMapperRegistry(@Nullable final MessageMapper defaultMapper, final List<MessageMapper> mappers) {
        this(defaultMapper);
        addAll(mappers);
    }

    /**
     * Returns the default mapper if present
     * @return the default mapper or null
     */
    @Nullable
    public MessageMapper getDefaultMapper() {
        return defaultMapper;
    }

    /**
     * Sets a default mapper
     * @param defaultMapper the default mapper
     */
    private void setDefaultMapper(@Nullable final MessageMapper defaultMapper) {
        this.defaultMapper = defaultMapper;
    }

    public Optional<MessageMapper> findMapper(final InternalMessage message) {
        return MessageMapper.findContentType(message).map(registry::get);
    }

    /**
     * Selects a mapper for this message. If no explicit mapper is found for the message and a default mapper is
     * present. The default mapper will be returned.
     *
     * @param message the message
     * @return the selected mapper
     */
    public Optional<MessageMapper> selectMapper(final InternalMessage message) {
        Optional<MessageMapper> mapper = findMapper(message);
        return mapper.isPresent() ? mapper : Optional.ofNullable(getDefaultMapper());
    }

    private Optional<Converter<Adaptable, InternalMessage>> findMapper(final Adaptable adaptable) {
        return MessageMapper.findContentType(adaptable).map(registry::get).map(MessageMapper::reverse);
    }

    /**
     * Selects a mapper for this adaptable. If no explicit mapper is found for the adaptable and a default mapper is
     * present. The default mapper will be returned.
     *
     * @param adaptable the adaptable
     * @return the selected mapper
     */
    public Optional<Converter<Adaptable, InternalMessage>> selectMapper(final Adaptable adaptable) {
        Optional<Converter<Adaptable, InternalMessage>> mapper = findMapper(adaptable);
        return mapper.isPresent() ? mapper : Optional.ofNullable(getDefaultMapper()).map(MessageMapper::reverse);
    }


    @Override
    public int size() {
        return registry.size();
    }

    @Override
    public boolean isEmpty() {
        return registry.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return registry.values().contains(o);
    }

    @Override
    public Iterator<MessageMapper> iterator() {
        return registry.values().iterator();
    }

    @Override
    public Object[] toArray() {
        return registry.values().toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        //noinspection SuspiciousToArrayCall
        return registry.values().toArray(a);
    }

    @Override
    public boolean add(final MessageMapper messageMapper) {
        return !messageMapper.equals(registry.put(messageMapper.getContentType(), messageMapper));
    }

    @Override
    public boolean remove(final Object o) {
        return !MessageMapper.class.isAssignableFrom(o.getClass()) &&
                Objects.nonNull(registry.remove(((MessageMapper) o).getContentType()));
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return registry.values().containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends MessageMapper> c) {
        boolean[] changed = new boolean[1];
        c.forEach(e -> {
            if (this.add(e)) {
                changed[0] = true;
            }
        });
        return changed[0];
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        registry.clear();
    }
}
