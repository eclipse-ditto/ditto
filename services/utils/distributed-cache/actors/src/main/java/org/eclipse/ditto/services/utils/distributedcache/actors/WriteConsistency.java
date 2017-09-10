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
package org.eclipse.ditto.services.utils.distributedcache.actors;

import java.util.concurrent.TimeUnit;

import akka.cluster.ddata.Replicator;
import scala.concurrent.duration.FiniteDuration;

/**
 * An enumeration of write consistencies supported by {@link CacheFacadeActor}.
 */
public enum WriteConsistency implements Consistency<Replicator.WriteConsistency> {

    /**
     * Replicator.WriteLocal$ read consistency. With this consistency replication is delayed which means that several
     * changes are collected during a 500 ms interval until flashing.
     */
    LOCAL("local", Replicator.writeLocal()),

    /**
     * Replicator.WriteMajority$ read consistency. With this consistency the majority of nodes need to acknowledge they
     * got the change before processing further.
     */
    MAJORITY("majority", new Replicator.WriteMajority(FiniteDuration.apply(1, TimeUnit.SECONDS)));

    private final String name;
    private final Replicator.WriteConsistency consistency;

    WriteConsistency(final String name, final Replicator.WriteConsistency consistency) {
        this.name = name;
        this.consistency = consistency;
    }

    @Override
    public Replicator.WriteConsistency getReplicatorConsistency() {
        return consistency;
    }

    @Override
    public String toString() {
        return name;
    }

}
