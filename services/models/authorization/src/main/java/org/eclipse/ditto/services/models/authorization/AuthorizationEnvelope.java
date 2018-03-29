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
package org.eclipse.ditto.services.models.authorization;

import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * Wrap Ditto-signals with the correct sharded message envelope to address an {@code EnforcerActor} of
 * authorization-service.
 */
public final class AuthorizationEnvelope {

    /**
     * Wrap a signal in a sharded message envelope addressed to the correct {@code EnforcerActor}.
     *
     * @param signal the signal to wrap.
     * @return the sharded message envelope.
     */
    public static ShardedMessageEnvelope wrap(final Signal<?> signal) {
        final EntityId entityId;
        if (signal instanceof MessageCommand) {
            entityId = EntityId.of(ThingCommand.RESOURCE_TYPE, signal.getId());
        } else {
            entityId = EntityId.of(signal.getResourceType(), signal.getId());
        }
        return createEnvelope(entityId, signal);
    }

    private static ShardedMessageEnvelope createEnvelope(final EntityId entityId, final Signal<?> signal) {
        return ShardedMessageEnvelope.of(
                entityId.toString(),
                signal.getType(),
                signal.toJson(signal.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                signal.getDittoHeaders());
    }
}
