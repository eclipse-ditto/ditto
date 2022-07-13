/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.enforcement.pre;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.enforcement.placeholders.AbstractPlaceholderSubstitutionPreEnforcer;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Policies specific Pre-Enforcer which applies substitution of placeholders on a policy command
 * (subtype of {@link org.eclipse.ditto.base.model.signals.Signal}) based on its {@link DittoHeaders}.
 *
 * May be subclassed in order to provide additional/different {@link #createReplacementDefinitions()} by overwriting
 * that method.
 */
public class PoliciesPlaceholderSubstitutionPreEnforcer extends AbstractPlaceholderSubstitutionPreEnforcer {

    /**
     * Constructs a new instance of PoliciesPlaceholderSubstitutionPreEnforcer extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     */
    @SuppressWarnings("unused")
    public PoliciesPlaceholderSubstitutionPreEnforcer(final ActorSystem actorSystem, final Config config) {
        super(PolicySubstitutionStrategyRegistry.newInstance());
    }

}
