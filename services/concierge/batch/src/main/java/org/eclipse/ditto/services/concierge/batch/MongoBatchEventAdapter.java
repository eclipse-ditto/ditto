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
package org.eclipse.ditto.services.concierge.batch;

import org.eclipse.ditto.services.models.concierge.batch.BatchStepCommandRegistry;
import org.eclipse.ditto.services.models.concierge.batch.BatchStepCommandResponseRegistry;
import org.eclipse.ditto.services.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.signals.events.base.EventRegistry;
import org.eclipse.ditto.signals.events.batch.BatchEvent;
import org.eclipse.ditto.signals.events.batch.BatchEventRegistry;

import akka.actor.ExtendedActorSystem;

/**
 * EventAdapter for {@link BatchEvent}s persisted into akka-persistence event-journal. Converts Events to MongoDB BSON
 * objects and vice versa.
 */
public final class MongoBatchEventAdapter extends AbstractMongoEventAdapter<BatchEvent> {

    public MongoBatchEventAdapter(final ExtendedActorSystem system) {
        super(system, createEventRegistry());
    }

    private static EventRegistry<BatchEvent> createEventRegistry() {
        final BatchStepCommandRegistry batchStepCommandRegistry = BatchStepCommandRegistry.newInstance();
        final BatchStepCommandResponseRegistry batchStepCommandResponseRegistry =
                BatchStepCommandResponseRegistry.newInstance();

        return BatchEventRegistry.newInstance(batchStepCommandRegistry, batchStepCommandResponseRegistry);
    }

}
