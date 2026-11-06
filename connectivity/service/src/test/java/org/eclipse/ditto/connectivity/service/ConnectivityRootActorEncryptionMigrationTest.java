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
package org.eclipse.ditto.connectivity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.eclipse.ditto.connectivity.service.config.DefaultFieldsEncryptionConfig;
import org.eclipse.ditto.connectivity.service.config.FieldsEncryptionConfig;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests the condition on which {@link ConnectivityRootActor} starts the connection encryption migration singleton and
 * its proxy: whenever encryption is enabled or an old key is set (an initial encryption, a key rotation or the
 * disabling of encryption may be triggered), on every persistence backend; nothing when encryption is off and no old
 * key is set.
 */
public final class ConnectivityRootActorEncryptionMigrationTest {

    private static final String KEY = "vJFSTPE9PO2BtZlcMAwNjs8jdFvQCk0Ya9MVdYjRJUU=";
    private static final String OTHER_KEY = "anotherBase64Key256bitsLongForAESGCM32Bytes=";

    @Test
    public void encryptionEnabledWithoutAnOldKeyNeedsTheMigration() {
        // initial encryption of plaintext secrets
        assertThat(ConnectivityRootActor.encryptionMigrationNeeded(encryption(true, KEY, ""))).isTrue();
    }

    @Test
    public void encryptionEnabledWithAnOldKeyNeedsTheMigration() {
        // key rotation
        assertThat(ConnectivityRootActor.encryptionMigrationNeeded(encryption(true, OTHER_KEY, KEY))).isTrue();
    }

    @Test
    public void encryptionDisabledWithAnOldKeyNeedsTheMigration() {
        // the documented "disable encryption" set-up: decrypt with the old key, write plaintext
        assertThat(ConnectivityRootActor.encryptionMigrationNeeded(encryption(false, "", KEY))).isTrue();
    }

    @Test
    public void encryptionDisabledWithoutAnOldKeyNeedsNoMigration() {
        assertThat(ConnectivityRootActor.encryptionMigrationNeeded(encryption(false, "", ""))).isFalse();
        // a current key alone, with encryption off, is not a migration set-up either
        assertThat(ConnectivityRootActor.encryptionMigrationNeeded(encryption(false, KEY, ""))).isFalse();
    }

    private static FieldsEncryptionConfig encryption(final boolean enabled, final String key, final String oldKey) {
        return DefaultFieldsEncryptionConfig.of(ConfigFactory.parseMap(Map.of(
                "encryption.encryption-enabled", enabled,
                "encryption.symmetrical-key", key,
                "encryption.old-symmetrical-key", oldKey)));
    }
}
