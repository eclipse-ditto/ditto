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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Typed map wrapper for {@link MessageMapper} configuration. Offers some convenience to access values.
 */
public class MessageMapperConfiguration implements Map<String, String> {

    private final Map<String, String> configuration;


    private MessageMapperConfiguration(final Map<String, String> configuration) {
        this.configuration = new LinkedHashMap<>(configuration);
    }

    public static MessageMapperConfiguration empty() {
        return from(Collections.emptyMap());
    }

    /**
     * Constructs a new {@link MessageMapperConfiguration} from the given map.
     * @param configuration the map holding configuration properties
     */
    public static MessageMapperConfiguration from(final Map<String, String> configuration) {
        return new MessageMapperConfiguration(configuration);
    }

    public Optional<String> findProperty(final String property) {
        return Optional.ofNullable(configuration.get(property)).filter(s -> !s.isEmpty());
    }

    public String getProperty(final String property) {
        return findProperty(property).orElseThrow(
                () -> new IllegalArgumentException("Missing configuration property: " + property));
    }

    @Override
    public int size() {
        return configuration.size();
    }

    @Override
    public boolean isEmpty() {
        return configuration.isEmpty();
    }

    @Override
    public boolean containsKey(final Object key) {
        return configuration.containsKey(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        return configuration.containsValue(value);
    }

    @Override
    public String get(final Object key) {
        return configuration.get(key);
    }

    @Override
    public String put(final String key, final String value) {
        return configuration.put(key, value);
    }

    @Override
    public String remove(final Object key) {
        return configuration.remove(key);
    }

    @Override
    public void putAll(final Map<? extends String, ? extends String> m) {
        configuration.putAll(m);
    }

    @Override
    public void clear() {
        configuration.clear();
    }

    @Override
    public Set<String> keySet() {
        return configuration.keySet();
    }

    @Override
    public Collection<String> values() {
        return configuration.values();
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return configuration.entrySet();
    }
}
