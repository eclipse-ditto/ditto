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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlBindType;
import org.junit.Test;

/**
 * Unit tests for {@link SqlValues}: the RQL-value → typed flat-column routing, and the guard that a {@code null}
 * operand never reaches a bind — {@code CreateSqlPredicateVisitor} dispatches every null operand ({@code eq},
 * {@code ne}, {@code in}, {@code ge}, {@code gt}, {@code le}, {@code lt}, a placeholder resolved to null) before
 * binding, so a {@code null} here is a translator bug, not a value to render as the text {@code 'null'}.
 */
public final class SqlValuesTest {

    @Test
    public void bindRejectsNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SqlValues.bind(null))
                .withMessageContaining("null");
    }

    @Test
    public void valueColumnRejectsNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SqlValues.valueColumn(null))
                .withMessageContaining("null");
    }

    @Test
    public void valueColumnRoutesByJavaType() {
        assertThat(SqlValues.valueColumn(true)).isEqualTo(SqlValues.VAL_BOOL);
        assertThat(SqlValues.valueColumn(7)).isEqualTo(SqlValues.VAL_NUM);
        assertThat(SqlValues.valueColumn(7L)).isEqualTo(SqlValues.VAL_NUM);
        assertThat(SqlValues.valueColumn(7.5)).isEqualTo(SqlValues.VAL_NUM);
        assertThat(SqlValues.valueColumn(new BigDecimal("7.25"))).isEqualTo(SqlValues.VAL_NUM);
        assertThat(SqlValues.valueColumn("7")).isEqualTo(SqlValues.VAL_TEXT);
        assertThat(SqlValues.valueColumn("null")).isEqualTo(SqlValues.VAL_TEXT);
    }

    @Test
    public void bindPreservesIntegralLongDoubleAndDecimalExactly() {
        assertThat(bind(Integer.MAX_VALUE)).containsExactly(SqlBindType.NUMERIC, BigDecimal.valueOf(Integer.MAX_VALUE));
        assertThat(bind(9_000_000_000L)).containsExactly(SqlBindType.NUMERIC, BigDecimal.valueOf(9_000_000_000L));
        assertThat(bind(3.5)).containsExactly(SqlBindType.NUMERIC, BigDecimal.valueOf(3.5));
        assertThat(bind(new BigDecimal("0.10"))).containsExactly(SqlBindType.NUMERIC, new BigDecimal("0.10"));
        assertThat(bind(false)).containsExactly(SqlBindType.BOOLEAN, false);
        assertThat(bind("null")).containsExactly(SqlBindType.TEXT, "null");
    }

    @Test
    public void toBigDecimalKeepsTheNumericSubtypeApart() {
        assertThat(SqlValues.toBigDecimal(42)).isEqualTo(BigDecimal.valueOf(42L));
        assertThat(SqlValues.toBigDecimal((short) 3)).isEqualTo(BigDecimal.valueOf(3L));
        assertThat(SqlValues.toBigDecimal(2.5f)).isEqualTo(BigDecimal.valueOf(2.5));
        assertThat(SqlValues.toBigDecimal(new BigDecimal("1E+2"))).isEqualTo(new BigDecimal("1E+2"));
    }

    private static Object[] bind(final Object value) {
        final RenderedSql rendered = Sql.render(SqlValues.bind(value));
        return new Object[]{rendered.binds().get(0).type(), rendered.bindValues().get(0)};
    }
}
