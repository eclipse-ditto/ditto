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
package org.eclipse.ditto.concierge.service.starter.actors;

import java.util.Optional;

import org.eclipse.ditto.base.service.actors.AbstractDittoRootActorTest;
import org.eclipse.ditto.concierge.service.common.ConciergeConfig;
import org.eclipse.ditto.concierge.service.common.DittoConciergeConfig;
import org.eclipse.ditto.concierge.service.starter.proxy.DefaultEnforcerActorFactory;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;
import akka.actor.Props;

public final class ConciergeRootActorTest extends AbstractDittoRootActorTest {

    @Override
    protected String serviceName() {
        return "concierge";
    }

    @Override
    protected Optional<String> getRootActorName() {
        return Optional.of(ConciergeRootActor.ACTOR_NAME);
    }

    @Override
    protected Props getRootActorProps(final ActorSystem system) {
        final ConciergeConfig config =
                DittoConciergeConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()));
        return ConciergeRootActor.props(config, system.deadLetters(), new DefaultEnforcerActorFactory());
    }

}

