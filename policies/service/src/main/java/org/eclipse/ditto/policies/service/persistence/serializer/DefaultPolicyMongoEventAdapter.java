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
package org.eclipse.ditto.policies.service.persistence.serializer;

import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;

import akka.actor.ExtendedActorSystem;

/**
 * EventAdapter for {@link PolicyEvent}s persisted into akka-persistence event-journal.
 * Converts Event to MongoDB BSON objects and vice versa.
 */
public final class DefaultPolicyMongoEventAdapter extends AbstractPolicyMongoEventAdapter {

    public DefaultPolicyMongoEventAdapter(final ExtendedActorSystem system) {
        super(system);
    }

}
