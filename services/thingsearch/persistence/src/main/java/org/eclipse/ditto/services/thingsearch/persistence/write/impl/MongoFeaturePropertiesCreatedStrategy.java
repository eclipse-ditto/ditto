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
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;

/**
 * Strategy that allows create {@link Bson} for {@link FeaturePropertiesCreated} events.
 */
public final class MongoFeaturePropertiesCreatedStrategy extends
        MongoEventToPersistenceStrategy<FeaturePropertiesCreated> {

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Bson> thingUpdates(final ProcessableThingEvent<FeaturePropertiesCreated> event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        final FeaturePropertiesCreated e = event.getThingEvent();
        return FeaturesUpdateFactory.createUpdateForFeatureProperties(
                indexLengthRestrictionEnforcer,
                e.getFeatureId(),
                e.getProperties());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<PolicyUpdate> policyUpdates(final ProcessableThingEvent<FeaturePropertiesCreated> event,
            final PolicyEnforcer policyEnforcer) {
        if (isPolicyRevelant(event.getJsonSchemaVersion())) {
            final FeaturePropertiesCreated e = event.getThingEvent();
            return Collections.singletonList(PolicyUpdateFactory.createFeaturePropertiesUpdate(
                    e.getThingId(),
                    e.getFeatureId(),
                    e.getProperties(),
                    policyEnforcer));
        }
        return Collections.emptyList();
    }
}
