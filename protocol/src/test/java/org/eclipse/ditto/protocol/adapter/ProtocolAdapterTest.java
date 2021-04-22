/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter;

import org.assertj.core.api.AbstractObjectAssert;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;

/**
 * Super class of protocol adapter tests.
 */
public interface ProtocolAdapterTest {

    /**
     * Make equality assertions on {@link org.eclipse.ditto.base.model.headers.WithDittoHeaders} comparing only
     * the external headers.
     *
     * @param actual a signal.
     * @return an Assert object for the signal.
     */
    default AbstractObjectAssert<?, DittoHeadersSettable<?>> assertWithExternalHeadersThat(
            final DittoHeadersSettable<?> actual) {
        return new WithFilteredHeadersAssert(actual, DittoProtocolAdapter.getHeaderTranslator(), true);
    }

    /**
     * Assert for {@link org.eclipse.ditto.base.model.headers.WithDittoHeaders} comparing only the external headers.
     */
    final class WithFilteredHeadersAssert
            extends AbstractObjectAssert<WithFilteredHeadersAssert, DittoHeadersSettable<?>> {

        private final HeaderTranslator headerTranslator;
        private final boolean filterSelf;

        private WithFilteredHeadersAssert(final DittoHeadersSettable<?> withDittoHeaders,
                final HeaderTranslator headerTranslator, final boolean filterSelf) {
            super(withDittoHeaders, WithFilteredHeadersAssert.class);
            this.headerTranslator = headerTranslator;
            this.filterSelf = filterSelf;
        }

        @Override
        public WithFilteredHeadersAssert isEqualTo(final Object that) {
            if (that instanceof DittoHeadersSettable<?>) {
                if (filterSelf) {
                    final DittoHeaders filteredHeaders =
                            DittoHeaders.of(headerTranslator.toExternalHeaders(actual.getDittoHeaders()));
                    final DittoHeadersSettable<?> actualWithFilteredHeaders = actual.setDittoHeaders(filteredHeaders);
                    return new WithFilteredHeadersAssert(actualWithFilteredHeaders, headerTranslator, false)
                            .isEqualTo(that);
                } else {
                    final DittoHeadersSettable<?> expected = (DittoHeadersSettable<?>) that;
                    final DittoHeaders filteredHeaders =
                            DittoHeaders.of(headerTranslator.toExternalHeaders(actual.getDittoHeaders()));
                    return super.isEqualTo(expected.setDittoHeaders(filteredHeaders));
                }
            } else {
                return super.isEqualTo(that);
            }
        }
    }
}
