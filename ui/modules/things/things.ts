/* eslint-disable new-cap */
/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
/* eslint-disable require-jsdoc */
// @ts-check
import * as API from '../api.js';

import * as Utils from '../utils.js';
import { TabHandler } from '../utils/tabHandler.js';
import thingsHTML from './things.html';
import * as ThingsSearch from './thingsSearch.js';

export let theThing;
export let historyModeActive = false;

const observers = [];
const historyModeObservers = [];

const dom = {
  collapseThings: null,
  tabThings: null,
};

document.getElementById('thingsHTML').innerHTML = thingsHTML;


/**
 * Adds a listener function for the currently selected thing
 * @param {function} observer function that will be called if the current thing was changed
 */
export function addChangeListener(observer) {
  observers.push(observer);
}

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Utils.getAllElementsById(dom);
  TabHandler(dom.tabThings, dom.collapseThings, refreshView);
}

/**
 * Load thing from Ditto and update all UI components
 * @param {string} thingId ThingId
 * @param {function} successCallback callback function that is called after refresh is finished
 */
export function refreshThing(thingId: string, successCallback = null) {
  console.assert(thingId && thingId !== '', 'thingId expected');
  API.callDittoREST('GET',
      `/things/${thingId}?` +
      'fields=thingId%2CpolicyId%2Cdefinition%2Cattributes%2Cfeatures%2C_created%2C_modified%2C_revision%2C_metadata')
      .then((thing) => {
        setTheThing(thing);
        successCallback && successCallback();
      })
      .catch(() => setTheThing(null));
}

/**
 * Update all UI components for the given Thing
 * @param {Object} thingJson Thing json
 */
export function setTheThing(thingJson) {
  const isNewThingId = thingJson && (!theThing || theThing.thingId !== thingJson.thingId);
  theThing = thingJson;
  observers.forEach((observer) => observer.call(null, theThing, isNewThingId));
}

function refreshView() {
  ThingsSearch.performLastSearch();
}

const HISTORY_FIELDS = 'fields=thingId%2CpolicyId%2Cdefinition%2Cattributes%2Cfeatures%2C_created%2C_modified%2C_revision%2C_metadata';

export function refreshThingAtRevision(thingId: string, revision: number) {
  console.assert(thingId && thingId !== '', 'thingId expected');
  API.callDittoREST('GET',
      `/things/${thingId}?${HISTORY_FIELDS}`,
      null,
      {'at-historical-revision': String(revision)})
      .then((thing) => setTheThing(thing))
      .catch((err) => console.error('Failed to fetch historical thing at revision', revision, err));
}

export function refreshThingAtTimestamp(thingId: string, timestamp: string) {
  console.assert(thingId && thingId !== '', 'thingId expected');
  API.callDittoREST('GET',
      `/things/${thingId}?${HISTORY_FIELDS}`,
      null,
      {'at-historical-timestamp': timestamp})
      .then((thing) => setTheThing(thing))
      .catch((err) => console.error('Failed to fetch historical thing at timestamp', timestamp, err));
}

export function setHistoryMode(active: boolean) {
  historyModeActive = active;
  historyModeObservers.forEach((observer) => observer.call(null, active));
  if (!active && theThing) {
    refreshThing(theThing.thingId);
  }
}

export function addHistoryModeChangeListener(observer) {
  historyModeObservers.push(observer);
}

