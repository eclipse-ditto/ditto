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
package org.eclipse.ditto.policies.enforcement;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Provides a singleton instance of a {@link org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider}.
 */
public class PolicyEnforcerProviderExtension implements Extension {

    private final PolicyEnforcerProvider policyEnforcerProvider;

    private PolicyEnforcerProviderExtension(final ActorSystem actorSystem) {
        final boolean withCaching = actorSystem.settings().config().getBoolean(
                PolicyEnforcerProvider.ENFORCER_CACHE_CONFIG_KEY + ".enabled");
        if (withCaching) {
            policyEnforcerProvider = new CachingPolicyEnforcerProvider(actorSystem);
        } else {
            policyEnforcerProvider = new DefaultPolicyEnforcerProvider(actorSystem);
        }
    }

    /**
     * @return the {@link PolicyEnforcerProvider} instance
     */
    public PolicyEnforcerProvider getPolicyEnforcerProvider() {
        return policyEnforcerProvider;
    }

    /**
     * Load the {@code PolicyEnforcerProviderExtension}.
     *
     * @param actorSystem The actor system in which to load the provider.
     * @return the {@link PolicyEnforcerProviderExtension}.
     */
    public static PolicyEnforcerProviderExtension get(final ActorSystem actorSystem) {
        return PolicyEnforcerProviderExtension.ExtensionId.INSTANCE.get(actorSystem);
    }

    /**
     * ID of the actor system extension for a {@code PolicyEnforcerProvider}.
     */
    private static final class ExtensionId extends AbstractExtensionId<PolicyEnforcerProviderExtension> {

        private static final PolicyEnforcerProviderExtension.ExtensionId INSTANCE = new PolicyEnforcerProviderExtension.ExtensionId();

        @Override
        public PolicyEnforcerProviderExtension createExtension(final ExtendedActorSystem system) {

            return new PolicyEnforcerProviderExtension(system);
        }
    }
}
