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
package org.eclipse.ditto.services.utils.pubsub.ddata.ack;

import java.util.Set;

import org.eclipse.ditto.services.utils.pubsub.ddata.DDataUpdate;

import akka.japi.Pair;

// TODO javadoc
public final class AckDDataUpdate implements DDataUpdate<Pair<String, Set<String>>> {

    private final Set<Pair<String, Set<String>>> inserts;

    private AckDDataUpdate(final Set<Pair<String, Set<String>>> inserts) {
        this.inserts = inserts;
    }

    // TODO javadoc
    public static AckDDataUpdate of(final Set<Pair<String, Set<String>>> inserts) {
        return new AckDDataUpdate(inserts);
    }

    @Override
    public Set<Pair<String, Set<String>>> getInserts() {
        return inserts;
    }

    @Override
    public Set<Pair<String, Set<String>>> getDeletes() {
        return Set.of();
    }

    @Override
    public boolean shouldReplaceAll() {
        return true;
    }
}
