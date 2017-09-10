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
 * An enumeration of ReadConsistencies supported by {@link CacheFacadeActor}.
 */
public enum ReadConsistency implements Consistency<Replicator.ReadConsistency> {

    /**
     * Replicator.ReadLocal$ read consistency.
     */
    LOCAL("local", Replicator.readLocal()),

    /**
     * Replicator.ReadMajority$ read consistency.
     */
    MAJORITY("majority", new Replicator.ReadMajority(FiniteDuration.apply(5, TimeUnit.SECONDS))),

    /**
     * Replicator.ReadAll$ read consistency.
     */
    ALL("all", new Replicator.ReadAll(FiniteDuration.apply(5, TimeUnit.SECONDS)));

    private final String name;
    private final Replicator.ReadConsistency consistency;

    ReadConsistency(final String name, final Replicator.ReadConsistency consistency) {
        this.name = name;
        this.consistency = consistency;
    }

    @Override
    public Replicator.ReadConsistency getReplicatorConsistency() {
        return consistency;
    }

    @Override
    public String toString() {
        return name;
    }

}
