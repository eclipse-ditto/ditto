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

import java.util.HashSet;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptionsUpdate;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataUpdate;

/**
 * Updates of uncompressed DData.
 */
@NotThreadSafe
public final class LiteralUpdate extends AbstractSubscriptionsUpdate<String, LiteralUpdate>
        implements DDataUpdate<String> {

    private LiteralUpdate(final Set<String> inserts) {
        super(inserts);
    }

    /**
     * @return An empty update.
     */
    public static LiteralUpdate empty() {
        return new LiteralUpdate(new HashSet<>());
    }

    /**
     * Replace everything associated with a subscriber in the distributed data.
     *
     * @param inserts topics to insert.
     * @return an immutable update object.
     */
    public static LiteralUpdate replaceAll(final Set<String> inserts) {
        final Set<String> copyOfInserts = Set.copyOf(inserts);
        return new LiteralUpdate(copyOfInserts);
    }

    @Override
    public LiteralUpdate snapshot() {
        return new LiteralUpdate(Set.copyOf(getInserts()));
    }
}
