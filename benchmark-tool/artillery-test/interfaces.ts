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

/**
 * Scenario interface - defines the contract for all test scenarios
 */
export interface Scenario {
    execute(context: any, events: any): Promise<void>;
}

/**
 * Channel that supports reading things
 */
export interface ThingReader {
    getThing(params: { thingId: string }): Promise<any>;
}

/**
 * Channel that supports searching things
 */
export interface ThingSearcher {
    searchThings(params: { filter: string }): Promise<any>;
}

/**
 * Channel that supports modifying things
 */
export interface ThingModifier {
    modifyThing(params: { thingId: string; updates: { path: string; value: any }; timeout?: number }): Promise<any>;
}

/**
 * Channel that supports sending live messages
 */
export interface LiveMessenger {
    sendLiveMessage(params: { thingId: string; subject: string; payload: any; timeout?: number }): Promise<any>;
}
