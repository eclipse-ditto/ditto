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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.AttributeModified;

/**
 * Strategy that creates {@link Bson} for {@link AttributeModified} events.
 */
public final class MongoAttributeModifiedStrategy extends MongoEventToPersistenceStrategy<AttributeModified> {

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Bson> thingUpdates(final AttributeModified event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        final JsonPointer pointer = event.getAttributePointer();
        final JsonValue value = event.getAttributeValue();
        return AttributesUpdateFactory.createAttributesUpdates(indexLengthRestrictionEnforcer, pointer, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<PolicyUpdate> policyUpdates(final AttributeModified event, final PolicyEnforcer policyEnforcer) {
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
