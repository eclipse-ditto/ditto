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
package org.eclipse.ditto.gateway.service.starter;

import java.util.Map;

import org.eclipse.ditto.base.service.actors.AbstractDittoRootActorTest;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.GatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Test {@link GatewayRootActor}.
 */
public final class GatewayRootActorTest extends AbstractDittoRootActorTest {

    @Override
    protected String serviceName() {
        return "gateway";
    }

    @Override
    protected Map<String, Object> overrideConfig() {
        return Map.of("ditto.gateway.http.port", 0);
    }

    @Override
    protected Props getRootActorProps(final ActorSystem system) {
        final GatewayConfig config =
                DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()));
        return GatewayRootActor.props(config, system.deadLetters());
    }

}
