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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


import org.eclipse.ditto.model.amqpbridge.InternalMessage;

/**
 * A registry for instatiated mappers.
 */
public class MessageMapperRegistry implements Collection<MessageMapper> {

    private final Map<String, MessageMapper> registry;
    private final MessageMapper defaultMapper;

    public MessageMapperRegistry(final MessageMapper defaultMapper) {
        checkNotNull(defaultMapper);
        registry = new HashMap<>();
        this.defaultMapper = defaultMapper;
    }

    public MessageMapper getDefaultMapper() {
        return defaultMapper;
    }

    public Optional<MessageMapper> findMapper(final InternalMessage message) {
        return MessageMapper.findContentType(message).map(registry::get);
    }

    public MessageMapper getOrDefault(final InternalMessage message) {
        return getOrDefault(message, getDefaultMapper());
    }

    public MessageMapper getOrDefault(final InternalMessage message, final MessageMapper defaultMapper) {
        return findMapper(message).orElse(defaultMapper);
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
