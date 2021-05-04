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
package org.eclipse.ditto.base.model.common;

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
