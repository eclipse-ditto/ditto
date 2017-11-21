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
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;

/**
 * Strategy that creates {@link Bson} for {@link FeaturePropertiesModified} events.
 */
public final class MongoFeaturePropertiesModifiedStrategy extends
        MongoEventToPersistenceStrategy<FeaturePropertiesModified> {

    @Override
    public final List<Bson> thingUpdates(final FeaturePropertiesModified event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        return FeaturesUpdateFactory.createUpdateForFeatureProperties(indexLengthRestrictionEnforcer,
                event.getFeatureId(),
                event.getProperties());
    }

    @Override
    public final List<PolicyUpdate> policyUpdates(final FeaturePropertiesModified event,
            final PolicyEnforcer policyEnforcer) {

        if (isPolicyRevelant(event.getImplementedSchemaVersion())) {
            return Collections.singletonList(PolicyUpdateFactory.createFeaturePropertiesUpdate(
                    event.getThingId(),
                    event.getFeatureId(),
                    event.getProperties(),
                    policyEnforcer));
        }
        return Collections.emptyList();
    }
}
