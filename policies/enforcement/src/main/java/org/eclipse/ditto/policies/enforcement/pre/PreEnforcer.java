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

import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

/**
 * A Pre-Enforcer is a function converting a {@link Signal} to a CompletionStage of a Signal, potentially throwing an
 * exception if something was "forbidden" by the enforcer implementation.
 * Implementations should apply some access validation. In case you want to only modify a signal modify the signal use
 * {@link org.eclipse.ditto.base.service.signaltransformer.SignalTransformer} instead.
 * All implementations need to have a constructor which accepts two parameters: ActorSystem and Config.
 */
public interface PreEnforcer extends Function<Signal<?>, CompletionStage<Signal<?>>>, DittoExtensionPoint {

    /**
     * Logger of pre-enforcers.
     */
    DittoLogger LOGGER = DittoLoggerFactory.getLogger(PreEnforcer.class);

    /**
     * Safe cast the message to a withEntityId
     *
     * @param message the message to be cast.
     * @return the withEntityId.
     */
    default WithEntityId getMessageAsWithEntityId(@Nullable final WithDittoHeaders message) {
        if (message instanceof WithEntityId withEntityId) {
            return withEntityId;
        }
        if (null == message) {
            // just in case
            LOGGER.error("Given message is null!");
            throw DittoInternalErrorException.newBuilder().build();
        }
        final String msgPattern = "Message of type <{0}> does not implement WithEntityId!";
        throw new IllegalArgumentException(MessageFormat.format(msgPattern, message.getClass()));
    }

    /**
     * Safe cast the entityId to a namespacedEntityId.
     *
     * @param entityId the entityId to be cast.
     * @return the withEntityId.
     */
    default NamespacedEntityId getEntityIdAsNamespacedEntityId(@Nullable final EntityId entityId) {
        if (entityId instanceof NamespacedEntityId namespacedEntityId) {
            return namespacedEntityId;
        }
        if (null == entityId) {
            // just in case
            LOGGER.error("Given entityId is null!");
            throw DittoInternalErrorException.newBuilder().build();
        }
        final String msgPattern = "Entity of type <{0}> is not namespaced!";
        throw new IllegalArgumentException(MessageFormat.format(msgPattern, entityId.getClass()));
    }

    /**
     * Extracts the {@code EntityId} of a signal.
     *
     * @param signal the signal to retrieve the {@code EntityId} from.
     * @return the {@code EntityId}.
     */
    default Optional<EntityId> extractEntityRelatedSignalId(final Signal<?> signal) {
        return WithEntityId.getEntityIdOfType(EntityId.class, signal);
    }

}
