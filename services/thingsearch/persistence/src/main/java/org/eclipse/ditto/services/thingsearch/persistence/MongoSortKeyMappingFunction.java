/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
