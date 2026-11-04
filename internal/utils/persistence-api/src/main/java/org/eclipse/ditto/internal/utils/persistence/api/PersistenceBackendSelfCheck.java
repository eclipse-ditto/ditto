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
package org.eclipse.ditto.internal.utils.persistence.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;

/**
 * Boot-time active-backend self-check — layer 2 of the pluggable-persistence switchability proof.
 * <p>
 * A service's {@code RootActor} resolves a {@link PersistenceBackendProvider} from
 * {@code ditto.extensions.persistence-backend-provider} and then wires the Pekko persistence plugins (journal +
 * snapshot store) and read journal from <em>separate</em> HOCON. Those two halves can drift: the classic
 * misconfiguration is "the selected provider is Postgres, but the deployment's HOCON still wires the Mongo plugins" (or
 * vice versa). The persistent actors would then start against the wrong backend and fail in confusing ways at first
 * write.
 * <p>
 * This check closes that gap. Right after the provider is resolved (and after {@code bootstrapSchema()}), and
 * <em>before</em> the persistent shard regions start, the {@code RootActor} calls {@link #verify} with the effective
 * config, the resolved provider, the service name and the resolved read-journal class name. The check:
 * <ol>
 *     <li>looks up, in the effective config, the {@code <plugin-id>.class} of every auto-start journal/snapshot plugin
 *     ID the provider declares for the service
 *     ({@link PersistencePluginConfig#getAutoStartPluginIds(String)}), and</li>
 *     <li>asserts each resolved plugin {@code class} is one of the provider's declared
 *     {@link PersistenceBackendProvider#expectedPluginClassNames() expected plugin class names}, and</li>
 *     <li>asserts the resolved read-journal class equals the provider's declared
 *     {@link PersistenceBackendProvider#expectedReadJournalClassName() expected read-journal class name}.</li>
 * </ol>
 * On a mismatch it throws a {@link DittoConfigError} naming the provider's {@link BackendFamily}, the offending plugin
 * ID (or the read journal) and the actual-vs-expected class, so the service refuses to boot against a mis-wired config.
 * On a match it returns quietly: a correctly-configured Mongo deployment and a correctly-configured Postgres deployment
 * both pass.
 * <p>
 * The check is backend-NEUTRAL: it compares STRINGS only (the {@code class} HOCON values and the
 * provider-supplied expected class names) and never references a Mongo or Postgres type. The family identity lives
 * entirely in the provider-supplied {@link BackendFamily} + expected class-name strings.
 *
 * @since 4.0.0
 */
public final class PersistenceBackendSelfCheck {

    /**
     * The HOCON key under each Pekko persistence plugin block that names its implementation class
     * (e.g. {@code pekko-contrib-mongodb-persistence-things-journal.class}).
     */
    private static final String CLASS_KEY = "class";

    private PersistenceBackendSelfCheck() {
        throw new AssertionError("nope");
    }

