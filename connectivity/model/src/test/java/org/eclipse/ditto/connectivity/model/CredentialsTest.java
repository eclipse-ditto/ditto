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
package org.eclipse.ditto.connectivity.model;

import static org.assertj.core.api.Java6Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests {@link Credentials}.
 */
public final class CredentialsTest {

    @Test
    public void deserializeX509Credentials() {
        // do not use the class X509Credentials here so that the test does not force JVM to load it
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set("type", "client-cert")
                .build();

        final Credentials credentials = Credentials.fromJson(jsonObject);

        assertThat(credentials.getClass().getSimpleName()).isEqualTo("ClientCertificateCredentials");
    }
}
