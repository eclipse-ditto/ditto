/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.concierge;

import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * Wrap messages which are send to the concierge service.
 */
public final class ConciergeWrapper {

    private ConciergeWrapper() {
        throw new AssertionError();
    }

    /**
     * Wrap a signal in a sharded message envelope addressed to the correct {@code EnforcerActor}.
     *
     * @param signal the signal to wrap.
     * @return the sharded message envelope.
     */
    public static ShardedMessageEnvelope wrapForEnforcer(final Signal<?> signal) {
        final EntityId entityId;
        if (MessageCommand.RESOURCE_TYPE.equals(signal.getResourceType())) {
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
