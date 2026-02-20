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

export interface ThingsConfig {
    count: number;
    startIndex: number;
    idTemplate: string;
    namespace: string;
    createBeforeTest: boolean;
    createBatchSize: number;
    deleteAfterTest: boolean;
}

export function getDefaultThingsConfig(): ThingsConfig {
    return {
        count: 10,
        startIndex: 1,
        idTemplate: 'test-thing-',
        namespace: 'org.eclipse.ditto',
        createBeforeTest: false,
        createBatchSize: 100,
        deleteAfterTest: false
    };
}
