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
package org.eclipse.ditto.services.amqpbridge.messaging.persistence;

import org.eclipse.ditto.services.utils.persistence.mongo.AbstractMongoEventAdapter;

import akka.actor.ExtendedActorSystem;

import org.eclipse.ditto.signals.events.amqpbridge.AmqpBridgeEvent;
import org.eclipse.ditto.signals.events.amqpbridge.AmqpBridgeEventRegistry;

/**
 * EventAdapter for {@link AmqpBridgeEvent}s persisted into
 * akka-persistence event-journal. Converts Events to MongoDB BSON objects and vice versa.
 */
public final class AmqpBridgeMongoEventAdapter extends AbstractMongoEventAdapter<AmqpBridgeEvent> {

    public AmqpBridgeMongoEventAdapter(final ExtendedActorSystem system) {
        super(system, AmqpBridgeEventRegistry.newInstance());
    }

}
