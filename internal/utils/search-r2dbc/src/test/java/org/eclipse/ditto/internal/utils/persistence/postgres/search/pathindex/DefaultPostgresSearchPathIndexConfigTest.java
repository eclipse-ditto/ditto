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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Unit tests for {@link DefaultPostgresSearchPathIndexConfig}: the per-path list (HOCON list and the env-var string
 * form), the direct URI, defaults and validation.
 */
public final class DefaultPostgresSearchPathIndexConfigTest {

    private static final Map<String, String> SIMPLE_FIELD_MAPPINGS = Map.of(
            "thingId", "_id",
            "namespace", "_namespace",
            "policyId", "/policyId");

    @Test
    public void defaultsApplyWhenTheWholeBlockIsAbsent() {
        final PostgresSearchPathIndexConfig config = of(ConfigFactory.empty());

        assertThat(config.isPathListConfigured()).isFalse();
        assertThat(config.getPathIndexes()).isEmpty();
        assertThat(config.getDirectUri()).isEmpty();
    }

    @Test
    public void shippedProfileDefaultsToNoIndexesAndNoDirectUri() {
        final Config shipped = ConfigFactory.load("postgres-search-test.conf").getConfig("ditto");
        final PostgresSearchPathIndexConfig config = of(shipped);

        // the shipped profile leaves the list unset: per-path indexes are not managed unless an operator sets it
        assertThat(config.isPathListConfigured()).isFalse();
        assertThat(config.getPathIndexes()).isEmpty();
        assertThat(config.getDirectUri()).isEmpty();
    }

    @Test
    public void hoconListOfPathsAndKinds() {
        final PostgresSearchPathIndexConfig config = of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.paths = [\n"
                        + "  { path = \"features/*/properties/prop0\", kind = \"trigram\" }\n"
                        + "  { path = \"attributes/location/city\", kind = \"trigram\" }\n"
                        + "]\n"));

