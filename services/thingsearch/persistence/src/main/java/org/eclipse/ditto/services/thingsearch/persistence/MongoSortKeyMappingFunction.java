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
package org.eclipse.ditto.services.thingsearch.persistence;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.DOT;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.thingsearch.common.util.KeyEscapeUtil;


/**
 * <pre>
 * This function does (in this order)
 *  * encode each element of the array (real . and $ with Unicode chanracter)
 *  * replace / (slash) with . (dot, the real one)
 *  * join the elements using a . (dot, the real one)
 *  </pre>
 */
public class MongoSortKeyMappingFunction implements Function<String[], String> {

    private static final MongoSortKeyMappingFunction INSTANCE = new MongoSortKeyMappingFunction();

    @Override
    public String apply(final String[] values) {
        return Arrays.stream(values)
                .map(KeyEscapeUtil::escape)
                .map(s -> s.replace('/', '.'))
                .collect(Collectors.joining(DOT));
    }

    public static String mapSortKey(String... values) {
        return INSTANCE.apply(values);
    }

}
