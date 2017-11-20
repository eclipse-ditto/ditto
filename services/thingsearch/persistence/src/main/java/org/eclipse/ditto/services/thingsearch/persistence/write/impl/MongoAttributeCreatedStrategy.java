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
import org.eclipse.ditto.services.thingsearch.persistence.ProcessableThingEvent;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.AttributeCreated;

public final class MongoAttributeCreatedStrategy extends MongoEventToPersistenceStrategy<AttributeCreated> {

    @Override
    public final List<Bson> thingUpdates(final ProcessableThingEvent<AttributeCreated> event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        final AttributeCreated attributeCreated = event.getThingEvent();
        final JsonPointer pointer = attributeCreated.getAttributePointer();
        final JsonValue value = attributeCreated.getAttributeValue();
        return AttributesUpdateFactory.createAttributesUpdates(indexLengthRestrictionEnforcer, pointer, value);
    }

    @Override
    public final List<PolicyUpdate> policyUpdates(final ProcessableThingEvent<AttributeCreated> event,
            final PolicyEnforcer policyEnforcer) {
        if (isPolicyRevelant(event.getJsonSchemaVersion())) {
            final AttributeCreated attributeCreated = event.getThingEvent();
            final JsonPointer pointer = attributeCreated.getAttributePointer();
            final JsonValue value = attributeCreated.getAttributeValue();
            return Collections.singletonList(
                    PolicyUpdateFactory.createAttributePolicyIndexUpdate(attributeCreated.getThingId(),
                            pointer,
                            value,
                            policyEnforcer));
        }
        return Collections.emptyList();
    }
}
