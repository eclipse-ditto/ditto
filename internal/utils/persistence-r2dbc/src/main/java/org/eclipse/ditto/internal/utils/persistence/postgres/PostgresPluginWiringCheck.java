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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStore;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

/**
 * Boot-time check that the Pekko persistence plugins a deployment wires belong to PostgreSQL when the PostgreSQL
 * provider is selected.
 * <p>
 * A service wires its journal and snapshot-store plugins from HOCON (the per-plugin {@code <id>.class} blocks and the
 * {@code pekko.persistence.*.auto-start-*} lists), while the read journal, cleanup, ping and namespace operations come
 * from the selected {@code PersistenceBackendProvider}. The two halves are configured separately and can drift: a
 * deployment that selects the PostgreSQL provider but still wires the MongoDB plugin classes (a half-applied overlay)
 * would write to one database and read from the other. {@link PostgresPersistenceBackendProvider#verifyPluginWiring}
 * runs this check before the schema bootstrap and the persistent shard regions, so such a deployment refuses to boot,
 * touching no database, with a message that names the plugin, its class and the expected classes.
 * <p>
 * Three legs, every one a {@link DittoConfigError}:
 * <ol>
 *     <li>each auto-start plugin ID the provider declares for the service
 *     ({@link PersistencePluginConfig#getAutoStartPluginIds(String)}) must resolve to a PostgreSQL plugin class;</li>
 *     <li>each entry of the effective {@code pekko.persistence.journal.auto-start-journals} and
 *     {@code pekko.persistence.snapshot-store.auto-start-snapshot-stores} lists must as well, which catches a
 *     deployment whose Pekko lists still auto-start the MongoDB plugins although the provider's own IDs are fine;</li>
 *     <li>an ID without a resolvable {@code class} is an error of its own.</li>
 * </ol>
 * A plugin ID is resolved the way Pekko resolves it: as a HOCON path, so {@code acme.persistence.pg-journal} names the
 * nested block {@code acme { persistence { pg-journal { class = ... } } }}. Pekko's shorthand {@code ""} for the
 * default plugin ({@code pekko.persistence.journal.plugin}) is rejected, because Ditto never configures a default
 * plugin. A malformed value where a list or a class name is expected is reported as a {@link DittoConfigError} too.
 *
 * @since 4.0.0
 */
public final class PostgresPluginWiringCheck {

    /**
     * The {@code class =} values of the PostgreSQL journal and snapshot-store plugins, as shipped in
     * {@code ditto-postgres-persistence.conf}.
     */
    private static final Set<String> EXPECTED_PLUGIN_CLASS_NAMES = Set.of(
            PostgresJournal.class.getName(),
            PostgresSnapshotStore.class.getName());

    private static final String BACKEND = "PostgreSQL";
    private static final String CLASS_KEY = "class";
    private static final String AUTO_START_JOURNALS_PATH = "pekko.persistence.journal.auto-start-journals";
    private static final String AUTO_START_SNAPSHOT_STORES_PATH =
            "pekko.persistence.snapshot-store.auto-start-snapshot-stores";

    private PostgresPluginWiringCheck() {
        throw new AssertionError("nope");
    }

