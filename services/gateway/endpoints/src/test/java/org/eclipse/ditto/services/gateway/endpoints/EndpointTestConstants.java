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
package org.eclipse.ditto.services.gateway.endpoints;

import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.BasicHttpCredentials;
import akka.http.javadsl.model.headers.HttpCredentials;

/**
 * Constants used for gateway endpoint tests.
 */
public final class EndpointTestConstants {

    public static final String KNOWN_FEATURE_ID = "knownFeatureId";
    public static final String KNOWN_NS = "org.eclipse.ditto.test";
    public static final String KNOWN_THING_ID = KNOWN_NS + ":dummyThingId";

    public static final String UNKNOWN_PATH = "/doesNotExist";
    public static final String KNOWN_SUBJECT = "knownSubject";
    public static final String KNOWN_SUBJECT_WITH_SLASHES = "knownSubject/with/slashes";
    public static final String KNOWN_CORRELATION_ID = "knownCorrelationId";

    /**
     * The domain used by JUnitRouteTest when providing relative URLs.
     */
    public static final String KNOWN_DOMAIN = "example.com";
    public static final StatusCode DUMMY_COMMAND_SUCCESS = StatusCodes.OK;
    public static final BasicHttpCredentials DEVOPS_CREDENTIALS =
            HttpCredentials.createBasicHttpCredentials("devops", "devops!");

    private EndpointTestConstants() {
        throw new AssertionError();
    }
}
