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
package org.eclipse.ditto.services.utils.persistence.mongo.ops;

import java.util.List;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Persistence ops on namespaces.
 * <p>
 * Currently the only supported operation is 'purge'.
 * </p>
 */
public interface NamespaceOps {

    /**
     * Purge a namespace from some persistent storage.
     *
     * @param namespace a namespace.
     * @return source of any error during the purge.
     */
    Source<List<Throwable>, NotUsed> purge(CharSequence namespace);
}
