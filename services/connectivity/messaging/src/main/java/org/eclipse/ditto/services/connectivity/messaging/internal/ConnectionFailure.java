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
