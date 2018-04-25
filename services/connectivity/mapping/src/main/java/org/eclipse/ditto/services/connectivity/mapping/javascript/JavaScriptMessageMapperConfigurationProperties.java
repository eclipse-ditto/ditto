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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

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
