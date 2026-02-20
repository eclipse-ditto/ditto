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

import { getConfig } from './common';
import { getThingId } from './config';

/**
 * Log manual cleanup instructions when automated cleanup fails
 */
export function logManualCleanupInstructions(
    thingsDeleted: boolean, connectionsDeleted: boolean,
    thingsCount: number, kafkaSourceConnectionId: string | null,
    kafkaTargetConnectionId: string | null,
    httpPushConnectionId: string | null): void {


    if (thingsDeleted && connectionsDeleted) {
        return;
    }

    const config = getConfig();
    const yamlLines: string[] = [];
    const envLines: string[] = [];

    // Cleanup mode
    yamlLines.push('cleanupOnly: true');
    envLines.push('CLEANUP_ONLY=1');

    if (!thingsDeleted) {
        yamlLines.push('things.createBeforeTest: false');
        yamlLines.push('things.deleteAfterTest: true');
        envLines.push('CREATE_THINGS=0');
        envLines.push('DELETE_THINGS=1');
    }

    if (!connectionsDeleted) {
        yamlLines.push('connections.createBeforeTest: false');
        yamlLines.push('connections.deleteAfterTest: true');
        envLines.push('CREATE_DITTO_CONNECTIONS=0');
        envLines.push('DELETE_DITTO_CONNECTIONS=1');

        if (kafkaSourceConnectionId) {
            yamlLines.push(`connections.preCreatedIds.kafkaSource: "${kafkaSourceConnectionId}"`);
            envLines.push(`KAFKA_SOURCE_CONNECTION_ID="${kafkaSourceConnectionId}"`);
        }
        if (kafkaTargetConnectionId) {
            yamlLines.push(`connections.preCreatedIds.kafkaTarget: "${kafkaTargetConnectionId}"`);
            envLines.push(`KAFKA_TARGET_CONNECTION_ID="${kafkaTargetConnectionId}"`);
        }
        if (httpPushConnectionId) {
            yamlLines.push(`connections.preCreatedIds.httpPush: "${httpPushConnectionId}"`);
            envLines.push(`HTTP_PUSH_CONNECTION_ID="${httpPushConnectionId}"`);
        }
    }

    console.error('');
    console.error('========================================================');
    console.error('  MANUAL CLEANUP REQUIRED');
    console.error('========================================================');

    if (!thingsDeleted) {
        console.error(`# Things to delete: ${thingsCount}`);
        console.error(`#   Range: ${getThingId(config, 0)} to ${getThingId(config, thingsCount - 1)}`);
    }

    console.error('');
    console.error('# ---- config.yml values (copy-paste) ----');
    yamlLines.forEach(line => console.error(line));

    console.error('');
    console.error('# ---- Environment variables (copy-paste) ----');
    envLines.forEach(line => console.error(`export ${line}`));

    console.error('');
    console.error('# Then re-run the test to perform cleanup.');
    console.error('========================================================');
    console.error('');
}
