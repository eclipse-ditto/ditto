/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service;

import org.eclipse.ditto.base.service.actors.AbstractDittoRootActorTest;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link ConnectivityRootActor}.
 */
public final class ConnectivityRootActorTest extends AbstractDittoRootActorTest {

    @Override
    protected String serviceName() {
        return "connectivity";
    }

    @Override
    protected Props getRootActorProps(final ActorSystem system) {
        final ConnectivityConfig config =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()));
        return ConnectivityRootActor.props(config, system.deadLetters());
    }

}
