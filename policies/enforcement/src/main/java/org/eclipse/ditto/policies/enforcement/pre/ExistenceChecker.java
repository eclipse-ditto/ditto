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
package org.eclipse.ditto.policies.enforcement.pre;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.DittoExtensionPoint;

import akka.actor.ActorSystem;

/**
 * Checks existence of a signals' entity, in effort to validate if the entity already exists.
 */
public interface ExistenceChecker extends DittoExtensionPoint {

    /**
     * Checks the existence of the entity identified by the passed {@code signal}.
     *
     * @param signal the signal from which to get the entity ID to check for from.
     * @return whether the entity exists.
     */
    CompletionStage<Boolean> checkExistence(Signal<?> signal);

    /**
     * Loads the implementation of {@code ExistenceChecker} which is configured for the {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code ExistenceChecker} should be loaded.
     * @return the {@code ExistenceChecker} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static ExistenceChecker get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<ExistenceChecker> {

        private static final String CONFIG_PATH = "ditto.existence-checker";
        private static final ExistenceChecker.ExtensionId INSTANCE = new ExtensionId(ExistenceChecker.class);

        private ExtensionId(final Class<ExistenceChecker> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }
}
