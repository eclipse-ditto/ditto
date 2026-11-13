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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * Reads {@code ditto.postgresql.search.path-indexes}:
 * <pre>{@code
 * path-indexes {
 *   paths = [ { path = "attributes/location/city", kind = "trigram" } ]
 *   direct-uri = ""
 * }
 * }</pre>
 * {@code paths} is a HOCON list of objects, or (as set from the {@code POSTGRES_SEARCH_PATH_INDEXES} environment
 * variable) a string holding such a list in JSON or HOCON syntax. Each entry needs a {@code path} (a search field, as
 * in an RQL filter) and a {@code kind}. An absent {@code paths} key or a blank string means "not configured" (nothing
 * is managed); {@code []} is a configured, empty list (every managed index is dropped). Every entry is validated here,
 * so a bad entry fails the service start with a {@link DittoConfigError} instead of being skipped.
 */
@Immutable
public final class DefaultPostgresSearchPathIndexConfig implements PostgresSearchPathIndexConfig {

    /**
     * The HOCON path of the block, relative to the {@code ditto} namespace.
     */
    public static final String CONFIG_PATH = "postgresql.search.path-indexes";

    private static final String PATHS = "paths";
    private static final String DIRECT_URI = "direct-uri";
    private static final String PATH = "path";
    private static final String KIND = "kind";
    private static final String FULL_PATHS = "ditto." + CONFIG_PATH + "." + PATHS;
    private static final String ENV_PATHS = "POSTGRES_SEARCH_PATH_INDEXES";

    @Nullable private final List<PathIndex> pathIndexes;
    @Nullable private final String directUri;

    private DefaultPostgresSearchPathIndexConfig(@Nullable final List<PathIndex> pathIndexes,
            @Nullable final String directUri) {
        this.pathIndexes = pathIndexes == null ? null : List.copyOf(pathIndexes);
        this.directUri = directUri;
    }

    /**
     * @param dittoConfig the settings of the {@code ditto} namespace (the block is read at {@value #CONFIG_PATH}).
     * @param simpleFieldMappings the search service's simple field mappings (RQL field name → internal name), so a
     * configured path resolves exactly like a query on it.
     * @return the config.
     * @throws DittoConfigError if the block is invalid.
     */
    public static DefaultPostgresSearchPathIndexConfig of(final Config dittoConfig,
            final Map<String, String> simpleFieldMappings) {
        Objects.requireNonNull(dittoConfig, "dittoConfig");
        final Config block = dittoConfig.hasPath(CONFIG_PATH) ? dittoConfig.getConfig(CONFIG_PATH)
                : ConfigFactory.empty();
        final ThingsFieldExpressionFactory fieldExpressionFactory =
                ThingsFieldExpressionFactory.of(Objects.requireNonNull(simpleFieldMappings, "simpleFieldMappings"));
        final List<PathIndex> indexes = isSet(block)
                ? parsePaths(block.getValue(PATHS), fieldExpressionFactory)
                : null;
        final String directUri = block.hasPath(DIRECT_URI) ? block.getString(DIRECT_URI).trim() : "";
        return new DefaultPostgresSearchPathIndexConfig(indexes, directUri.isEmpty() ? null : validated(directUri));
    }

    private static String validated(final String directUri) {
        // never echo the URI: it may carry credentials
        final ConnectionFactoryOptions options;
        try {
            options = ConnectionFactoryOptions.parse(directUri);
        } catch (final RuntimeException e) {
            throw new DittoConfigError(String.format("Invalid ditto.%s.%s: not an r2dbc URI (%s).", CONFIG_PATH,
                    DIRECT_URI, e.getClass().getSimpleName()), e);
        }
        if (!"postgresql".equals(options.getValue(ConnectionFactoryOptions.DRIVER))) {
            throw new DittoConfigError(String.format("Invalid ditto.%s.%s: expected an r2dbc:postgresql:// URI.",
                    CONFIG_PATH, DIRECT_URI));
        }
        return directUri;
    }

    /**
     * @return whether {@code paths} is set: present and not a blank string (an unset environment variable leaves the
     * key absent; an empty one yields a blank string, which counts as unset too).
     */
    private static boolean isSet(final Config block) {
        if (!block.hasPath(PATHS)) {
            return false;
        }
        final ConfigValue value = block.getValue(PATHS);
        return value.valueType() != ConfigValueType.STRING || !((String) value.unwrapped()).isBlank();
    }

