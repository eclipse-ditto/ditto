/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.ddata.compressed;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptionsUpdate;

/**
 * Updates of compressed DData.
 */
@NotThreadSafe
public final class CompressedUpdate extends AbstractSubscriptionsUpdate<Long, CompressedUpdate> {

    private CompressedUpdate(final Set<Long> inserts, final Set<Long> deletes, final boolean replaceAll) {
        super(inserts, deletes, replaceAll);
    }

    /**
     * @return An empty update.
     */
    public static CompressedUpdate empty() {
        return new CompressedUpdate(new HashSet<>(), new HashSet<>(), false);
    }

    /**
     * Replace everything associated with a subscriber in the distributed data.
     *
     * @param inserts topics to insert.
     * @return an immutable update object.
     */
    public static CompressedUpdate replaceAll(final Set<Long> inserts) {
        final Set<Long> copyOfInserts = Set.copyOf(inserts);
        return new CompressedUpdate(copyOfInserts, Collections.emptySet(), true);
    }

    @Override
    public CompressedUpdate snapshot() {
        return new CompressedUpdate(Set.copyOf(getInserts()), Set.copyOf(getDeletes()), shouldReplaceAll());
    }
}
