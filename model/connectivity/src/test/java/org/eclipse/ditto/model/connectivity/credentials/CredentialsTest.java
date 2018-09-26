/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.connectivity.credentials;

import static org.assertj.core.api.Java6Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.model.connectivity.credentials.Credentials}.
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
