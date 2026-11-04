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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;

/**
 * D1 validation test: asserts that {@link MongoPersistencePluginConfig#getAutoStartPluginIds(String)} returns the SAME
 * set as the union of {@code pekko.persistence.journal.auto-start-journals} and
 * {@code pekko.persistence.snapshot-store.auto-start-snapshot-stores} from each service's shipped
 * {@code <service>.conf}.
 *
 * <h2>Purpose (Task D1)</h2>
 * The static lists in Pekko's HOCON remain authoritative — Pekko reads them at boot. The Java constants in
 * {@link MongoPersistencePluginConfig} are used by the D1 validator and the G2 boot self-check. This test is the
 * regression guard that catches DRIFT between the Java constants and the shipped HOCON: if any service's
 * {@code auto-start-*} list is updated in HOCON without a corresponding update to the Java constants (or vice versa),
 * this test fails.
 *
 * <h2>How it loads the config</h2>
 * The service conf files are NOT on the test classpath of this module (the service modules do not depend on
 * {@code internal/utils/persistence}). They are loaded directly from the source tree using
 * {@link ConfigFactory#parseFile(File, ConfigParseOptions)}, with the file located by walking up from
 * {@code user.dir} (the Maven surefire working directory, i.e. this module's directory) until the repo root is
 * found — the same strategy used by {@code PostgresPersistenceBackendProviderTest}. This keeps the assertion against
 * the real, shipped config rather than a copy or fixture.
 *
 * <h2>WoT exclusion</h2>
 * The {@code pekko-contrib-mongodb-persistence-wot-validation-config-*} IDs appear in {@code things.conf} as Pekko
 * plugin blocks but are intentionally NOT in the {@code pekko.persistence.*.auto-start-*} lists (WoT is not
 * auto-started). The test explicitly asserts that neither WoT ID appears in the things auto-start set, mirroring the
 * intentional exclusion documented in {@code MongoPersistencePluginConfig}.
 *
 * @since 4.0.0
 */
@RunWith(Parameterized.class)
public final class MongoAutoStartIdsMatchServiceHoconTest {

    private static final String JOURNAL_AUTO_START_PATH = "pekko.persistence.journal.auto-start-journals";
    private static final String SNAPSHOT_AUTO_START_PATH =
            "pekko.persistence.snapshot-store.auto-start-snapshot-stores";

    private static ActorSystem system;
    private static MongoPersistencePluginConfig pluginConfig;

    @BeforeClass
    public static void setUpClass() {
        final Config extensionConfig =
                ScopedConfig.dittoExtension(ConfigFactory.load("reference"))
                        .getConfig("persistence-backend-provider.extension-config");
        system = ActorSystem.create("MongoAutoStartIdsMatchServiceHoconTest", ConfigFactory.load("reference"));
        final MongoPersistenceBackendProvider provider = new MongoPersistenceBackendProvider(system, extensionConfig);
        pluginConfig = MongoPersistencePluginConfig.of(provider);
    }

    @AfterClass
    public static void tearDownClass() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    /**
     * Each parameter row: [serviceName, confFileName]
     * The conf file name differs for connectivity (service name = "connectivity", conf = "connectivity.conf").
     * For things and policies the service name matches the conf name.
     */
    @Parameterized.Parameters(name = "service={0}")
    public static List<Object[]> parameters() {
        return List.of(
                new Object[]{"things",        "things"},
                new Object[]{"policies",      "policies"},
                new Object[]{"connectivity",  "connectivity"}
        );
    }

    @Parameterized.Parameter(0)
    public String serviceName;

    @Parameterized.Parameter(1)
    public String confBaseName;

    // ── main D1 assertion ─────────────────────────────────────────────────────

