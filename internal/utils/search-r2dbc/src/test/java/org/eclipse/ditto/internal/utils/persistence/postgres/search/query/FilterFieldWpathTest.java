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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.Test;

/**
 * Unit tests for {@link FilterFieldWpath}: the {@code search_flat.wpath} a filter on a field compares against must be
 * exactly the value the translator binds, or a partial index on that wpath is never provable for the query.
 */
public final class FilterFieldWpathTest {

    private static final ThingsFieldExpressionFactory FEF = ThingsFieldExpressionFactory.of(Map.of(
            "thingId", "_id",
            "namespace", "_namespace",
            "policyId", "/policyId",
            "definition", "/definition",
            "_modified", "/_modified"));
    private static final CriteriaFactory CF = CriteriaFactory.getInstance();

    @Test
    public void attributePath() {
        assertThat(wpathOf("attributes/location/city")).isEqualTo("/attributes/location/city");
    }

    @Test
    public void leadingSlashIsAccepted() {
        assertThat(wpathOf("/attributes/location/city")).isEqualTo("/attributes/location/city");
    }

    @Test
    public void namedFeatureProperty() {
        assertThat(wpathOf("features/env/properties/temperature"))
                .isEqualTo("/features/env/properties/temperature");
    }

    @Test
    public void wildcardFeatureProperty() {
        assertThat(wpathOf("features/*/properties/prop0")).isEqualTo("/features/*/properties/prop0");
    }

    @Test
    public void wildcardFeatureDesiredProperty() {
        assertThat(wpathOf("features/*/desiredProperties/target"))
                .isEqualTo("/features/*/desiredProperties/target");
    }

    @Test
    public void wildcardFeatureDefinition() {
        assertThat(wpathOf("features/*/definition")).isEqualTo("/features/*/definition");
    }

    @Test
    public void slashMappedSimpleField() {
        assertThat(wpathOf("policyId")).isEqualTo("/policyId");
    }

    @Test
    public void tildeEscapesAreEncodedLikeTheFlattener() {
        // Ditto's JsonPointer decodes ~0 to '~' but keeps ~1 as the two characters '~' '1' (its keys cannot contain
        // '/'); JsonPointerSegments then escapes every '~' again. Same as the translator (pinned below).
        assertThat(wpathOf("attributes/a~1b/c~0d")).isEqualTo("/attributes/a~01b/c~0d");
    }

    @Test
    public void rootMappedFieldsHaveNoWpath() {
        assertThatThrownBy(() -> wpathOf("thingId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("_id");
        assertThatThrownBy(() -> wpathOf("namespace"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("_namespace");
    }

    @Test
    public void wpathIsExactlyTheValueTheTranslatorBindsForIlike() {
        for (final String field : List.of("attributes/location/city", "/attributes/vendor",
                "features/env/properties/temperature", "features/*/properties/prop0",
                "features/*/desiredProperties/target", "features/f1/desiredProperties/x/y", "features/*/definition",
                "attributes/a~1b/c~0d", "attributes/it's/a\\b", "policyId", "_modified")) {
            final FilterFieldExpression expression = FEF.filterBy(field);
            final RenderedSql rendered =
                    SearchQueryAssembler.count(CF.fieldCriteria(expression, CF.ilike("*abc*")), null, 0, 0);
            assertThat(rendered.bindValues())
                    .as("binds of ilike(%s): %s", field, rendered.sql())
                    .contains(FilterFieldWpath.of(expression));
            assertThat(rendered.sql()).as(field).containsPattern("\\b[a-z]+\\.wpath = \\$\\d+");
        }
    }

    private static String wpathOf(final String field) {
        return FilterFieldWpath.of(FEF.filterBy(field));
    }

}
