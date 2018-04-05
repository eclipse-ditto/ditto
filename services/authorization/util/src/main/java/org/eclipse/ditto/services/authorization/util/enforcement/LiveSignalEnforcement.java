/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.authorization.util.enforcement;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

import akka.actor.ActorRef;

/**
 * Enforces live commands and live events.
 */
public final class LiveSignalEnforcement extends Enforcement<Signal> {

    private final EnforcerRetriever enforcerRetriever;

    private LiveSignalEnforcement(final Context context, final Cache<EntityId, Entry<EntityId>> thingIdCache,
            final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
            final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache) {

        super(context);
        requireNonNull(thingIdCache);
        requireNonNull(policyEnforcerCache);
        requireNonNull(aclEnforcerCache);
        enforcerRetriever =
                PolicyOrAclEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache, aclEnforcerCache);
    }

    /**
     * {@link EnforcementProvider} for {@link LiveSignalEnforcement}.
     */
    public static final class Provider implements EnforcementProvider<Signal> {

        private final Cache<EntityId, Entry<EntityId>> thingIdCache;
        private final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache;
        private final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache;

        /**
         * Constructor.
         *
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param aclEnforcerCache the acl-enforcer cache.
         */
        public Provider(final Cache<EntityId, Entry<EntityId>> thingIdCache,
                final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
                final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache) {

            this.thingIdCache = requireNonNull(thingIdCache);
            this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
            this.aclEnforcerCache = requireNonNull(aclEnforcerCache);
        }

        @Override
        public Class<Signal> getCommandClass() {
            return Signal.class;
        }

        @Override
        public boolean isApplicable(final Signal signal) {
            return isLiveSignal(signal);
        }

        @Override
        public Enforcement<Signal> createEnforcement(final Context context) {
            return new LiveSignalEnforcement(context, thingIdCache, policyEnforcerCache, aclEnforcerCache);
        }
    }

    @Override
    public void enforce(final Signal signal, final ActorRef sender) {
        enforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            if (enforcerEntry.exists()) {
                final Signal<?> generifiedSignal = (Signal) signal;
                final Signal<?> signalWithReadSubjects =
                        addReadSubjectsToSignal(generifiedSignal, enforcerEntry.getValue());
                replyToSender(signalWithReadSubjects, sender);
            } else {
                // drop live command to nonexistent things and respond with error.
                final ThingNotAccessibleException error = ThingNotAccessibleException.newBuilder(entityId().getId())
                        .dittoHeaders(signal.getDittoHeaders())
                        .build();
                replyToSender(error, sender);
            }
        });
    }

    /**
     * Tests whether a signal is applicable for live signal enforcement.
     *
     * @param signal the signal to test.
     * @return whether the signal belongs to the live channel.
     */
    public static boolean isLiveSignal(final Signal signal) {
        return !(signal instanceof MessageCommand) &&
                signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }
}
