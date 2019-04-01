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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.AttributeCreated;

/**
 * Strategy that creates {@link Bson} for {@link AttributeCreated} events.
 */
public final class MongoAttributeCreatedStrategy extends MongoEventToPersistenceStrategy<AttributeCreated> {

    @Override
    public final List<Bson> thingUpdates(final AttributeCreated event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        final JsonPointer pointer = event.getAttributePointer();
        final JsonValue value = event.getAttributeValue();
        return AttributesUpdateFactory.createAttributesUpdates(indexLengthRestrictionEnforcer, pointer, value);
    }

    @Override
    public final List<PolicyUpdate> policyUpdates(final AttributeCreated event, final Enforcer policyEnforcer) {
        if (isPolicyRevelant(event.getImplementedSchemaVersion())) {
            final JsonPointer pointer = event.getAttributePointer();
            final JsonValue value = event.getAttributeValue();
            return Collections.singletonList(
                    PolicyUpdateFactory.createAttributePolicyIndexUpdate(event.getThingId(),
                            pointer,
                            value,
                            policyEnforcer));
        }
        return Collections.emptyList();
    }
}
