/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.config;

import java.io.InputStream;
import java.util.Scanner;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Helper class providing the status of a Things-Service microservice instance.
 */
public final class Version {

    private static final JsonFieldDefinition<String> SERVICE_VERSION_FIELD =
            JsonFieldDefinition.ofString("service-version");
    private static final String VERSIONS_FILE_NAME = "versions.json";

    private static final JsonObject VERSIONS_JSON;

    static {
        final InputStream versionsInputStream = Version.class.getClassLoader().getResourceAsStream(VERSIONS_FILE_NAME);
        VERSIONS_JSON = JsonFactory.readFrom(new Scanner(versionsInputStream).useDelimiter("\\Z").next()).asObject();
    }

    /**
     * Returns the static versions of this instance as loaded from {@value #VERSIONS_FILE_NAME} file in the classpath +
     * calculated JVM specific values determined at startup.
     *
     * @return the static status of this instance.
     */
    public static JsonObject getVersionJson() {
        return VERSIONS_JSON;
    }

    public static String getServiceVersion() {
        return VERSIONS_JSON.getValue(SERVICE_VERSION_FIELD)
                .orElseThrow(() -> new DittoConfigError("Missing service-version in versions.json."));
    }

    private Version() {
        // no op
    }

}
