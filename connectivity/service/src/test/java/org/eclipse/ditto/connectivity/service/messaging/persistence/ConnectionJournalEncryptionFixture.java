/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import javax.annotation.Nullable;

/**
 * Encryption keys and the connection encryption config block the connectivity journal golden tests write and replay
 * the stored connection rows with.
 */
final class ConnectionJournalEncryptionFixture {

    /** Fixed key already used by the connectivity tests. */
    static final String K_OLD = "vJFSTPE9PO2BtZlcMAwNjs8jdFvQCk0Ya9MVdYjRJUU=";
    /** Generated once with {@code EncryptorAesGcm.generateAESKeyAsString()}. */
    static final String K_NEW = "cn5N7Ykci9vET2VuANQ-CedGexxA1LcAsQvJeAM01qk=";

    static String encryptionBlock(final boolean enabled, @Nullable final String key, @Nullable final String oldKey) {
        return "ditto.connectivity.connection.encryption {\n"
                + "  encryption-enabled = " + enabled + "\n"
                + "  symmetrical-key = \"" + (key == null ? "" : key) + "\"\n"
                + "  old-symmetrical-key = \"" + (oldKey == null ? "" : oldKey) + "\"\n"
                + "  json-pointers = [\"/uri\", \"/credentials/password\"]\n"
                + "}\n";
    }

    private ConnectionJournalEncryptionFixture() {
        throw new AssertionError();
    }

}
