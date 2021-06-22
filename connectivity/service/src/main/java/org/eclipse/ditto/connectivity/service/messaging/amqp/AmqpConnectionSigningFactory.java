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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import org.eclipse.ditto.connectivity.model.HmacCredentials;

/**
 * Functional interface for creator of {@link AmqpConnectionSigning} from {@code HmacCredentials}.
 */
@FunctionalInterface
public interface AmqpConnectionSigningFactory {

    /**
     * Create an {@link AmqpConnectionSigning} object from HMAC credentials.
     *
     * @param credentials the credentials.
     * @return the signing process using the given credentials.
     */
    AmqpConnectionSigning createAmqpConnectionSigning(HmacCredentials credentials);

}
