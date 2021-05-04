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
package org.eclipse.ditto.internal.utils.persistence.operations;

import java.util.List;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Persistence operations on namespaces.
 * <p>
 * Currently the only supported operation is 'purge'.
 * </p>
 */
public interface NamespacePersistenceOperations {

    /**
     * Purge a namespace from some persistent storage.
     *
     * @param namespace a namespace.
     * @return source of any error during the purge.
     * @throws NullPointerException if {@code namespace} is {@code null}.
     */
    Source<List<Throwable>, NotUsed> purge(CharSequence namespace);

}