    private static List<PathIndex> parsePaths(final ConfigValue value,
            final ThingsFieldExpressionFactory fieldExpressionFactory) {
        final List<? extends ConfigValue> entries = entries(value);
        final Map<String, PathIndex> byName = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            final PathIndex index = parseEntry(i, entries.get(i), fieldExpressionFactory);
            final PathIndex previous = byName.putIfAbsent(index.name(), index);
            if (previous != null) {
                throw new DittoConfigError(String.format("Invalid %s[%d]: duplicate of path <%s> (both resolve to "
                                + "wpath <%s> with kind <%s>).", FULL_PATHS, i, previous.path(), index.wpath(),
                        index.kind().getConfigValue()));
            }
        }
        return new ArrayList<>(byName.values());
    }

    private static List<? extends ConfigValue> entries(final ConfigValue value) {
        final ConfigValueType type = value.valueType();
        if (type == ConfigValueType.LIST) {
            return (ConfigList) value;
        }
        if (type == ConfigValueType.STRING) {
            final String text = ((String) value.unwrapped()).trim();
            if (text.isEmpty()) {
                return List.of();
            }
            try {
                return ConfigFactory.parseString(PATHS + " = " + text).getList(PATHS);
            } catch (final ConfigException e) {
                throw new DittoConfigError(String.format("Invalid %s (e.g. from the environment variable %s): "
                                + "expected a list of {\"path\": ..., \"kind\": ...} objects in JSON or HOCON "
                                + "syntax: %s", FULL_PATHS, ENV_PATHS, e.getMessage()), e);
            }
        }
        if (type == ConfigValueType.OBJECT) {
            // -D...paths.0.path=... style: an object whose keys are all list indexes.
            try {
                return ConfigFactory.empty().withValue(PATHS, value).getList(PATHS);
            } catch (final ConfigException e) {
                throw new DittoConfigError(String.format("Invalid %s: expected a list, got an object: %s",
                        FULL_PATHS, e.getMessage()), e);
            }
        }
        throw new DittoConfigError(String.format("Invalid %s: expected a list of {path, kind} objects, got <%s>.",
                FULL_PATHS, value.render()));
    }

    private static PathIndex parseEntry(final int position, final ConfigValue entry,
            final ThingsFieldExpressionFactory fieldExpressionFactory) {
        if (entry.valueType() != ConfigValueType.OBJECT) {
            throw new DittoConfigError(String.format("Invalid %s[%d]: expected an object {path = ..., kind = ...}, "
                    + "got <%s>.", FULL_PATHS, position, entry.render()));
        }
        final Config object = ((ConfigObject) entry).toConfig();
        final String path = requiredString(object, PATH, position);
        final String kindValue = requiredString(object, KIND, position);
        final PathIndexKind kind = PathIndexKind.fromConfigValue(kindValue)
                .orElseThrow(() -> new DittoConfigError(String.format("Invalid %s[%d].%s <%s>: expected one of: %s.",
                        FULL_PATHS, position, KIND, kindValue, PathIndexKind.configValues())));
        try {
            return PathIndex.of(path, kind, fieldExpressionFactory);
        } catch (final IllegalArgumentException e) {
            throw new DittoConfigError(String.format("Invalid %s[%d]: %s", FULL_PATHS, position, e.getMessage()), e);
        }
    }

    private static String requiredString(final Config object, final String key, final int position) {
        if (!object.hasPath(key)) {
            final String hint = KIND.equals(key) ? " (one of: " + PathIndexKind.configValues() + ")" : "";
            throw new DittoConfigError(String.format("Invalid %s[%d]: missing <%s>%s.", FULL_PATHS, position, key,
                    hint));
        }
        try {
            return object.getString(key);
        } catch (final ConfigException e) {
            throw new DittoConfigError(String.format("Invalid %s[%d].%s: %s", FULL_PATHS, position, key,
                    e.getMessage()), e);
        }
    }

    @Override
    public boolean isPathListConfigured() {
        return pathIndexes != null;
    }

    @Override
    public List<PathIndex> getPathIndexes() {
        return pathIndexes == null ? List.of() : pathIndexes;
    }

    @Override
    public Optional<String> getDirectUri() {
        return Optional.ofNullable(directUri);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPostgresSearchPathIndexConfig that = (DefaultPostgresSearchPathIndexConfig) o;
        return Objects.equals(pathIndexes, that.pathIndexes) && Objects.equals(directUri, that.directUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathIndexes, directUri);
    }

    @Override
    public String toString() {
        // the direct URI may carry credentials: say only whether one is set
        return getClass().getSimpleName() + " [" +
                "pathIndexes=" + (pathIndexes == null ? "<not configured>" : pathIndexes) +
                ", directUriConfigured=" + (directUri != null) +
                "]";
    }

}
