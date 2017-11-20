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
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.services.thingsearch.persistence.ProcessableThingEvent;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;

/**
 * Strategy that creates {@link Bson} for {@link FeaturePropertyDeleted} events.
 */
public final class MongoFeaturePropertyDeletedStrategy extends MongoEventToPersistenceStrategy<FeaturePropertyDeleted> {

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Bson> thingUpdates(final ProcessableThingEvent<FeaturePropertyDeleted> event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        final FeaturePropertyDeleted e = event.getThingEvent();
        return Collections.singletonList(
                FeaturesUpdateFactory.createDeleteFeaturePropertyUpdate(e.getFeatureId(),
                        e.getPropertyPointer()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<PolicyUpdate> policyUpdates(final ProcessableThingEvent<FeaturePropertyDeleted> event,
            final PolicyEnforcer policyEnforcer) {
        if (isPolicyRevelant(event.getJsonSchemaVersion())) {
            final FeaturePropertyDeleted e = event.getThingEvent();
            return Collections.singletonList(PolicyUpdateFactory.createFeaturePropertyDeletion(e.getThingId(), e
                            .getFeatureId(),
                    e.getPropertyPointer()));
        }
        return Collections.emptyList();
    }
}
