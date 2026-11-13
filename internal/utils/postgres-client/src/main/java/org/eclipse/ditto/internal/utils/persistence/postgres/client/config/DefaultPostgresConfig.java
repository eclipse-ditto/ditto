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

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link PostgresConfig}, reading the {@code postgresql} block (i.e. {@code ditto.postgresql}
 * when handed the {@code ditto} config).
 */
@Immutable
public final class DefaultPostgresConfig implements PostgresConfig {

    /**
     * The HOCON path of the PostgreSQL backend block, relative to the {@code ditto} namespace.
     */
    public static final String CONFIG_PATH = "postgresql";

    /**
     * Literal emitted instead of the {@code uri} when redaction cannot be applied safely (e.g. a malformed URI). Never
     * leak the raw String in that case.
     */
    private static final String REDACTED = "<redacted>";

    private static final String MASK = "****";

    /**
     * Matches the userinfo of an r2dbc PostgreSQL URI, tolerating the optional single-segment wrapper scheme (e.g.
     * {@code r2dbc:pool:postgresql://}). Group 1 = user, group 2 = password (optional). Anchored at the start.
     * <p>
     * The scheme prefix is matched case-insensitively ({@code (?i:...)}), consistent with {@link #SECRET_PARAM_PATTERN}
     * (ticket ditto-postgres-smv, defence-in-depth: an upper-case scheme must not pass through with its credentials
     * intact). The user/password character classes deliberately exclude {@code @}; a literal {@code @} inside the
     * password (which RFC&nbsp;3986 requires to be percent-encoded) is caught earlier by {@link #looksMalformed} and
     * routed to the {@value #REDACTED} fallback rather than risking a partial leak.
     */
    private static final Pattern USERINFO_PATTERN = Pattern.compile(
            "^((?i:r2dbc:(?:[^:]+:)?postgresql://))([^:@/?]+)(?::([^@/?]+))?@");

    /**
     * Matches a {@code key=value} pair (in a query string) whose key is a known secret. Case-insensitive on the key.
     */
    private static final Pattern SECRET_PARAM_PATTERN = Pattern.compile(
            "(?i)([?&](?:sslPassword|password|sslcert|sslkey)=)([^&]*)");

    private final String uri;
    private final String username;
    private final String password;
    private final PoolerMode poolerMode;
    private final Duration connectTimeout;
    private final Duration statementTimeout;
    private final boolean schemaSelfHealEnabled;
    private final boolean forceCustomPlan;
    private final DefaultPoolConfig poolConfig;
    private final DefaultSslConfig sslConfig;
    private final DefaultBootstrapConfig bootstrapConfig;

    private DefaultPostgresConfig(final ScopedConfig config) {
        uri = config.getString(PostgresConfigValue.URI.getConfigPath());
        username = config.getString(PostgresConfigValue.USERNAME.getConfigPath());
        password = config.getString(PostgresConfigValue.PASSWORD.getConfigPath());
        final String poolerModeValue = config.getString(PostgresConfigValue.POOLER_MODE.getConfigPath());
        poolerMode = PoolerMode.fromConfigValue(poolerModeValue)
                .orElseThrow(() -> new DittoConfigError(String.format(
                        "Invalid ditto.postgresql.pooler-mode <%s>; expected one of direct|session|transaction.",
                        poolerModeValue)));
        connectTimeout = config.getDuration(PostgresConfigValue.CONNECT_TIMEOUT.getConfigPath());
        statementTimeout = config.getDuration(PostgresConfigValue.STATEMENT_TIMEOUT.getConfigPath());
        // The self-heal flag lives at the nested path `postgresql.schema.self-heal`. Read it defensively (hasPath +
        // default) rather than relying on the KnownConfigValue fallback: the fallback map keys the default under the
        // dotted path, and ConfigFactory.parseMap's handling of a dotted key is not something to bank on here — this
        // read is correct whether the fallback nested the default or not.
        final String selfHealPath = PostgresConfigValue.SCHEMA_SELF_HEAL.getConfigPath();
        schemaSelfHealEnabled = config.hasPath(selfHealPath)
                ? config.getBoolean(selfHealPath)
                : (boolean) PostgresConfigValue.SCHEMA_SELF_HEAL.getDefaultValue();
        forceCustomPlan = config.getBoolean(PostgresConfigValue.FORCE_CUSTOM_PLAN.getConfigPath());
        poolConfig = DefaultPoolConfig.of(config);
        sslConfig = DefaultSslConfig.of(config);
        bootstrapConfig = DefaultBootstrapConfig.of(config);
    }

