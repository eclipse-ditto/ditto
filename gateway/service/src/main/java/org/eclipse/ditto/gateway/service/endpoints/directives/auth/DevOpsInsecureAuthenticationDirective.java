/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.directives.auth;

import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;

/**
 * Authentication directive which does not perform any authentication.
 */
public final class DevOpsInsecureAuthenticationDirective implements DevopsAuthenticationDirective {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(DevOpsInsecureAuthenticationDirective.class);
    private static final DevOpsInsecureAuthenticationDirective INSTANCE = new DevOpsInsecureAuthenticationDirective();

    private DevOpsInsecureAuthenticationDirective() {}

    public static DevOpsInsecureAuthenticationDirective getInstance() {
        return INSTANCE;
    }

    @Override
    public Route authenticateDevOps(final String realm, final DittoHeaders dittoHeaders, final Route inner) {
        LOGGER.withCorrelationId(dittoHeaders).warn("DevOps resource is not secured");
        return inner;
    }
}
