/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.coap;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.californium.elements.auth.AdditionalInfo;
import org.eclipse.californium.scandium.auth.ApplicationLevelInfoSupplier;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

/**
 * TODO TJ doc
 * TODO TJ use in scope of authenticating with PSK / Certificate
 */
final class DittoCoapDeviceInfoSupplier implements ApplicationLevelInfoSupplier {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(DittoCoapDeviceInfoSupplier.class);

    /**
     * Creates additional information for authenticated devices.
     *
     * @param context the {@link AuthorizationContext} of the authenticated device.
     * @return additional device information.
     */
    public static AdditionalInfo createDeviceInfo(final AuthorizationContext context) {
        final Map<String, Object> result = new HashMap<>();
        result.put(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), context);
        return AdditionalInfo.from(result);
    }

    @Override
    public AdditionalInfo getInfo(final Principal principal, final Object customArgument) {
        if (customArgument instanceof AdditionalInfo additionalInfo) {
            final AuthorizationContext authorizationContext =
                    additionalInfo.get(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), AuthorizationContext.class);
            LOGGER.info("get AdditionalInfo auth context: {} - for principal: {}", authorizationContext, principal);
            return additionalInfo;
        }
        LOGGER.debug("did not get additional info");
        return AdditionalInfo.empty();
    }
}
