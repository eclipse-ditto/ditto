/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.metrics.instruments;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;

/**
 * {@link MetricInstrument} which is able to be tagged with keys and values and can the tags can be read from.
 *
 * @param <T> the type of the MetricInstrument itself
 */
public interface TaggedMetricInstrument<T extends MetricInstrument> extends TaggableMetricsInstrument<T> {

    /**
     * Returns all tags of this TaggableMetricsInstrument.
     *
     * @return all tags.
     */
    TagSet getTagSet();

}
