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
package org.eclipse.ditto.connectivity.service.messaging.internal.ssl;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Test;

/**
 * Tests {@link Keys} class.
 */
public class KeysTest {

    private static final ExceptionMapper EXCEPTION_MAPPER = new ExceptionMapper(DittoHeaders.empty(),
            JsonPointer.empty(), JsonPointer.empty(), JsonPointer.empty());

    @Test
    public void loadPrivateKey() {
        Keys.getPrivateKey(TestConstants.Certificates.SERVER_KEY, EXCEPTION_MAPPER);
    }

    @Test
    public void loadPublicKey() {
        Keys.getPublicKey(TestConstants.Certificates.SERVER_PUB, EXCEPTION_MAPPER);
    }

    @Test
    public void loadCertificate() {
        Keys.getCertificate(TestConstants.Certificates.SERVER_CRT, EXCEPTION_MAPPER);
    }
}
