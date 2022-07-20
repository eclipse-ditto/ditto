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
package org.eclipse.ditto.things.service.signaltransformation.placeholdersubstitution;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution.AbstractPlaceholderSubstitution;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Things specific SignalTransformer which applies substitution of placeholders on a thing command
 * (subtype of {@link org.eclipse.ditto.base.model.signals.Signal}) based on its {@link DittoHeaders}.
 * <p>
 * May be subclassed in order to provide additional/different {@link #createReplacementDefinitions()} by overwriting
 * that method.
 */
public class ThingsPlaceholderSubstitution extends AbstractPlaceholderSubstitution {

    /**
     * Constructs a new instance of ThingsPlaceholderSubstitutionPreEnforcer extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    public ThingsPlaceholderSubstitution(final ActorSystem actorSystem, final Config config) {
        super(ThingSubstitutionStrategyRegistry.newInstance());
    }

}
