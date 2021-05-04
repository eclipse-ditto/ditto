/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;

/**
 * Map holding valid resource paths and allows to seek for a given path.
 *
 * @param <T> the resource type
 */
public final class ResourceMap<T> extends HashMap<JsonKey, ResourceMap<T>> {

    private static final JsonKey ONE_LEVEL = JsonKey.of("*");
    private static final JsonKey ANY_LEVEL = JsonKey.of("**");

    private final T resource;

    private ResourceMap(T resource) {
        this.resource = resource;
    }

    /**
     * Instantiates a new ResourceMapBuilder.
     *
     * @param root the root resource
     * @param <T> the resource type
     * @return the new builder
     */
    public static <T> ResourceMapBuilder<T> newBuilder(final T root) {
        return new ResourceMapBuilder<>(root);
    }

    /**
     * Seeks the given path in this ResourceMap.
     *
     * @param path the path that is matched
     * @return the resource at the end of the path
     */
    public Optional<T> seek(Iterator<JsonKey> path) {
        if (path.hasNext()) {
            final JsonKey key = path.next();
            if (containsKey(ONE_LEVEL)) {
                return get(ONE_LEVEL).seek(path);
            } else if (containsKey(ANY_LEVEL)) {
                return Optional.of(get(ANY_LEVEL).getResource());
            } else if (containsKey(key)) {
                return get(key).seek(path);
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(getResource());
    }

    private T getResource() {
        return resource;
    }

    /**
     * Builds a new ResourceMap.
     *
     * @param <T> the result type
     */
    public static class ResourceMapBuilder<T> {

        private final ResourceMap<T> map;

        private ResourceMapBuilder(final T root) {
            map = new ResourceMap<>(root);
        }

        /**
         * Add a resource with any number of child levels.
         *
         * @param resource the resource at this node.
         * @return the resulting ResourceMap
         */
        public ResourceMap<T> addAny(T resource) {
            map.put(ANY_LEVEL, new ResourceMap<>(resource));
            return this.end();
        }

        /**
         * Add a resource with single child level.
         *
         * @param resource the resource at this node.
         * @return the resulting ResourceMap
         */
        public ResourceMap<T> addOne(T resource) {
            map.put(ONE_LEVEL, new ResourceMap<>(resource));
            return this.end();
        }

        /**
         * Add a resource with single child level.
         *
         * @param child the child to be added
         * @return the resulting ResourceMap
         */
        public ResourceMap<T> addOne(ResourceMap<T> child) {
            map.put(ONE_LEVEL, child);
            return this.end();
        }

        /**
         * Add a resource that matches the given JsonFieldDefinition.
         *
         * @param definition the definition that must match the path
         * @param resource the resource at this node.
         * @return the ResourceMapBuilder to add more resources
         */
        public ResourceMapBuilder<T> add(JsonFieldDefinition<?> definition, T resource) {
            return add(definition.getPointer().iterator().next(), resource);
        }

        /**
         * Add a resource that matches the given key.
         *
         * @param key the key that must match the path
         * @param resource the resource at this node.
         * @return the ResourceMapBuilder to add more resources
         */
        public ResourceMapBuilder<T> add(JsonKey key, T resource) {
            map.put(key, new ResourceMap<>(resource));
            return this;
        }

        /**
         * Add a resource that matches the given JsonFieldDefinition.
         *
         * @param definition the definition that must match the path
         * @param child the child to be added
         * @return the ResourceMapBuilder to add more resources
         */
        public ResourceMapBuilder<T> add(JsonFieldDefinition<?> definition, ResourceMap<T> child) {
            return add(definition.getPointer().iterator().next(), child);
        }

        /**
         * Add a resource that matches the given key.
         *
         * @param key the key that must match the path
         * @param child the child to be added
         * @return the ResourceMapBuilder to add more resources
         */
        public ResourceMapBuilder<T> add(JsonKey key, ResourceMap<T> child) {
            map.put(key, child);
            return this;
        }

        /**
         * @return the final ResourceMap
         */
        public ResourceMap<T> end() {
            return map;
        }
    }
}