    /**
     * Returns an instance of {@code DefaultPostgresConfig} based on the settings of the given Config.
     *
     * @param config is supposed to provide the settings of the PostgreSQL backend at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws DittoConfigError if {@code config} is invalid.
     */
    public static DefaultPostgresConfig of(final Config config) {
        return new DefaultPostgresConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, PostgresConfigValue.values()));
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public Optional<String> getUsername() {
        return username.isEmpty() ? Optional.empty() : Optional.of(username);
    }

    @Override
    public Optional<String> getPassword() {
        return password.isEmpty() ? Optional.empty() : Optional.of(password);
    }

    @Override
    public PoolerMode getPoolerMode() {
        return poolerMode;
    }

    @Override
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    @Override
    public Duration getStatementTimeout() {
        return statementTimeout;
    }

    @Override
    public boolean isSchemaSelfHealEnabled() {
        return schemaSelfHealEnabled;
    }

    @Override
    public boolean isForceCustomPlan() {
        return forceCustomPlan;
    }

    @Override
    public DefaultPoolConfig getPoolConfig() {
        return poolConfig;
    }

    @Override
    public DefaultSslConfig getSslConfig() {
        return sslConfig;
    }

    @Override
    public DefaultBootstrapConfig getBootstrapConfig() {
        return bootstrapConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPostgresConfig that = (DefaultPostgresConfig) o;
        return Objects.equals(uri, that.uri) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                poolerMode == that.poolerMode &&
                Objects.equals(connectTimeout, that.connectTimeout) &&
                Objects.equals(statementTimeout, that.statementTimeout) &&
                schemaSelfHealEnabled == that.schemaSelfHealEnabled &&
                forceCustomPlan == that.forceCustomPlan &&
                Objects.equals(poolConfig, that.poolConfig) &&
                Objects.equals(sslConfig, that.sslConfig) &&
                Objects.equals(bootstrapConfig, that.bootstrapConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, username, password, poolerMode, connectTimeout, statementTimeout, schemaSelfHealEnabled,
                forceCustomPlan, poolConfig, sslConfig, bootstrapConfig);
    }

    /**
     * Redacts credentials embedded in an r2dbc PostgreSQL connection URI so it is safe to log or render in
     * {@link #toString()}.
     * <p>
     * Performs targeted string surgery on the raw URI String (R2DBC's compound scheme {@code r2dbc:postgresql:} is
     * parsed by {@link java.net.URI} as an opaque part, so {@code URI.getUserInfo()} does not surface the credentials):
     * <ol>
     *     <li>the userinfo password ({@code user:secret@} → {@code user:****@}), tolerating the optional pool wrapper
     *     scheme {@code r2dbc:pool:postgresql://}, and</li>
     *     <li>the values of the known secret query parameters {@code sslPassword}, {@code password}, {@code sslcert}
     *     and {@code sslkey} (→ {@code ****}).</li>
     * </ol>
     * On any failure — a {@code null} input, an apparently malformed URI (e.g. unbalanced {@code []} brackets in the
     * authority) or an unexpected exception — the literal {@value #REDACTED} is returned. This method never throws.
     *
     * @param rawUri the connection URI to redact; may be {@code null}.
     * @return the redacted URI, or {@value #REDACTED} if it could not be redacted safely.
     */
    static String redactSecrets(final String rawUri) {
        if (rawUri == null) {
            return REDACTED;
        }
        try {
            if (looksMalformed(rawUri)) {
                return REDACTED;
            }
            String redacted = rawUri;

            final Matcher userInfo = USERINFO_PATTERN.matcher(redacted);
            if (userInfo.find() && userInfo.group(3) != null) {
                redacted = userInfo.replaceFirst(
                        Matcher.quoteReplacement(userInfo.group(1) + userInfo.group(2) + ":" + MASK + "@"));
            }

            final Matcher secretParam = SECRET_PARAM_PATTERN.matcher(redacted);
            redacted = secretParam.replaceAll(matchResult ->
                    Matcher.quoteReplacement(matchResult.group(1) + MASK));

            return redacted;
        } catch (final RuntimeException e) {
            return REDACTED;
        }
    }

    /**
     * Cheap structural guard against an obviously malformed or unsafe-to-redact URI: the authority (between
     * {@code ://} and the first {@code /} or {@code ?}) must contain balanced square brackets and at most one
     * {@code @}.
     * <ul>
     *     <li>Unbalanced {@code []} brackets are the canonical malformed example cited by the plan.</li>
     *     <li>More than one {@code @} (ticket ditto-postgres-smv) means a literal, non-percent-encoded {@code @} is
     *     embedded in the userinfo — RFC&nbsp;3986 requires it to be percent-encoded. The {@link #USERINFO_PATTERN}
     *     password class stops at the first {@code @}, so masking such a URI would leave a plaintext password fragment
     *     (e.g. {@code user:****@ss@host}). Treating it as malformed routes the whole URI to the {@value #REDACTED}
     *     fallback, which cannot leak.</li>
     * </ul>
     */
    private static boolean looksMalformed(final String rawUri) {
        final int schemeEnd = rawUri.indexOf("://");
        if (schemeEnd < 0) {
            // not an r2dbc URI we recognise — leave the rest of redaction to attempt best effort.
            return false;
        }
        final int authorityStart = schemeEnd + "://".length();
        int authorityEnd = authorityStart;
        while (authorityEnd < rawUri.length()) {
            final char c = rawUri.charAt(authorityEnd);
            if (c == '/' || c == '?') {
                break;
            }
            authorityEnd++;
        }
        final String authority = rawUri.substring(authorityStart, authorityEnd);
        int depth = 0;
        int atCount = 0;
        for (int i = 0; i < authority.length(); i++) {
            final char c = authority.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth < 0) {
                    return true;
                }
            } else if (c == '@') {
                atCount++;
                if (atCount > 1) {
                    return true;
                }
            }
        }
        return depth != 0;
    }

    @Override
    public String toString() {
        // credentials intentionally not logged
        return getClass().getSimpleName() + " [" +
                "uri=" + redactSecrets(uri) +
                ", poolerMode=" + poolerMode +
                ", connectTimeout=" + connectTimeout +
                ", statementTimeout=" + statementTimeout +
                ", schemaSelfHealEnabled=" + schemaSelfHealEnabled +
                ", forceCustomPlan=" + forceCustomPlan +
                ", poolConfig=" + poolConfig +
                ", sslConfig=" + sslConfig +
                ", bootstrapConfig=" + bootstrapConfig +
                "]";
    }

}
