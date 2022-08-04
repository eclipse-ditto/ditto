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
package org.eclipse.ditto.policies.service.starter;

import org.eclipse.ditto.base.service.actors.AbstractDittoRootActorTest;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.policies.service.common.config.DittoPoliciesConfig;
import org.eclipse.ditto.policies.service.common.config.PoliciesConfig;

import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link org.eclipse.ditto.policies.service.starter.PoliciesRootActor}.
 */
public final class PoliciesRootActorTest extends AbstractDittoRootActorTest {

    @Override
    protected String serviceName() {
        return "policies";
    }

    @Override
    protected Props getRootActorProps(final ActorSystem system) {
        final PoliciesConfig config =
                DittoPoliciesConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()));

        return PoliciesRootActor.props(config, system.deadLetters());
    }
}
