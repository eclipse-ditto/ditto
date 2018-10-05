/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.base.common;

/**
 * Global constants for Ditto.
 */
public final class DittoConstants {

    private DittoConstants() {
        throw new AssertionError();
    }

    /**
     * Defines the Ditto Protocol Content-Type.
     */
    public static final String DITTO_PROTOCOL_CONTENT_TYPE = "application/vnd.eclipse.ditto+json";
}