    /**
     * Verifies that every journal and snapshot-store plugin the effective config wires for the service is a PostgreSQL
     * plugin. See the class Javadoc for the three legs.
     *
     * @param effectiveConfig the fully-resolved actor-system config (root scope: the Pekko plugin blocks such as
     * {@code ditto-postgres-things-journal} live there, not under {@code ditto}).
     * @param pluginConfig the PostgreSQL provider's plugin-ID surface, supplying the auto-start IDs it declares for the
     * service.
     * @param serviceName the lower-case service name, e.g. {@code "things"}, {@code "policies"},
     * {@code "connectivity"}.
     * @throws DittoConfigError if a wired plugin class is not a PostgreSQL plugin class, a plugin ID has no resolvable
     * {@code class}, or the config is malformed where the check reads it.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static void verify(final Config effectiveConfig, final PersistencePluginConfig pluginConfig,
            final String serviceName) {
        Objects.requireNonNull(effectiveConfig, "effectiveConfig");
        Objects.requireNonNull(pluginConfig, "pluginConfig");
        Objects.requireNonNull(serviceName, "serviceName");

        for (final String pluginId : pluginConfig.getAutoStartPluginIds(serviceName)) {
            final String actualClass = resolvePluginClass(effectiveConfig, pluginId, serviceName);
            if (!EXPECTED_PLUGIN_CLASS_NAMES.contains(actualClass)) {
                throw new DittoConfigError(String.format(
                        "Persistence backend mismatch: the selected persistence-backend-provider is %s, but the "
                                + "effective config wires persistence plugin <%s> to class <%s>. Expected one of the "
                                + "%s plugin classes %s. The deployment HOCON still wires a different backend's "
                                + "plugins than the selected provider - fix the "
                                + "ditto.extensions.persistence-backend-provider selection or the plugin <class = ...> "
                                + "wiring so both name the same backend.",
                        BACKEND, pluginId, actualClass, BACKEND, EXPECTED_PLUGIN_CLASS_NAMES));
            }
        }
        verifyEffectiveAutoStartList(effectiveConfig, AUTO_START_JOURNALS_PATH, serviceName);
        verifyEffectiveAutoStartList(effectiveConfig, AUTO_START_SNAPSHOT_STORES_PATH, serviceName);
    }

    private static void verifyEffectiveAutoStartList(final Config effectiveConfig, final String listPath,
            final String serviceName) {
        if (!effectiveConfig.hasPath(listPath)) {
            return;
        }
        final List<String> pluginIds;
        try {
            pluginIds = effectiveConfig.getStringList(listPath);
        } catch (final ConfigException.WrongType e) {
            throw new DittoConfigError(String.format(
                    "Persistence plugin wiring check (service <%s>, selected persistence-backend-provider %s): the "
                            + "effective <%s> is not a list of plugin IDs - fix the auto-start lists.",
                    serviceName, BACKEND, listPath), e);
        }
        for (final String pluginId : pluginIds) {
            final String actualClass = resolvePluginClass(effectiveConfig, pluginId, serviceName);
            if (!EXPECTED_PLUGIN_CLASS_NAMES.contains(actualClass)) {
                throw new DittoConfigError(String.format(
                        "Persistence backend mismatch: the effective <%s> list auto-starts plugin <%s> (class <%s>), "
                                + "which is not a %s plugin class (expected one of %s). The deployment kept the other "
                                + "backend's pekko.persistence auto-start wiring - include the PostgreSQL profile at a "
                                + "position where its pekko.persistence.* lists win, or fix the lists.",
                        listPath, pluginId, actualClass, BACKEND, EXPECTED_PLUGIN_CLASS_NAMES));
            }
        }
    }

    private static String resolvePluginClass(final Config effectiveConfig, final String pluginId,
            final String serviceName) {
        if (pluginId.isEmpty()) {
            throw new DittoConfigError(String.format(
                    "Persistence plugin wiring check (service <%s>, selected persistence-backend-provider %s): an "
                            + "auto-start list names the default plugin (\"\"), which Ditto does not configure - name "
                            + "the %s plugin blocks explicitly.",
                    serviceName, BACKEND, BACKEND));
        }
        // A plugin ID is a HOCON path, exactly as Pekko resolves it: dots descend into nested blocks.
        final String classPath = pluginId + "." + CLASS_KEY;
        try {
            if (!effectiveConfig.hasPath(classPath)) {
                throw new DittoConfigError(String.format(
                        "Persistence plugin wiring check could not resolve a <class> for auto-start plugin <%s> "
                                + "(service <%s>, selected persistence-backend-provider %s): the effective config has "
                                + "no <%s>. The deployment HOCON is missing the plugin block the selected provider "
                                + "expects - wire <%s { class = ... }> for %s.",
                        pluginId, serviceName, BACKEND, classPath, pluginId, BACKEND));
            }
            return effectiveConfig.getString(classPath);
        } catch (final ConfigException.BadPath | ConfigException.WrongType e) {
            throw new DittoConfigError(String.format(
                    "Persistence plugin wiring check could not read <%s> for auto-start plugin <%s> (service <%s>, "
                            + "selected persistence-backend-provider %s): %s",
                    classPath, pluginId, serviceName, BACKEND, e.getMessage()), e);
        }
    }
}