    /**
     * Verifies that the journal/snapshot plugin classes and the read-journal class the effective config resolves all
     * belong to the selected provider's backend family. See the class javadoc for the full contract.
     *
     * @param effectiveConfig the fully-resolved actor-system config (root scope — Pekko persistence plugin blocks such
     * as {@code pekko-contrib-mongodb-persistence-*} / {@code ditto-postgres-*} live here, not under {@code ditto}).
     * @param provider the resolved persistence backend provider, supplying the expected family + class-name strings.
     * @param serviceName the lower-case service name, e.g. {@code "things"}, {@code "policies"}, {@code "connectivity"}
     * — the key under which the provider's {@link PersistencePluginConfig#getAutoStartPluginIds(String)} returns this
     * service's journal/snapshot plugin IDs.
     * @param actualReadJournalClassName the runtime class name of the resolved read journal (the caller passes
     * {@code provider.getReadJournal().getClass().getName()}).
     * @throws DittoConfigError if any resolved plugin class or the read-journal class does not belong to the provider's
     * declared family (boot must fail fast), or if an auto-start plugin ID has no resolvable {@code class}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static void verify(final Config effectiveConfig,
            final PersistenceBackendProvider provider,
            final String serviceName,
            final String actualReadJournalClassName) {

        Objects.requireNonNull(effectiveConfig, "effectiveConfig");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(serviceName, "serviceName");
        Objects.requireNonNull(actualReadJournalClassName, "actualReadJournalClassName");

        final BackendFamily family = provider.backendFamily();
        final Set<String> expectedPluginClasses = provider.expectedPluginClassNames();
        final String expectedReadJournalClass = provider.expectedReadJournalClassName();

        final List<String> pluginIds = provider.pluginConfig().getAutoStartPluginIds(serviceName);
        for (final String pluginId : pluginIds) {
            final String actualPluginClass = resolvePluginClass(effectiveConfig, pluginId, family, serviceName);
            if (!expectedPluginClasses.contains(actualPluginClass)) {
                throw new DittoConfigError(String.format(
                        "Persistence backend mismatch: the selected provider declares backend family <%s>, but the "
                                + "effective config wires persistence plugin <%s> to class <%s>. Expected one of the "
                                + "<%s> plugin classes %s. The deployment HOCON still wires a different backend's "
                                + "plugins than the selected persistence-backend-provider — fix the "
                                + "ditto.extensions.persistence-backend-provider selection or the plugin <class = ...> "
                                + "wiring so both name the same backend.",
                        family, pluginId, actualPluginClass, family, expectedPluginClasses));
            }
        }

        if (!expectedReadJournalClass.equals(actualReadJournalClassName)) {
            throw new DittoConfigError(String.format(
                    "Persistence backend mismatch: the selected provider declares backend family <%s>, but its "
                            + "resolved read journal is of class <%s>. Expected the <%s> read journal class <%s>. The "
                            + "selected persistence-backend-provider does not match the wired read journal — fix the "
                            + "ditto.extensions.persistence-backend-provider selection so it matches the deployment's "
                            + "backend.",
                    family, actualReadJournalClassName, family, expectedReadJournalClass));
        }

        // Layer 3: the EFFECTIVE Pekko auto-start lists. Layers 1-2 validate the provider-declared
        // plugin ids; they cannot see a deployment whose pekko.persistence.* lists still auto-start
        // the OTHER backend's plugins (the classic partial-include misconfiguration). Every effective
        // auto-start entry must resolve to a class of the active backend family.
        verifyEffectiveAutoStartList(effectiveConfig, "pekko.persistence.journal.auto-start-journals",
                expectedPluginClasses, family, serviceName);
        verifyEffectiveAutoStartList(effectiveConfig,
                "pekko.persistence.snapshot-store.auto-start-snapshot-stores",
                expectedPluginClasses, family, serviceName);
    }

    private static void verifyEffectiveAutoStartList(final Config effectiveConfig, final String listPath,
            final Set<String> expectedPluginClasses, final BackendFamily family, final String serviceName) {
        if (!effectiveConfig.hasPath(listPath)) {
            return;
        }
        for (final String pluginId : effectiveConfig.getStringList(listPath)) {
            final String actualPluginClass = resolvePluginClass(effectiveConfig, pluginId, family, serviceName);
            if (!expectedPluginClasses.contains(actualPluginClass)) {
                throw new DittoConfigError(String.format(
                        "Persistence backend mismatch: the effective <%s> list auto-starts plugin <%s> "
                                + "(class <%s>), which does not belong to the selected backend family <%s> "
                                + "(expected one of %s). The deployment kept the other backend's "
                                + "pekko.persistence auto-start wiring — include the backend profile at a "
                                + "position where its pekko.persistence.* lists win, or fix the lists.",
                        listPath, pluginId, actualPluginClass, family, expectedPluginClasses));
            }
        }
    }

    private static String resolvePluginClass(final Config effectiveConfig, final String pluginId,
            final BackendFamily family, final String serviceName) {
        final String classPath = quotePath(pluginId) + "." + CLASS_KEY;
        if (!effectiveConfig.hasPath(classPath)) {
            throw new DittoConfigError(String.format(
                    "Persistence backend self-check could not resolve a <class> for auto-start persistence plugin <%s> "
                            + "(service <%s>, selected backend family <%s>): the effective config has no <%s>. The "
                            + "deployment HOCON is missing the plugin block the selected persistence-backend-provider "
                            + "expects — wire <%s { class = ... }> for the selected backend.",
                    pluginId, serviceName, family, classPath, pluginId));
        }
        return effectiveConfig.getString(classPath);
    }

    /**
     * Quotes a plugin ID for safe use as a single Typesafe-config path segment. Pekko plugin IDs contain dots in some
     * deployments and hyphens always; quoting prevents the dots from being interpreted as path separators.
     *
     * @param pluginId the plugin ID.
     * @return the quoted single-element path.
     */
    private static String quotePath(final String pluginId) {
        return "\"" + pluginId + "\"";
    }
}
