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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

/**
 * Holds configuration keys for JavaScript MessageMapper.
 */
final class JavaScriptMessageMapperConfigurationProperties {

    private JavaScriptMessageMapperConfigurationProperties() {
        throw new AssertionError();
    }

    /**
     * Configuration key for the incoming MappingScript.
     */
    static final String INCOMING_SCRIPT = "incomingScript";

    /**
     * Configuration key for the outgoing MappingScript.
     */
    static final String OUTGOING_SCRIPT = "outgoingScript";

    /**
     * Configuration key for loading the "bytebuffer.js" library.
     */
    static final String LOAD_BYTEBUFFER_JS = "loadBytebufferJS";

    /**
     * Configuration key for loading the "long.js" library.
     */
    static final String LOAD_LONG_JS = "loadLongJS";

}
