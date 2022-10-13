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

import java.util.Map;
import java.util.Optional;

/**
 * {@link MetricInstrument} which is able to be tagged with keys and values and can the tags can be read from.
 *
 * @param <T> the type of the MetricInstrument itself
 */
public interface TaggedMetricInstrument<T extends MetricInstrument> extends TaggableMetricsInstrument<T> {

    /**
     * Gets the value of the tag with the given key.
     *
     * @param key the key of the tag.
     * @return an Optional containing the value of the tag with the given key or an empty Optional if no value is
     * associated with {@code key}.
     */
    Optional<String> getTag(String key);

    /**
     * Gets the map containing all tags.
     *
     * @return All tags.
     */
    Map<String, String> getTags();

}
