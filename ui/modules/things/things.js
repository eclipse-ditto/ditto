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

/* eslint-disable new-cap */
import {JSONPath} from 'https://cdn.jsdelivr.net/npm/jsonpath-plus@5.0.3/dist/index-browser-esm.min.js';
import * as API from '../api.js';

import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';
import * as Fields from './fields.js';

export let theThing;
let theSearchCursor;

let thingJsonEditor;

const observers = [];

const dom = {
  searchFilterEdit: null,
  thingsTable: null,
  thingDetails: null,
  thingId: null,
  tabModifyThing: null,
};

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

  thingJsonEditor = ace.edit('thingJsonEditor');
  thingJsonEditor.session.setMode('ace/mode/json');

  document.getElementById('pinnedThings').onclick = () => {
    dom.searchFilterEdit.value = null;
    getThings(Environments.current()['pinnedThings']);
  };

  dom.searchFilterEdit.onchange = removeMoreFromThingList;

  document.getElementById('createThing').onclick = () => {
    API.callDittoREST('POST', '/things', {})
        .then((data) => {
          refreshThing(data.thingId);
          getThings([data.thingId]);
        });
  };

  document.getElementById('putThing').onclick = clickModifyThing('PUT');
  document.getElementById('deleteThing').onclick = clickModifyThing('DELETE');

  dom.thingsTable.addEventListener('click', (event) => {
    if (event.target && event.target.nodeName === 'TD') {
      const row = event.target.parentNode;
      if (row.id === 'searchThingsMore') {
        row.style.pointerEvents = 'none';
        event.stopImmediatePropagation();
        searchThings(dom.searchFilterEdit.value, theSearchCursor);
      } else {
        refreshThing(row.id);
      }
    }
  });

  document.querySelector('a[data-bs-target="#tabModifyThing"]').addEventListener('shown.bs.tab', (event) => {
    thingJsonEditor.renderer.updateFull();
  });

  dom.searchFilterEdit.focus();
};

/**
 * Fills the things table UI with the given things
 * @param {Array} thingsList Array of thing json objects
 */
