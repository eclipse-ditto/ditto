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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

import io.r2dbc.postgresql.client.SSLMode;

/**
 * Default implementation of {@link PostgresConfig.SslConfig}.
 */
@Immutable
public final class DefaultSslConfig implements PostgresConfig.SslConfig {

    static final String CONFIG_PATH = "ssl";

    /**
     * The SSL modes that verify the server certificate chain and therefore need a trust anchor (root cert).
     */
    private static final Set<String> CHAIN_VERIFYING_MODES = Set.of("verify-ca", "verify-full");

    /**
     * The set of accepted (lower-cased) {@code ssl.mode} values, derived from the r2dbc-postgresql {@link SSLMode}
     * canonical wire values (e.g. {@code "verify-full"}). Note: this deliberately does NOT use
     * {@link SSLMode#fromValue(String)} for validation because that method also accepts the enum name form
     * ({@code "verify_full"}), so a typo like {@code "verify_full"} would slip through. A strict membership check
     * against the canonical dash-form values rejects such typos at load time.
     */
    private static final Set<String> VALID_MODES = Arrays.stream(SSLMode.values())
            .map(mode -> mode.toString().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    private final String mode;
    private final String rootCert;
    private final String cert;
    private final String key;
    private final String keyPassword;
    private final boolean allowSystemTruststore;

    private DefaultSslConfig(final ScopedConfig config) {
        mode = config.getString(SslConfigValue.MODE.getConfigPath()).trim().toLowerCase(Locale.ROOT);
        if (!VALID_MODES.contains(mode)) {
            throw new DittoConfigError(String.format(
                    "Invalid value '%s' for config key 'ditto.postgresql.%s.%s': must be one of %s.",
                    mode, CONFIG_PATH, SslConfigValue.MODE.getConfigPath(),
                    VALID_MODES.stream().sorted().collect(Collectors.joining(", ", "[", "]"))));
        }
        rootCert = config.getString(SslConfigValue.ROOT_CERT.getConfigPath());
        cert = config.getString(SslConfigValue.CERT.getConfigPath());
        key = config.getString(SslConfigValue.KEY.getConfigPath());
        keyPassword = config.getString(SslConfigValue.KEY_PASSWORD.getConfigPath());
        allowSystemTruststore = config.getBoolean(SslConfigValue.ALLOW_SYSTEM_TRUSTSTORE.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultSslConfig} based on the settings of the given Config.
     *
     * @param config is supposed to provide the settings at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSslConfig of(final Config config) {
        return new DefaultSslConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, SslConfigValue.values()));
    }

    @Override
    public String getMode() {
        return mode;
    }

    @Override
    public Optional<String> getRootCert() {
        return rootCert.isEmpty() ? Optional.empty() : Optional.of(rootCert);
    }

    @Override
    public Optional<String> getCert() {
        return cert.isEmpty() ? Optional.empty() : Optional.of(cert);
    }

    @Override
    public Optional<String> getKey() {
        return key.isEmpty() ? Optional.empty() : Optional.of(key);
    }

    @Override
    public Optional<String> getKeyPassword() {
        return keyPassword.isEmpty() ? Optional.empty() : Optional.of(keyPassword);
    }

    @Override
    public boolean isAllowSystemTruststore() {
        return allowSystemTruststore;
    }

    /**
     * The boot guard: refuse to boot when a chain-verifying SSL mode is configured without a root cert while
     * system-truststore fallback is disallowed. Without this, r2dbc-postgresql silently falls back to the JVM
     * default truststore and the TLS handshake fails at <em>first connect</em>, not at boot.
     *
     * @throws DittoConfigError if the configuration is unsafe.
     */
    public void validateBootGuard() {
        if (CHAIN_VERIFYING_MODES.contains(mode) && getRootCert().isEmpty() && !allowSystemTruststore) {
            throw new DittoConfigError(String.format(
                    "Refusing to boot: ditto.postgresql.ssl.mode=%s verifies the server certificate chain but " +
                            "ditto.postgresql.ssl.root-cert is unset. Configure 'root-cert' with the PEM CA bundle, " +
                            "or explicitly set ditto.postgresql.ssl.allow-system-truststore=true to fall back to the " +
                            "JVM default truststore (handshake would otherwise fail at first connect, not at boot).",
                    mode));
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSslConfig that = (DefaultSslConfig) o;
        return allowSystemTruststore == that.allowSystemTruststore &&
                Objects.equals(mode, that.mode) &&
                Objects.equals(rootCert, that.rootCert) &&
                Objects.equals(cert, that.cert) &&
                Objects.equals(key, that.key) &&
                Objects.equals(keyPassword, that.keyPassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, rootCert, cert, key, keyPassword, allowSystemTruststore);
    }

    @Override
    public String toString() {
        // key material intentionally not logged
        return getClass().getSimpleName() + " [" +
                "mode=" + mode +
                ", rootCertConfigured=" + getRootCert().isPresent() +
                ", certConfigured=" + getCert().isPresent() +
                ", keyConfigured=" + getKey().isPresent() +
                ", allowSystemTruststore=" + allowSystemTruststore +
                "]";
    }

}
