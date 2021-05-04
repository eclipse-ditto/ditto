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
package org.eclipse.ditto.internal.utils.health.status;

import java.io.InputStream;
import java.util.Scanner;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.HostNameSupplier;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.internal.utils.config.LocalHostAddressSupplier;

/**
 * Helper class providing the status of a Things-Service microservice instance.
 */
public final class Status {

    private static final String VERSIONS_FILE_NAME = "versions.json";

    private static final JsonObject VERSIONS_JSON;

    static {
        final InputStream versionsInputStream = Status.class.getClassLoader().getResourceAsStream(VERSIONS_FILE_NAME);
        if (versionsInputStream == null) {
            throw new DittoConfigError("Missing required file in classpath: " + VERSIONS_FILE_NAME);
        }
        VERSIONS_JSON = JsonFactory.readFrom(new Scanner(versionsInputStream).useDelimiter("\\Z").next()).asObject()
                .setValue("hostname", HostNameSupplier.getInstance().get())
                .setValue("local-address", LocalHostAddressSupplier.getInstance().get())
                .setValue("instance", InstanceIdentifierSupplier.getInstance().get())
                .setValue("processor-count", Runtime.getRuntime().availableProcessors())
                .setValue("total-memory", (Runtime.getRuntime().totalMemory() / 1024 / 1024) + " MB");
    }

    /**
     * Returns the static status of this instance as loaded from {@value #VERSIONS_FILE_NAME} file in the classpath +
     * calculated JVM specific values determined at startup.
     *
     * @return the static status of this instance.
     */
    public static JsonObject provideStaticStatus() {
        return VERSIONS_JSON;
    }

    private Status() {
        // no op
    }

}
