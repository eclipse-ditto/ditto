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
package org.eclipse.ditto.services.utils.tracing;

/**
 * This class provides Keys that should be used when adding tags to traces.
 */
public final class TracingTags {

    private static final String PREFIX = "ditto.";
    //AMQP round trip tags
    public static final String COMMAND_TYPE = PREFIX + "command.type";
    public static final String COMMAND_TYPE_PREFIX = PREFIX + "command.typePrefix";
    public static final String COMMAND_CATEGORY = PREFIX + "command.category";
    public static final String CONNECTION_ID =  PREFIX + "connection.id";

    //HTTP round trip tags
    public static final String STATUS_CODE = PREFIX + "statusCode";
    public static final String REQUEST_METHOD = PREFIX + "request.method";
    public static final String REQUEST_PATH = PREFIX + "request.path";

    //Auth filter tags
    public static final String AUTH_SUCCESS = PREFIX + "auth.success";
    public static final String AUTH_ERROR = PREFIX + "auth.error";
    public static final String AUTH_TYPE = PREFIX + "auth.type";

    //Mapping tags
    public static final String MAPPING_SUCCESS = PREFIX + "mapping.success";
}
