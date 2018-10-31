/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Persistence operations on the level of namespaces.
 * <p>
 * Currently the only supported operation is 'purge'.
 * </p>
 * <p>
 * Implementations specific to a persistent storage decide what descriptor of a namespace to accept. For example, an
 * implementation for MongoDB-based event journals may accept a MongoDB-collection-name with a filter. An implementation
 * for a search index may accept the namespace itself as a string.
 * </p>
 *
 * @param <S> descriptor of a namespace. Implementations decide which type of descriptors to support.
 */
public interface NamespaceOps<S> {

    /**
     * Purge a namespace from some persistent storage.
     *
     * @param namespaceDescriptor storage-specific identifier to describe a namespace.
     * @return source of any error during the purge.
     */
    Source<Optional<Throwable>, NotUsed> purge(S namespaceDescriptor);

    /**
     * Purge namespaces from some persistent storage.
     *
     * @param namespaceDescriptors storage-specific identifiers to describe namespaces.
     * @return source of any errors during the purge.
     */
    default Source<List<Throwable>, NotUsed> purgeAll(final Collection<S> namespaceDescriptors) {
        return Source.from(namespaceDescriptors)
                .flatMapConcat(this::purge)
                .grouped(namespaceDescriptors.size())
                .map(errors -> errors.stream()
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()));
    }

}
