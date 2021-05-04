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

import java.security.PublicKey;
import java.security.cert.Certificate;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Implementation of KeyExtractor to load test certificates.
 *
 * @see org.eclipse.ditto.connectivity.service.messaging.TestConstants.Certificates
 */
public class TestCertificates {

    public static PublicKey getPublicKey(final String publicKey) {
        return Keys.getPublicKey(publicKey, ExceptionMapper.forSshPublicKeyCredentials(DittoHeaders.empty()));
    }

    public static Certificate getCertificate(final String certificate) {
        return Keys.getCertificate(certificate, ExceptionMapper.forTrustedCertificates(DittoHeaders.empty()));
    }

    public static void main(String[] args) {

        final PublicKey publicKey =
                Keys.getPublicKey("-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvNLnOxbW" +
                        "/XoO7Tuw16K0\nPUyVpmAVcfZL3w67mLcZuy7fRarHXR1y0ixkpwRjoZ2HpZAXOkwTemIKLC9P00B/\n" +
                        "/cNfQhunE2bEJTvSoVNKAKerq/B5M7GHs91EJ+JJNfgWnHEqSYqeuzaDGw2ePlaJ\nLD+zj0Temwgv3+bYN5WR" +
                        "+/TmqLPigpZRwID1n4/5KFGHYwHMTSCDxeaU0iinjwnA\nh4KfOXIQONoaTx85FnGvO84dKSTIZtb+1VZrBB5P7EEBlM6" +
                        "/D0A3cTZDBLcXkULp\nTapoJj0EXJzXh457+O1YnyQ0ItzbDZ8qyzm6cj4+9K7n5NTqjg+l680oCd94jL87\nRQIDAQAB\n" +
                        "-----END PUBLIC KEY-----", ExceptionMapper.forSshPublicKeyCredentials(DittoHeaders.empty()));

        System.out.println(publicKey.getFormat());


    }
}
