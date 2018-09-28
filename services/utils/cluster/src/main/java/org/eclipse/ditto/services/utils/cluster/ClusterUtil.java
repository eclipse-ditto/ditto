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
package org.eclipse.ditto.services.utils.cluster;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;

/**
 * Convenience methods to operate an Akka cluster.
 */
public final class ClusterUtil {

    /**
     * Start a cluster singleton actor.
     *
     * @param context context of the actor whose child the cluster singleton manager shall be.
     * @param role role of this cluster member.
     * @param actorName name of the singleton actor.
     * @param props Props of the singleton actor.
     * @return reference of the singleton actor.
     */
    public static ActorRef startSingleton(final ActorContext context,
            final String role,
            final String actorName,
            final Props props) {

        final ClusterSingletonManagerSettings settings =
                ClusterSingletonManagerSettings.create(context.system()).withRole(role);

        return context.actorOf(ClusterSingletonManager.props(props, PoisonPill.getInstance(), settings), actorName);
    }
}
