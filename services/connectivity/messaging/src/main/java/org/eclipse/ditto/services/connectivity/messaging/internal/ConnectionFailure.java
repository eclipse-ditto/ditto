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
package org.eclipse.ditto.services.connectivity.messaging.internal;

import akka.actor.Status;

/**
 * Messaging internal error message for when a Failure was detected on a a Connection.
 */
public interface ConnectionFailure extends WithOrigin {

    /**
     * @return the description of the failure.
     */
    String getFailureDescription();

    /**
     * @return the Failure containing the cause.
     */
    Status.Failure getFailure();
}
