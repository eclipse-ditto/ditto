/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.apache.qpid.jms.provider.amqp.message;

import java.util.Optional;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

/**
 * Workaround to access non-exposed, defined, vendor-specific AMQP properties for Qpid JMS client.
 * Breaks easily on Qpid JMS upgrade.
 * Remove methods from this class as they become available in Qpid JMS API.
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class JMSPropertyWorkaround {

    private JMSPropertyWorkaround() {}

    /**
     * Get the content-encoding vendor property if set.
     *
     * @param amqpJmsMessageFacade Qpid-specific representation of a JMS message.
     * @return the optional content-encoding property.
     */
    public static Optional<String> getContentEncoding(final AmqpJmsMessageFacade amqpJmsMessageFacade) {
        return Optional.ofNullable(amqpJmsMessageFacade.getProperties())
                .map(Properties::getContentEncoding)
                .map(Symbol::toString);
    }

    /**
     * Set the content encoding.
     *
     * @param facade Qpid-specific representation of a JMS message.
     * @param contentEncoding the optional content-encoding property.
     */
    public static void setContentEncoding(final AmqpJmsMessageFacade facade, final String contentEncoding) {
        ensurePropertiesNonNull(facade);
        facade.getProperties().setContentEncoding(Symbol.valueOf(contentEncoding));
    }

    private static void ensurePropertiesNonNull(final AmqpJmsMessageFacade facade) {
        if (facade.getProperties() == null) {
            facade.setProperties(new Properties());
        }
    }
}
