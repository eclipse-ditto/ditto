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
package org.eclipse.ditto.internal.utils.tracing;

/**
 * This class provides Keys that should be used when adding tags to traces.
 */
public final class TracingTags {

    private static final String PREFIX = "ditto.";

    // General
    public static final String CORRELATION_ID = PREFIX + "correlationId";
    public static final String SIGNAL_TYPE = PREFIX + "signal.type";
    public static final String CHANNEL = PREFIX + "channel";

    // connectivity tags
    public static final String CONNECTION_ID = PREFIX + "connection.id";
    public static final String CONNECTION_TYPE = PREFIX + "connection.type";

    // HTTP round trip tags
    public static final String STATUS_CODE = PREFIX + "statusCode";
    public static final String REQUEST_METHOD = PREFIX + "request.method";
    public static final String REQUEST_PATH = PREFIX + "request.path";

    // Auth filter tags
    public static final String AUTH_SUCCESS = PREFIX + "auth.success";
    public static final String AUTH_ERROR = PREFIX + "auth.error";
    public static final String AUTH_TYPE = PREFIX + "auth.type";

    // Mapping tags
    public static final String MAPPING_SUCCESS = PREFIX + "mapping.success";

    // Acknowledgement tags
    public static final String ACK_SUCCESS = PREFIX + "ack.success";
    public static final String ACK_REDELIVER = PREFIX + "ack.redeliver";

    private TracingTags() {
        throw new AssertionError();
    }
}
