/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.utils.pubsub.ddata.literal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptionsUpdate;

/**
 * Updates of uncompressed DData.
 */
@NotThreadSafe
public final class LiteralUpdate extends AbstractSubscriptionsUpdate<String, LiteralUpdate> {

    private LiteralUpdate(final Set<String> inserts, final Set<String> deletes, final boolean replaceAll) {
        super(inserts, deletes, replaceAll);
    }

    /**
     * @return An empty update.
     */
    public static LiteralUpdate empty() {
        return new LiteralUpdate(new HashSet<>(), new HashSet<>(), false);
    }

    /**
     * Replace everything associated with a subscriber in the distributed data.
     *
     * @param inserts topics to insert.
     * @return an immutable update object.
     */
    public static LiteralUpdate replaceAll(final Set<String> inserts) {
        final Set<String> copyOfInserts = Set.copyOf(inserts);
        return new LiteralUpdate(copyOfInserts, Collections.emptySet(), true);
    }

    @Override
    public LiteralUpdate snapshot() {
        return new LiteralUpdate(Set.copyOf(getInserts()), Set.copyOf(getDeletes()), shouldReplaceAll());
    }
}