    /**
     * Loads the service's shipped {@code <service>.conf} from the source tree, reads the two
     * {@code auto-start-*} lists, and asserts they equal {@code pluginConfig.getAutoStartPluginIds(serviceName)}.
     */
    @Test
    public void autoStartPluginIdsMatchShippedServiceHocon() {
        final List<String> hoconAutoStartIds = loadHoconAutoStartIds(confBaseName);
        final List<String> javaAutoStartIds = pluginConfig.getAutoStartPluginIds(serviceName);

        assertThat(javaAutoStartIds)
                .as("D1: MongoPersistencePluginConfig.getAutoStartPluginIds(\"%s\") must equal "
                                + "the union of pekko.persistence.journal.auto-start-journals + "
                                + "pekko.persistence.snapshot-store.auto-start-snapshot-stores "
                                + "from %s.conf (drift guard)",
                        serviceName, confBaseName)
                .containsExactlyInAnyOrderElementsOf(hoconAutoStartIds);
    }

    // ── WoT exclusion guard (things only) ────────────────────────────────────

    /**
     * Extra guard for the things service: the WoT validation-config Pekko plugin blocks exist in things.conf but
     * are intentionally absent from the auto-start lists. This asserts that {@code getAutoStartPluginIds("things")}
     * does NOT contain either WoT ID, catching any future accidental inclusion.
     */
    @Test
    public void thingsAutoStartSetDoesNotContainWot() {
        if (!"things".equals(serviceName)) {
            return; // WoT guard applies only to things
        }
        assertThat(pluginConfig.getAutoStartPluginIds("things"))
                .as("D1: WoT plugin IDs must NOT be in the things auto-start set "
                        + "(intentionally excluded per things.conf — WoT is not auto-started)")
                .doesNotContain(
                        "pekko-contrib-mongodb-persistence-wot-validation-config-journal",
                        "pekko-contrib-mongodb-persistence-wot-validation-config-snapshots");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses the shipped {@code <confBaseName>.conf} from the source tree WITHOUT resolution and reads the two
     * {@code pekko.persistence.*.auto-start-*} string lists directly from the unresolved parse tree.
     *
     * <p>Why no full resolution: connectivity.conf includes {@code connectivity-extension.conf} which contains a
     * self-referential list append ({@code [...] ${ditto.extensions.signal-transformers-provider...}}) that
     * creates a HOCON cyclic substitution. Calling {@code config.resolve()} — even with
     * {@code setAllowUnresolved(true)} — trips over this cycle. Since the auto-start lists themselves contain
     * only string literals (no {@code ${...}} placeholders), we can read them directly from the unresolved
     * parse tree with {@code getStringList()} without triggering any substitution evaluation.
     */
    private static List<String> loadHoconAutoStartIds(final String confBaseName) {
        final File confFile = locateServiceConf(confBaseName);
        // Parse without resolution: the auto-start lists are plain string literals — no substitution needed.
        // Do NOT call .resolve() here: connectivity.conf (via connectivity-extension.conf) contains a cyclic
        // HOCON substitution that causes ConfigException.UnresolvedSubstitution even with setAllowUnresolved(true).
        final Config raw = ConfigFactory.parseFile(confFile,
                ConfigParseOptions.defaults().setAllowMissing(false));

        final List<String> ids = new ArrayList<>();
        if (raw.hasPath(JOURNAL_AUTO_START_PATH)) {
            ids.addAll(raw.getStringList(JOURNAL_AUTO_START_PATH));
        }
        if (raw.hasPath(SNAPSHOT_AUTO_START_PATH)) {
            ids.addAll(raw.getStringList(SNAPSHOT_AUTO_START_PATH));
        }
        return ids;
    }

    /**
     * Walks up from the Maven surefire working directory ({@code user.dir}, the module directory) to the repo root
     * and resolves the service's {@code src/main/resources/<confBaseName>.conf}. The service modules do not depend on
     * this module, so the conf is not on the test classpath; reading from the source tree keeps the assertion against
     * the real, shipped config.
     */
    private static File locateServiceConf(final String confBaseName) {
        // Connectivity's conf is in connectivity/service/src/main/resources/connectivity.conf.
        // Things and policies follow the same <service>/service/src/main/resources/<service>.conf pattern.
        final String relative = confBaseName + "/service/src/main/resources/" + confBaseName + ".conf";
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            final File candidate = new File(dir, relative);
            if (candidate.isFile()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        throw new IllegalStateException(
                "Could not locate " + relative + " from user.dir=" + System.getProperty("user.dir")
                        + " — ensure the test is run from within the ditto source tree");
    }
}
