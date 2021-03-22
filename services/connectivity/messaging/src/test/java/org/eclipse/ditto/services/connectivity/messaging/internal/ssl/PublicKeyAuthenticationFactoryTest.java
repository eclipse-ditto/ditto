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
package org.eclipse.ditto.services.connectivity.messaging.internal.ssl;

import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.SshPublicKeyAuthentication;
import org.eclipse.ditto.model.connectivity.UserPasswordCredentials;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.junit.Test;

public class PublicKeyAuthenticationFactoryTest {

    @Test(expected = UnsupportedOperationException.class)
    public void clientCertificate() {
        ClientCertificateCredentials.newBuilder().build().accept(PublicKeyAuthenticationFactory.getInstance());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void usernamePassword() {
        UserPasswordCredentials.newInstance("test", "test").accept(PublicKeyAuthenticationFactory.getInstance());
    }

    @Test
    public void sshPublicKeyAuthentication() {
        final SshPublicKeyAuthentication publicKeyAuthentication = SshPublicKeyAuthentication.of("test",
                TestConstants.Certificates.SERVER_PUB, TestConstants.Certificates.SERVER_KEY);
        publicKeyAuthentication.accept(PublicKeyAuthenticationFactory.getInstance());
    }
}
