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
package org.eclipse.ditto.gateway.service.endpoints;

import org.eclipse.ditto.things.model.ThingId;

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
    public static final ThingId KNOWN_THING_ID = ThingId.of(KNOWN_NS + ":dummyThingId");

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
    public static final BasicHttpCredentials STATUS_CREDENTIALS =
            HttpCredentials.createBasicHttpCredentials("devops", "status!");

    private EndpointTestConstants() {
        throw new AssertionError();
    }
}