        assertThat(config.isPathListConfigured()).isTrue();
        assertThat(config.getPathIndexes()).extracting(PathIndex::wpath)
                .containsExactly("/features/*/properties/prop0", "/attributes/location/city");
        assertThat(config.getPathIndexes()).extracting(PathIndex::kind)
                .containsOnly(PathIndexKind.TRIGRAM);
    }

    @Test
    public void envVariableStringFormIsAJsonArray() {
        // what `paths = ${?POSTGRES_SEARCH_PATH_INDEXES}` yields when the env var is set: a STRING value
        final Config raw = ConfigFactory.empty().withValue("postgresql.search.path-indexes.paths",
                ConfigValueFactory.fromAnyRef(
                        "[{\"path\":\"features/*/properties/prop0\",\"kind\":\"trigram\"}]"));

        assertThat(of(raw).getPathIndexes()).extracting(PathIndex::wpath)
                .containsExactly("/features/*/properties/prop0");
    }

    @Test
    public void blankEnvVariableStringMeansNotConfigured() {
        // e.g. a Helm template that renders POSTGRES_SEARCH_PATH_INDEXES="" must not drop every per-path index
        final Config raw = ConfigFactory.empty().withValue("postgresql.search.path-indexes.paths",
                ConfigValueFactory.fromAnyRef("  "));

        assertThat(of(raw).isPathListConfigured()).isFalse();
        assertThat(of(raw).getPathIndexes()).isEmpty();
    }

    @Test
    public void explicitEmptyListIsConfiguredAndEmpty() {
        final PostgresSearchPathIndexConfig config =
                of(ConfigFactory.parseString("postgresql.search.path-indexes.paths = []\n"));

        assertThat(config.isPathListConfigured()).isTrue();
        assertThat(config.getPathIndexes()).isEmpty();
    }

    @Test
    public void envVariableWithAnEmptyJsonArrayIsConfiguredAndEmpty() {
        final Config raw = ConfigFactory.empty().withValue("postgresql.search.path-indexes.paths",
                ConfigValueFactory.fromAnyRef("[]"));

        assertThat(of(raw).isPathListConfigured()).isTrue();
        assertThat(of(raw).getPathIndexes()).isEmpty();
    }

    @Test
    public void nullValueMeansNotConfigured() {
        assertThat(of(ConfigFactory.parseString("postgresql.search.path-indexes.paths = null\n"))
                .isPathListConfigured()).isFalse();
    }

    @Test
    public void systemPropertyStyleNumericKeysBecomeAList() {
        // -Dditto.postgresql.search.path-indexes.paths.0.path=... arrives as an object with numeric keys
        final Config raw = ConfigFactory.parseString(
                "postgresql.search.path-indexes.paths.0.path = \"attributes/vendor\"\n"
                        + "postgresql.search.path-indexes.paths.0.kind = trigram\n");

        assertThat(of(raw).getPathIndexes()).extracting(PathIndex::wpath).containsExactly("/attributes/vendor");
    }

    @Test
    public void directUri() {
        final PostgresSearchPathIndexConfig config = of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.direct-uri = \"r2dbc:postgresql://db-direct:5432/ditto\"\n"));

        assertThat(config.getDirectUri()).contains("r2dbc:postgresql://db-direct:5432/ditto");
    }

    @Test
    public void malformedDirectUriIsRejected() {
        assertThatThrownBy(() -> of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.direct-uri = \"db-direct:5432/ditto\"\n")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("direct-uri");
    }

    @Test
    public void directUriOfAnotherDriverIsRejected() {
        assertThatThrownBy(() -> of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.direct-uri = \"r2dbc:mysql://db-direct:3306/ditto\"\n")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("postgresql");
    }

    @Test
    public void directUriWithCredentialsIsNotPrintedByToString() {
        final PostgresSearchPathIndexConfig config = of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.direct-uri = \"r2dbc:postgresql://u:secret@db-direct:5432/ditto\"\n"));

        assertThat(config.toString()).doesNotContain("secret").contains("directUriConfigured=true");
    }

    @Test
    public void blankDirectUriMeansNone() {
        assertThat(of(ConfigFactory.parseString("postgresql.search.path-indexes.direct-uri = \" \"\n"))
                .getDirectUri()).isEmpty();
    }

    @Test
    public void missingKindIsRejected() {
        assertThatThrownBy(() -> of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.paths = [ { path = \"attributes/vendor\" } ]\n")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("kind")
                .hasMessageContaining("trigram");
    }

    @Test
    public void unknownKindIsRejected() {
        assertThatThrownBy(() -> of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.paths = [ { path = \"attributes/vendor\", kind = btree } ]\n")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("btree")
                .hasMessageContaining("trigram");
    }

    @Test
    public void missingPathIsRejected() {
        assertThatThrownBy(() -> of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.paths = [ { kind = trigram } ]\n")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("path");
    }

    @Test
    public void invalidPathIsRejectedWithItsPosition() {
        assertThatThrownBy(() -> of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.paths = [\n"
                        + "  { path = \"attributes/vendor\", kind = trigram }\n"
                        + "  { path = \"thingId\", kind = trigram }\n"
                        + "]\n")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("paths[1]")
                .hasMessageContaining("thingId");
    }

    @Test
    public void duplicatePathAndKindIsRejected() {
        assertThatThrownBy(() -> of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.paths = [\n"
                        + "  { path = \"attributes/vendor\", kind = trigram }\n"
                        + "  { path = \"/attributes/vendor\", kind = trigram }\n"
                        + "]\n")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("duplicate")
                .hasMessageContaining("/attributes/vendor");
    }

    @Test
    public void malformedEnvVariableStringIsRejected() {
        final Config raw = ConfigFactory.empty().withValue("postgresql.search.path-indexes.paths",
                ConfigValueFactory.fromAnyRef("[{\"path\": "));

        assertThatThrownBy(() -> of(raw))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("POSTGRES_SEARCH_PATH_INDEXES");
    }

    @Test
    public void nonListValueIsRejected() {
        assertThatThrownBy(() -> of(ConfigFactory.parseString("postgresql.search.path-indexes.paths = 42\n")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("list");
    }

    @Test
    public void entryThatIsNotAnObjectIsRejected() {
        assertThatThrownBy(() -> of(ConfigFactory.parseString(
                "postgresql.search.path-indexes.paths = [ \"attributes/vendor\" ]\n")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("paths[0]");
    }

    private static PostgresSearchPathIndexConfig of(final Config dittoConfig) {
        return DefaultPostgresSearchPathIndexConfig.of(dittoConfig, SIMPLE_FIELD_MAPPINGS);
    }

}
