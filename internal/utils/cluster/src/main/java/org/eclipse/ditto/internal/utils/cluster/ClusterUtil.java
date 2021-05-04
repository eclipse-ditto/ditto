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
package org.eclipse.ditto.internal.utils.cluster;

import javax.annotation.concurrent.Immutable;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;

/**
 * Convenience methods to operate an Akka cluster.
 */
@Immutable
public final class ClusterUtil {

    private ClusterUtil() {
        throw new AssertionError();
    }

    /**
     * Start a proxy to a singleton actor.
     *
     * @param context context to create the proxy actor in.
     * @param role role of the singleton actor.
     * @param singleton actor reference of the singleton manager.
     * @return actor reference of the proxy to the singleton actor.
     */
    public static ActorRef startSingletonProxy(final ActorContext context, final String role,
            final ActorRef singleton) {

        final ClusterSingletonProxySettings settings =
                ClusterSingletonProxySettings.create(context.system()).withRole(role);

        final Props proxyProps = ClusterSingletonProxy.props(singleton.path().toStringWithoutAddress(), settings);
        return context.actorOf(proxyProps, singleton.path().name() + "Proxy");
    }

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

        return startSingleton(context.system(), context, role, actorName, props);
    }

    /**
     * Start a cluster singleton actor.
     *
     * @param context context of the actor whose child the cluster singleton manager shall be.
     * @param role role of this cluster member.
     * @param actorName name of the singleton actor.
     * @param props Props of the singleton actor.
     * @param supervisorStrategy the {@link SupervisorStrategy} for the singleton actor.
     * @return reference of the singleton actor.
     */
    public static ActorRef startSingleton(final ActorContext context,
            final String role,
            final String actorName,
            final Props props,
            final SupervisorStrategy supervisorStrategy) {

        return startSingleton(context.system(), context, role, actorName, props, supervisorStrategy);
    }

    /**
     * Start a cluster singleton actor.
     *
     * @param system the actor system.
     * @param actorRefFactory where the cluster singleton should be created.
     * @param role role of this cluster member.
     * @param actorName name of the singleton actor.
     * @param props Props of the singleton actor.
     * @return reference of the singleton actor.
     */
    public static ActorRef startSingleton(final ActorSystem system,
            final ActorRefFactory actorRefFactory,
            final String role,
            final String actorName,
            final Props props) {

        final ClusterSingletonManagerSettings settings =
                ClusterSingletonManagerSettings.create(system).withRole(role);

        final Props supervisorProps = ClusterSingletonSupervisorActor.props(props);
        final Props singletonManagerProps =
                ClusterSingletonManager.props(supervisorProps, PoisonPill.getInstance(), settings);
        return actorRefFactory.actorOf(singletonManagerProps, actorName);
    }

    /**
     * Start a cluster singleton actor.
     *
     * @param system the actor system.
     * @param actorRefFactory where the cluster singleton should be created.
     * @param role role of this cluster member.
     * @param actorName name of the singleton actor.
     * @param props Props of the singleton actor.
     * @param supervisorStrategy the {@link SupervisorStrategy} for the singleton actor.
     * @return reference of the singleton actor.
     */
    public static ActorRef startSingleton(final ActorSystem system,
            final ActorRefFactory actorRefFactory,
            final String role,
            final String actorName,
            final Props props,
            final SupervisorStrategy supervisorStrategy) {

        final ClusterSingletonManagerSettings settings =
                ClusterSingletonManagerSettings.create(system).withRole(role);

        final Props supervisorProps = ClusterSingletonSupervisorActor.props(props, supervisorStrategy);
        final Props singletonManagerProps =
                ClusterSingletonManager.props(supervisorProps, PoisonPill.getInstance(), settings);
        return actorRefFactory.actorOf(singletonManagerProps, actorName);
    }

}
