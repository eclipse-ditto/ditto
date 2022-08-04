/* eslint-disable require-jsdoc */
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
/* eslint-disable no-invalid-this */
import {JSONPath} from 'https://cdn.jsdelivr.net/npm/jsonpath-plus@5.0.3/dist/index-browser-esm.min.js';
import * as API from '../api.js';

import * as Environments from '../environments/environments.js';
import * as SearchFilter from './searchFilter.js';
import * as Utils from '../utils.js';
import * as Fields from './fields.js';

export let theThing;
let theSearchCursor;

let thingJsonEditor;

const observers = [];

const dom = {
  thingsTableHead: null,
  thingsTableBody: null,
  thingDetails: null,
  thingId: null,
  tabModifyThing: null,
  searchFilterEdit: null,
  collapseThings: null,
  tabThings: null,
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
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);

  thingJsonEditor = ace.edit('thingJsonEditor');
  thingJsonEditor.session.setMode('ace/mode/json');

  dom.searchFilterEdit.onchange = removeMoreFromThingList;

  document.getElementById('createThing').onclick = () => {
    API.callDittoREST('POST', '/things', {})
        .then((data) => {
          refreshThing(data.thingId);
          getThings([data.thingId]);
        });
  };

  document.getElementById('putThing').onclick = () => {
    Utils.assert(dom.thingId.value, 'Thing ID is empty');
    modifyThing('PUT');
  };

  document.getElementById('deleteThing').onclick = () => {
    Utils.assert(dom.thingId.value, 'Thing ID is empty');
    Utils.confirm(`Are you sure you want to delete thing<br>'${theThing.thingId}'?`, 'Delete', () => {
      modifyThing('DELETE');
    });
  };

  dom.thingsTableBody.addEventListener('click', (event) => {
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

  dom.tabThings.onclick = onTabActivated;

  dom.searchFilterEdit.focus();
}

/**
 * Fills the things table UI with the given things
 * @param {Array} thingsList Array of thing json objects
 */
function fillThingsTable(thingsList) {
  const activeFields = Environments.current().fieldList.filter((f) => f.active);
  fillHeaderRow();
  thingsList.forEach((item, t) => {
    const row = dom.thingsTableBody.insertRow();
    fillBodyRow(row, item);
  });

  function fillHeaderRow() {
    dom.thingsTableHead.innerHTML = '';
    // Utils.addCheckboxToRow(dom.thingsTableHead, 'checkboxHead', false, null);
    Utils.insertHeaderCell(dom.thingsTableHead, '');
    Utils.insertHeaderCell(dom.thingsTableHead, 'Thing ID');
    activeFields.forEach((field) => {
      Utils.insertHeaderCell(dom.thingsTableHead, field['label'] ? field.label : field.path);
    });
  }

  function fillBodyRow(row, item) {
    row.id = item.thingId;
    if (theThing && (item.thingId === theThing.thingId)) {
      row.classList.add('table-active');
    }
    Utils.addCheckboxToRow(
        row,
        item.thingId,
        Environments.current().pinnedThings.includes(item.thingId),
        togglePinnedThing,
    );
    row.insertCell(-1).innerHTML = item.thingId;
    activeFields.forEach((field) => {
      let path = field.path.replace(/\//g, '.');
      if (path.charAt(0) !== '.') {
        path = '$.' + path;
      }
      const elem = JSONPath({
        json: item,
        path: path,
      });
      row.insertCell(-1).innerHTML = elem.length !== 0 ? elem[0] : '';
    });
  }
}

/**
 * Calls Ditto search api and fills UI with the result
 * @param {String} filter Ditto search filter (rql)
 * @param {String} cursor (optional) cursor returned from things search for additional pages
 */
export function searchThings(filter, cursor) {
  document.body.style.cursor = 'progress';

  API.callDittoREST('GET',
      '/search/things?' + Fields.getQueryParameter() +
      ((filter && filter != '') ? '&filter=' + encodeURIComponent(filter) : '') +
      '&option=sort(%2BthingId)' +
      // ',size(3)' +
      (cursor ? ',cursor(' + cursor + ')' : ''),
  ).then((searchResult) => {
    if (cursor) {
      removeMoreFromThingList();
    } else {
      theSearchCursor = null;
      dom.thingsTableBody.innerHTML = '';
    }
    fillThingsTable(searchResult.items);
    checkMorePages(searchResult);
  }).catch((error) => {
    theSearchCursor = null;
    dom.thingsTableBody.innerHTML = '';
  }).finally(() => {
    document.body.style.cursor = 'default';
  });
}

/**
 * Gets things from Ditto by thingIds and fills the UI with the result
 * @param {Array} thingIds Array of thingIds
 */
export function getThings(thingIds) {
  dom.thingsTableBody.innerHTML = '';
  if (thingIds.length > 0) {
    API.callDittoREST('GET',
        `/things?${Fields.getQueryParameter()}&ids=${thingIds}&option=sort(%2BthingId)`,
    ).then(fillThingsTable);
  }
}

/**
 * Returns a click handler for Update thing and delete thing
 * @param {String} method PUT or DELETE
 */
function modifyThing(method) {
  API.callDittoREST(method,
      '/things/' + dom.thingId.value,
      method === 'PUT' ? JSON.parse(thingJsonEditor.getValue()) : null,
  ).then(() => {
    method === 'PUT' ? refreshThing(dom.thingId.value) : searchThings();
  });
}

/**
 * Load thing from Ditto and update all UI components
 * @param {String} thingId ThingId
 */
export function refreshThing(thingId) {
  API.callDittoREST('GET',
      `/things/${thingId}?fields=thingId%2Cattributes%2Cfeatures%2C_created%2C_modified%2C_revision%2C_policy`)
      .then((thing) => setTheThing(thing))
      .catch(() => setTheThing(null));
}

/**
 * Update all UI components for the given Thing
 * @param {Object} thingJson Thing json
 */
export function setTheThing(thingJson) {
  theThing = thingJson;

  updateThingDetailsTable();
  updateThingJsonEditor();

  observers.forEach((observer) => observer.call(null, theThing));

  function updateThingDetailsTable() {
    dom.thingDetails.innerHTML = '';
    if (theThing) {
      Utils.addTableRow(dom.thingDetails, 'thingId', false, true, theThing.thingId);
      Utils.addTableRow(dom.thingDetails, 'policyId', false, true, theThing._policy.policyId);
      Utils.addTableRow(dom.thingDetails, 'revision', false, true, theThing._revision);
      Utils.addTableRow(dom.thingDetails, 'created', false, true, theThing._created);
      Utils.addTableRow(dom.thingDetails, 'modified', false, true, theThing._modified);
    }
  }

  function updateThingJsonEditor() {
    if (theThing) {
      dom.thingId.value = theThing.thingId;
      const thingCopy = JSON.parse(JSON.stringify(theThing));
      delete thingCopy['_revision'];
      delete thingCopy['_created'];
      delete thingCopy['_modified'];
      delete thingCopy['_policy'];
      thingCopy.policyId = theThing._policy.policyId;
      thingJsonEditor.setValue(JSON.stringify(thingCopy, null, 2));
    } else {
      dom.thingId.value = null;
      thingJsonEditor.setValue('');
    }
  }
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
  const moreCell = dom.thingsTableBody.insertRow().insertCell(-1);
  moreCell.innerHTML = 'load more...';
  moreCell.colSpan = dom.thingsTableBody.rows[0].childElementCount;
  moreCell.style.textAlign = 'center';
  moreCell.style.cursor = 'pointer';
  moreCell.disabled = true;
  moreCell.style.color = '#3a8c9a';
  moreCell.parentNode.id = 'searchThingsMore';
}

/**
 * remove the "more" line from the things table
 */
function removeMoreFromThingList() {
  const moreRow = document.getElementById('searchThingsMore');
  if (moreRow) {
    moreRow.parentNode.removeChild(moreRow);
  }
}

function togglePinnedThing(evt) {
  if (evt.target.checked) {
    Environments.current().pinnedThings.push(this.id);
  } else {
    const index = Environments.current().pinnedThings.indexOf(this.id);
    if (index > -1) {
      Environments.current().pinnedThings.splice(index, 1);
    }
  }
  Environments.environmentsJsonChanged('pinnedThings');
}

let viewDirty = false;

function onTabActivated() {
  if (viewDirty) {
    refreshView();
    viewDirty = false;
  }
}

function onEnvironmentChanged(modifiedField) {
  if (dom.collapseThings.classList.contains('show')) {
    if (!['pinnedThings', 'filterList'].includes(modifiedField)) {
      refreshView();
    }
  } else {
    viewDirty = true;
  }
}

function refreshView() {
  SearchFilter.performLastSearch();
  if (theThing) {
    refreshThing(theThing.thingId);
  }
}


