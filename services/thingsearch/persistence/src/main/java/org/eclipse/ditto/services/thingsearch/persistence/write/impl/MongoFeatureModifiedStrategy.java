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
import org.eclipse.ditto.signals.events.things.FeatureModified;

/**
 * Strategy that allows create {@link Bson} for {@link FeatureModified} events.
 */
public final class MongoFeatureModifiedStrategy extends MongoEventToPersistenceStrategy<FeatureModified> {

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Bson> thingUpdates(final ProcessableThingEvent<FeatureModified> event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        return FeaturesUpdateFactory.createUpdateForFeature(indexLengthRestrictionEnforcer,
                event.getThingEvent().getFeature(),
                false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<PolicyUpdate> policyUpdates(final ProcessableThingEvent<FeatureModified> event,
            final PolicyEnforcer policyEnforcer) {
        if (isPolicyRevelant(event.getJsonSchemaVersion())) {
            return Collections.singletonList(
                    PolicyUpdateFactory.createFeatureUpdate(event.getThingEvent().getThingId(), event
                            .getThingEvent().getFeature(), policyEnforcer));
        }
        return Collections.emptyList();
    }
}