function fillThingsTable(thingsList) {
  const fields = Environments.current().fieldList.filter((f) => f.active).map((f) => f.path);
  thingsList.forEach((item, t) => {
    const row = dom.thingsTable.insertRow();
    row.id = item.thingId;
    if (theThing && (item.thingId === theThing.thingId)) {
      row.classList.add('table-active');
    };
    Utils.addCheckboxToRow(
        row,
        item.thingId,
        Environments.current().pinnedThings.includes(item.thingId),
        Environments.togglePinnedThing,
    );
    row.insertCell(-1).innerHTML = item.thingId;
    fields.forEach((key, i) => {
      let path = key.replace(/\//g, '.');
      if (path.charAt(0) !== '.') {
        path = '$.' + path;
      }
      const elem = JSONPath({
        json: item,
        path: path,
      });
      row.insertCell(-1).innerHTML = elem.length !== 0 ? elem[0] : '';
    });
  });
};

/**
 * Set the search filter UI component
 * @param {String} filter filter to be filled
 */
export function setSearchFilterEdit(filter) {
  dom.searchFilterEdit.value = filter;
}

/**
 * Calls Ditto search api and fills UI with the result
 * @param {String} filter Ditto search filter (rql)
 * @param {String} cursor (optional) cursor returned from things search for additional pages
 */
export function searchThings(filter, cursor) {
  if (cursor) {
    removeMoreFromThingList();
  } else {
    theSearchCursor = null;
    dom.thingsTable.innerHTML = '';
  }

  document.body.style.cursor = 'progress';

  API.callDittoREST('GET',
      '/search/things?' + Fields.getQueryParameter() +
      ((filter && filter != '') ? '&filter=' + encodeURIComponent(filter) : '') +
      '&option=sort(%2BthingId)' +
      // ',size(3)' +
      (cursor ? ',cursor(' + cursor + ')' : ''),
  ).then((searchResult) => {
    fillThingsTable(searchResult.items);
    checkMorePages(searchResult);
  }).catch((error) => {
    // nothing to do if search failed
  }).finally(() => {
    document.body.style.cursor = 'default';
  });
};

/**
 * Gets things from Ditto by thingIds and fills the UI with the result
 * @param {Array} thingIds Array of thingIds
 */
export function getThings(thingIds) {
  dom.thingsTable.innerHTML = '';
  if (thingIds.length > 0) {
    API.callDittoREST('GET',
        `/things?${Fields.getQueryParameter()}&ids=${thingIds}&option=sort(%2BthingId)`,
    ).then(fillThingsTable);
  };
};

/**
 * Returns a click handler for Update thing and delete thing
 * @param {String} method PUT or DELETE
 * @return {function} Click handler function
 */
function clickModifyThing(method) {
  return function() {
    Utils.assert(dom.thingId.value, 'Thing ID is empty');
    API.callDittoREST(method,
        '/things/' + dom.thingId.value,
        method === 'PUT' ? JSON.parse(thingJsonEditor.getValue()) : null,
    ).then(() => {
      // todo: perform last things table update
      method === 'PUT' ? refreshThing(dom.thingId.value) : searchThings();
    });
  };
};

/**
 * Load thing from Ditto and update all UI components
 * @param {String} thingId ThingId
 */
export function refreshThing(thingId) {
  API.callDittoREST('GET',
      `/things/${thingId}?fields=thingId%2Cattributes%2Cfeatures%2C_created%2C_modified%2C_revision%2C_policy`)
      .then((thing) => setTheThing(thing));
};

/**
 * Update all UI components for the given Thing
 * @param {Object} thingJson Thing json
 */
export function setTheThing(thingJson) {
  theThing = thingJson;

  // Update fields of Thing table
  dom.thingDetails.innerHTML = '';
  Utils.addTableRow(dom.thingDetails, 'thingId', theThing.thingId, null, true);
  Utils.addTableRow(dom.thingDetails, 'policyId', theThing._policy.policyId, null, true);
  Utils.addTableRow(dom.thingDetails, 'revision', theThing._revision, null, true);
  Utils.addTableRow(dom.thingDetails, 'created', theThing._created, null, true);
  Utils.addTableRow(dom.thingDetails, 'modified', theThing._modified, null, true);

  // Update edit thing area
  dom.thingId.value = theThing.thingId;
  const thingCopy = JSON.parse(JSON.stringify(theThing));
  delete thingCopy['_revision'];
  delete thingCopy['_created'];
  delete thingCopy['_modified'];
  delete thingCopy['_policy'];
  thingCopy.policyId = theThing._policy.policyId;
  thingJsonEditor.setValue(JSON.stringify(thingCopy, null, 2));

  observers.forEach((observer) => observer.call(null, theThing));
}

/**
 * Updates UI depepending on existing additional pages on Ditto things search
 * @param {Object} searchResult Result from Ditto thing search
 */
function checkMorePages(searchResult) {
  if (searchResult['cursor']) {
    addMoreToThingList();
    theSearchCursor = searchResult.cursor;
  } else {
    theSearchCursor = null;
  }
}

/**
 * Adds a clickable "more" line to the things table UI
 */
function addMoreToThingList() {
  const moreCell = dom.thingsTable.insertRow().insertCell(-1);
  moreCell.innerHTML = 'load more...';
  moreCell.colSpan = dom.thingsTable.rows[0].childElementCount;
  moreCell.style.textAlign = 'center';
  moreCell.style.cursor = 'pointer';
  moreCell.disabled = true;
  moreCell.style.color = '#3a8c9a';
  moreCell.parentNode.id = 'searchThingsMore';
};

/**
 * remove the "more" line from the things table
 */
function removeMoreFromThingList() {
  const moreRow = document.getElementById('searchThingsMore');
  if (moreRow) {
    moreRow.parentNode.removeChild(moreRow);
  }
}


