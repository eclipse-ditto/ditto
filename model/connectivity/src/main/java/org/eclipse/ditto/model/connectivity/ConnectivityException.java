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
package org.eclipse.ditto.model.connectivity;

/**
 * Aggregates all {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}s which are thrown by the
 * Connectivity service.
 */
public interface ConnectivityException {

    /**
     * Error code prefix of errors thrown by the Connectivity service.
     */
    String ERROR_CODE_PREFIX = "connectivity" + ":";

}
