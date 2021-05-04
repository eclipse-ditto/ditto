/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.amqp.status;

import javax.jms.Session;

import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;

/**
 * Reports a closed amqp session.
 */
public final class SessionClosedStatusReport {

    private final Session session;
    private final ConnectionFailure failure;

    private SessionClosedStatusReport(
            final ConnectionFailure failure,
            final Session session) {
        this.failure = failure;
        this.session = session;
    }

    public static SessionClosedStatusReport get(final ConnectionFailure failure, final Session session) {
        return new SessionClosedStatusReport(failure, session);
    }

    public ConnectionFailure getFailure() {
        return failure;
    }

    public Session getSession() {
        return session;
    }
}
