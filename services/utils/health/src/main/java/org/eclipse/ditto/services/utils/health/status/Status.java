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
package org.eclipse.ditto.services.utils.health.status;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.utils.config.HostNameSupplier;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.config.LocalHostAddressSupplier;
import org.eclipse.ditto.services.utils.config.Version;

/**
 * Helper class providing the status of a Things-Service microservice instance.
 */
public final class Status {

    private static final JsonObject STATUS_JSON;

    static {
        STATUS_JSON = Version.getVersionJson()
                .setValue("hostname", HostNameSupplier.getInstance().get())
                .setValue("local-address", LocalHostAddressSupplier.getInstance().get())
                .setValue("instance", InstanceIdentifierSupplier.getInstance().get())
                .setValue("processor-count", Runtime.getRuntime().availableProcessors())
                .setValue("total-memory", (Runtime.getRuntime().totalMemory() / 1024 / 1024) + " MB");
    }

    /**
     * Returns the static status of this instance as loaded from {@link Version#getVersionJson()} +
     * calculated JVM specific values determined at startup.
     *
     * @return the static status of this instance.
     */
    public static JsonObject provideStaticStatus() {
        return STATUS_JSON;
    }

    private Status() {
        // no op
    }

}
