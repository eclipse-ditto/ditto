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
package org.eclipse.ditto.services.utils.health.status;

import java.io.InputStream;
import java.util.Scanner;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.utils.config.ConfigUtil;

/**
 * Helper class providing the status of a Things-Service microservice instance.
 */
public final class Status {

    private static final String VERSIONS_JSON = "versions.json";

    private static final JsonObject versionsJson;

    static {
        final InputStream versionsInputStream = Status.class.getClassLoader().getResourceAsStream(VERSIONS_JSON);
        versionsJson = JsonFactory.readFrom(new Scanner(versionsInputStream).useDelimiter("\\Z").next()).asObject()
                .setValue("hostname", ConfigUtil.getHostNameFromEnv()) //
                .setValue("local-address", ConfigUtil.getLocalHostAddress()) //
                .setValue("instance-index", ConfigUtil.instanceIndex()) //
                .setValue("processor-count", Runtime.getRuntime().availableProcessors()) //
                .setValue("total-memory", (Runtime.getRuntime().totalMemory() / 1024 / 1024) + " MB");
    }

    /**
     * Returns the static status of this instance as loaded from {@value #VERSIONS_JSON} file in the classpath +
     * calculated JVM specific values determined at startup.
     *
     * @return the static status of this instance.
     */
    public static JsonObject provideStaticStatus() {
        return versionsJson;
    }


    private Status() {
        // no op
    }

}
