/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.concierge.service.enforcement;

import java.util.Optional;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;

import akka.NotUsed;
import akka.stream.javadsl.Flow;

/**
 * Provider interface for {@link AbstractEnforcement}.
 *
 * @param <T> the type of commands which are enforced.
 */
public interface EnforcementProvider<T extends Signal<?>> {

    /**
     * The base class of the commands to which this enforcement applies.
     *
     * @return the command class.
     */
    Class<T> getCommandClass();

    /**
     * Test whether this enforcement provider is applicable for the given command.
     *
     * @param command the command.
     * @return whether this enforcement provider is applicable.
     */
    default boolean isApplicable(final T command) {
        return true;
    }

    /**
     * Creates an {@link AbstractEnforcement} for the given {@code context}.
     *
     * @param context the context.
     * @return the {@link AbstractEnforcement}.
     */
    AbstractEnforcement<T> createEnforcement(Contextual<T> context);

    /**
     * Check whether a signal will change authorization of subsequent signals when dispatched.
     *
     * @param signal the signal.
     * @return whether the signal will change authorization.
     */
    default boolean changesAuthorization(final T signal) {
        return false;
    }

    /**
     * Convert this enforcement provider into a stream of enforcement tasks.
     *
     * @param preEnforcer failable future to execute before the actual enforcement.
     * @return the stream.
     */
    default Flow<Contextual<WithDittoHeaders>, EnforcementTask, NotUsed> createEnforcementTask(
            final PreEnforcer preEnforcer
    ) {
        return Flow.<Contextual<WithDittoHeaders>, Optional<Contextual<T>>>fromFunction(
                contextual -> contextual.tryToMapMessage(this::mapToHandledClass))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(contextual -> buildEnforcementTask(contextual, preEnforcer))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<EnforcementTask> buildEnforcementTask(final Contextual<T> contextual,
            final PreEnforcer preEnforcer) {
        final T message = contextual.getMessage();
        final boolean changesAuthorization = changesAuthorization(message);


        if (message instanceof WithEntityId) {
            final var entityId = ((WithEntityId) message).getEntityId();
            return Optional.of(EnforcementTask.of(entityId, changesAuthorization,
                    () -> preEnforcer.withErrorHandlingAsync(contextual,
                            contextual.setMessage(null).withReceiver(null),
                            converted -> createEnforcement(converted).enforceSafely()
                    )
            ));
        } else {
            // This should not happen: Refuse to perform enforcement task for messages without ID.
            contextual.getLog().error("Cannot build EnforcementTask without EntityId: <{}>", message);
            return Optional.empty();
        }

    }

    private Optional<T> mapToHandledClass(final WithDittoHeaders message) {
        return getCommandClass().isInstance(message)
                ? Optional.of(getCommandClass().cast(message)).filter(this::isApplicable)
                : Optional.empty();
    }

}
