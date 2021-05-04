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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.policies.service.persistence.actors.resolvers.SubjectIdFromActionResolver;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.policies.model.signals.commands.actions.PolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.events.PolicyActionEvent;

import akka.actor.ActorSystem;

/**
 * Abstract base class for {@link PolicyActionCommand} strategies.
 *
 * @param <C> the type of the handled command
 */
abstract class AbstractPolicyActionCommandStrategy<C extends PolicyActionCommand<C>>
        extends AbstractPolicyCommandStrategy<C, PolicyActionEvent<?>> {

    protected final SubjectIdFromActionResolver subjectIdFromActionResolver;

    AbstractPolicyActionCommandStrategy(final Class<C> theMatchingClass,
            final PolicyConfig policyConfig,
            final ActorSystem system) {
        super(theMatchingClass, policyConfig);
        this.subjectIdFromActionResolver = AkkaClassLoader.instantiate(system, SubjectIdFromActionResolver.class,
                policyConfig.getSubjectIdResolver());
    }

}
