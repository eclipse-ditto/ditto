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

import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

import akka.actor.ActorRef;

/**
 * Enforces live commands and live events.
 */
public final class LiveSignalEnforcement extends Enforcement<Signal> {

    private LiveSignalEnforcement(final Context context) {
        super(context);
    }

    /**
     * {@code EnforcementProvider} for {@code LiveSignalEnforcement}.
     */
    public static final class Provider implements EnforcementProvider<Signal> {

        @Override
        public Class<Signal> getCommandClass() {
            return Signal.class;
        }

        /**
         * Tests whether a signal is applicable for live signal enforcement.
         *
         * @param signal the signal to test.
         * @return whether the signal belongs to the live channel.
         */
        @Override
        public boolean isApplicable(final Signal signal) {
            return !(signal instanceof MessageCommand) &&
                    signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
        }

        @Override
        public Enforcement<Signal> createEnforcement(final Context context) {
            return new LiveSignalEnforcement(context);
        }
    }

    @Override
    public void enforce(final Signal signal, final ActorRef sender) {
        caches().retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
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
}
