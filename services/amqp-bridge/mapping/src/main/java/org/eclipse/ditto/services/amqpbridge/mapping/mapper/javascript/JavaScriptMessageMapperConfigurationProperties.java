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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript;

/**
 * TODO doc
 */
final class JavaScriptMessageMapperConfigurationProperties {

    private JavaScriptMessageMapperConfigurationProperties() {
        throw new AssertionError();
    }

    public static final String INCOMING_MAPPING_SCRIPT = "incomingMappingScript";
    public static final String OUTGOING_MAPPING_SCRIPT = "outgoingMappingScript";
    public static final String LOAD_BYTEBUFFER_JS = "loadBytebufferJS";
    public static final String LOAD_LONG_JS = "loadLongJS";
    public static final String LOAD_MUSTACHE_JS = "loadMustacheJS";

}
