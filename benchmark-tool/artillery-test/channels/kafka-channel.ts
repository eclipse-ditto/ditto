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

import * as kafkaUtil from '../kafka-util';
import { ThingModifier } from '../interfaces';

/**
 * Kafka Channel - Uses Kafka for asynchronous thing modifications
 * Supports only: modifyThing
 */
export class KafkaChannel implements ThingModifier {

    /**
     * Modify a thing via Kafka
     * @param params - { thingId, updates, timeout }
     */
    async modifyThing({ thingId, updates, timeout }: { thingId: string; updates: { path: string; value: any }; timeout?: number }): Promise<any> {
        return await kafkaUtil.sendModifyThing(thingId, updates, timeout);
    }
}
