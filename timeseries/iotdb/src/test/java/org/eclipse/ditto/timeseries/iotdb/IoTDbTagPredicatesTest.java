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
package org.eclipse.ditto.timeseries.iotdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.iotdb.IoTDbSql.TagType;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.junit.Test;

/**
 * Unit tests for {@link IoTDbTagPredicates}.
 */
public final class IoTDbTagPredicatesTest {

    private static final String KEY = "attributes/floor";
    private static final String S = IoTDbSql.tagColumn(KEY, TagType.STRING);
    private static final String N = IoTDbSql.tagColumn(KEY, TagType.NUMBER);
    private static final String B = IoTDbSql.tagColumn(KEY, TagType.BOOLEAN);
    private static final Set<String> ALL = Set.of(S, N, B);

    @Test
    public void stringTagFilterMatchesOnlyTheStringColumn() {
        assertThat(IoTDbTagPredicates.forTagFilters(Map.of(KEY, JsonValue.of("2")), ALL))
                .isEqualTo("(\"" + S + "\" IS NOT NULL AND \"" + S + "\" = '2')");
    }

    @Test
    public void numericTagFilterAlsoMatchesItsStringForm() {
        assertThat(IoTDbTagPredicates.forTagFilters(Map.of(KEY, JsonValue.of(2)), ALL))
                .isEqualTo("((\"" + N + "\" IS NOT NULL AND \"" + N + "\" = 2.0) OR " +
                        "(\"" + S + "\" IS NOT NULL AND \"" + S + "\" = '2'))");
    }

    @Test
    public void booleanTagFilterAlsoMatchesItsStringForm() {
        assertThat(IoTDbTagPredicates.forTagFilters(Map.of(KEY, JsonValue.of(true)), Set.of(B)))
                .isEqualTo("(\"" + B + "\" IS NOT NULL AND \"" + B + "\" = true)");
    }

    @Test
    public void tagFilterOnANeverIngestedKeyMatchesNothing() {
        assertThat(IoTDbTagPredicates.forTagFilters(Map.of("unknown", JsonValue.of("x")), ALL))
                .isEqualTo(IoTDbTagPredicates.FALSE);
    }

    @Test
    public void tagFiltersAreConjoinedAndEmptyMeansTrue() {
        final String other = IoTDbSql.tagColumn("attributes/building", TagType.STRING);
        final Map<String, JsonValue> filters = new LinkedHashMap<>();
        filters.put(KEY, JsonValue.of("2"));
        filters.put("attributes/building", JsonValue.of("A"));
        assertThat(IoTDbTagPredicates.forTagFilters(filters, Set.of(S, other)))
                .isEqualTo("((\"" + S + "\" IS NOT NULL AND \"" + S + "\" = '2') AND " +
                        "(\"" + other + "\" IS NOT NULL AND \"" + other + "\" = 'A'))");
        assertThat(IoTDbTagPredicates.forTagFilters(Map.of(), ALL)).isEqualTo(IoTDbTagPredicates.TRUE);
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class).isThrownBy(() ->
                IoTDbTagPredicates.forTagFilters(Map.of(KEY, JsonObject.empty()), ALL));
    }

    @Test
    public void tagValuesAreEscapedLiterals() {
        assertThat(IoTDbTagPredicates.forTagFilters(Map.of(KEY, JsonValue.of("x' OR '1'='1")), ALL))
                .endsWith("= 'x'' OR ''1''=''1')");
    }

    @Test
    public void rqlComparisonsMatchOnlyTheLiteralsType() {
        assertThat(IoTDbTagPredicates.forRql("eq(attributes/floor,2)", ALL))
                .isEqualTo("(\"" + N + "\" IS NOT NULL AND \"" + N + "\" = 2.0)");
        assertThat(IoTDbTagPredicates.forRql("ge(/attributes/floor,'2')", ALL))
                .isEqualTo("(\"" + S + "\" IS NOT NULL AND \"" + S + "\" >= '2')");
        assertThat(IoTDbTagPredicates.forRql("eq(attributes/floor,false)", ALL))
                .isEqualTo("(\"" + B + "\" IS NOT NULL AND \"" + B + "\" = false)");
    }

    @Test
    public void negationAlsoMatchesPointsWithoutTheTag() {
        assertThat(IoTDbTagPredicates.forRql("ne(attributes/floor,2)", ALL))
                .isEqualTo("(NOT (\"" + N + "\" IS NOT NULL AND \"" + N + "\" = 2.0))");
        assertThat(IoTDbTagPredicates.forRql("not(eq(attributes/missing,2))", ALL))
                .isEqualTo(IoTDbTagPredicates.TRUE);
    }

    @Test
    public void nullExistsAndInAreSupported() {
        assertThat(IoTDbTagPredicates.forRql("eq(attributes/floor,null)", Set.of(S, N)))
                .isEqualTo("(\"" + S + "\" IS NULL AND \"" + N + "\" IS NULL)");
        assertThat(IoTDbTagPredicates.forRql("exists(attributes/floor)", Set.of(N)))
                .isEqualTo("(NOT \"" + N + "\" IS NULL)");
        assertThat(IoTDbTagPredicates.forRql("exists(attributes/missing)", ALL))
                .isEqualTo(IoTDbTagPredicates.FALSE);
        assertThat(IoTDbTagPredicates.forRql("in(attributes/floor,1,'1')", ALL))
                .isEqualTo("((\"" + N + "\" IS NOT NULL AND \"" + N + "\" = 1.0) OR " +
                        "(\"" + S + "\" IS NOT NULL AND \"" + S + "\" = '1'))");
    }

    @Test
    public void likeAndIlikeTranslateWildcards() {
        assertThat(IoTDbTagPredicates.forRql("like(attributes/floor,'B*_?')", ALL))
                .isEqualTo("(\"" + S + "\" IS NOT NULL AND \"" + S + "\" LIKE 'B%\\__' ESCAPE '\\')");
        assertThat(IoTDbTagPredicates.forRql("ilike(attributes/floor,'Ab*')", ALL))
                .isEqualTo("(\"" + S + "\" IS NOT NULL AND lower(\"" + S + "\") LIKE 'ab%' ESCAPE '\\')");
    }

    @Test
    public void logicalOperatorsFoldConstants() {
        assertThat(IoTDbTagPredicates.forRql("and(eq(attributes/missing,1),eq(attributes/floor,1))", ALL))
                .isEqualTo(IoTDbTagPredicates.FALSE);
        assertThat(IoTDbTagPredicates.forRql("or(eq(attributes/missing,1),eq(attributes/floor,'1'))", ALL))
                .isEqualTo("(\"" + S + "\" IS NOT NULL AND \"" + S + "\" = '1')");
    }

    @Test
    public void unsupportedConstructsAreRejected() {
        for (final String rql : new String[]{"eq(attributes/floor", "gt(attributes/floor,true)",
                "lt(attributes/floor,null)", "like(attributes/floor,1)", "eq(,1)"}) {
            assertThatExceptionOfType(TimeseriesQueryInvalidException.class).as(rql)
                    .isThrownBy(() -> IoTDbTagPredicates.forRql(rql, ALL));
        }
    }
}
