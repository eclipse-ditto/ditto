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
/* eslint-disable new-cap */
/* eslint-disable no-invalid-this */
/* eslint-disable arrow-parens */

import { JSONPath } from 'jsonpath-plus';

import * as API from '../api.js';
import * as Environments from '../environments/environments.js';

import * as Utils from '../utils.js';
import * as Fields from './fields.js';
import * as Things from './things.js';
import * as ThingsSSE from './thingsSSE.js';

let lastSearch = '';
let theSearchCursor;

const dom = {
  searchFilterCount: null,
  thingsTableHead: null,
  thingsTableBody: null,
  searchFilterEdit: null,
  favIcon: null,
};

const observers = [];
export function addChangeListener(observer) {
  observers.push(observer);
}
function notifyAll(thingIds = null, fields = null) {
  observers.forEach(observer => observer.call(null, thingIds, fields));
}


export async function ready() {
  Things.addChangeListener(onThingChanged);
  ThingsSSE.addChangeListener(updateTableRow);

  Utils.getAllElementsById(dom);

  dom.thingsTableBody.addEventListener('click', onThingsTableClicked);
}

function onThingsTableClicked(event) {
  if (event.target && event.target.nodeName === 'TD') {
    const row = event.target.parentNode;
    if (row.id === 'searchThingsMore') {
      row.style.pointerEvents = 'none';
      event.stopImmediatePropagation();
      searchThings(dom.searchFilterEdit.value, true);
    } else {
      if (Things.theThing && Things.theThing.thingId === row.id) {
        event.stopImmediatePropagation();
        Things.setTheThing(null);
      } else {
        Things.refreshThing(row.id, null);
      }
    }
  }
}

/**
 * Tests if the search filter is an RQL. If yes, things search is called otherwise just things get
 * @param {String} filter search filter string containing an RQL or a thingId
 */
export function searchTriggered(filter: string) {
  lastSearch = filter;
  const regex = /^(eq\(|ne\(|gt\(|ge\(|lt\(|le\(|in\(|like\(|ilike\(|exists\(|and\(|or\(|not\().*/;
  if (filter === '' || regex.test(filter)) {
    searchThings(filter);
  } else {
    getThings([filter]);
  }
}

/**
 * Gets the list of pinned things
 */
export function pinnedTriggered() {
  lastSearch = 'pinned';
  dom.searchFilterEdit.value = null;
  dom.favIcon.classList.replace('bi-star-fill', 'bi-star');
  getThings(Environments.current()['pinnedThings']);
}

/**
 * Performs the last search by the user using the last used filter.
 * If the user used pinned things last time, the pinned things are reloaded
 */
export function performLastSearch() {
  if (lastSearch === 'pinned') {
    pinnedTriggered();
  } else {
    searchTriggered(lastSearch);
  }
}

/**
 * Gets things from Ditto by thingIds and fills the UI with the result
 * @param {Array} thingIds Array of thingIds
 */
export function getThings(thingIds) {
  dom.searchFilterCount.innerHTML = '';
  dom.thingsTableBody.innerHTML = '';
  const fieldsQueryParameter = Fields.getQueryParameter();
  if (thingIds.length > 0) {
    API.callDittoREST('GET',
        `/things?${fieldsQueryParameter}&ids=${thingIds}&option=sort(%2BthingId)`)
        .then((thingJsonArray) => {
          fillThingsTable(thingJsonArray);
          dom.searchFilterCount.innerHTML = '#: ' + thingJsonArray.length;
          notifyAll(thingIds, fieldsQueryParameter);
        })
        .catch((error) => {
          resetAndClearViews();
          notifyAll();
        });
  } else {
    resetAndClearViews();
    notifyAll();
  }
}

function resetAndClearViews(retainThing = false) {
  theSearchCursor = null;
  dom.searchFilterCount.innerHTML = '';
  dom.thingsTableHead.innerHTML = '';
  dom.thingsTableBody.innerHTML = '';
  if (!retainThing) {
    Things.setTheThing(null);
  }
}

/**
 * Calls Ditto search API to perform a count and adds the count to the UI.
 * @param {String} filter Ditto search filter (rql)
 */
function countThings(filter: string) {
  dom.searchFilterCount.innerHTML = '';
  const namespaces = Environments.current().searchNamespaces
  API.callDittoREST('GET',
    '/search/things/count' +
    ((filter && filter !== '') ? '?filter=' + encodeURIComponent(filter) : '') +
    ((namespaces && namespaces !== '') ? '&namespaces=' + namespaces : ''), null, null
  ).then((countResult) => {
    dom.searchFilterCount.innerHTML = '#: ' + countResult;
  }).catch((error) => {
    notifyAll();
  });
}

/**
 * Calls Ditto search api and fills UI with the result
 * @param {String} filter Ditto search filter (rql)
 * @param {boolean} isMore (optional) use cursor from previous search for additional pages
 */
function searchThings(filter: string, isMore = false) {
  document.body.style.cursor = 'progress';

  const namespaces = Environments.current().searchNamespaces;
  const fieldsQueryParameter = Fields.getQueryParameter();
  API.callDittoREST('GET',
      '/search/things?' + fieldsQueryParameter +
      ((filter && filter !== '') ? '&filter=' + encodeURIComponent(filter) : '') +
      ((namespaces && namespaces !== '') ? '&namespaces=' + namespaces : '') +
      '&option=sort(%2BthingId)' +
      // ',size(3)' +
      (isMore ? ',cursor(' + theSearchCursor + ')' : ''), null, null
  ).then((searchResult) => {
    if (isMore) {
      removeMoreFromThingList();
    } else {
      countThings(filter);
      resetAndClearViews(true);
    }
    fillThingsTable(searchResult.items);
    checkMorePages(searchResult);
    if (!isMore) {
      notifyAll(searchResult.items.map(thingJson => thingJson.thingId), fieldsQueryParameter);
    }
  }).catch((error) => {
    resetAndClearViews();
    notifyAll();
  }).finally(() => {
    document.body.style.cursor = 'default';
  });

  function checkMorePages(searchResult) {
    if (searchResult['cursor']) {
      addMoreToThingList();
      theSearchCursor = searchResult.cursor;
    } else {
      theSearchCursor = null;
    }
  }

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
}

/**
 * remove the "more" line from the things table
 */
export function removeMoreFromThingList() {
  const moreRow = document.getElementById('searchThingsMore');
  if (moreRow) {
    moreRow.parentNode.removeChild(moreRow);
  }
}


/**
 * Fills the things table UI with the given things
 * @param {Array} thingsList Array of thing json objects
 */
function fillThingsTable(thingsList: any[]) {
  const activeFields = Environments.current().fieldList.filter((f) => f.active);
  fillHeaderRow();
  let thingSelected = false;
  thingsList.forEach((item, t) => {
    const row = dom.thingsTableBody.insertRow();
    fillBodyRow(row, item);
  });
  if (!thingSelected) {
    Things.setTheThing(null);
  }

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
    if (Things.theThing && (item.thingId === Things.theThing.thingId)) {
      thingSelected = true;
      row.classList.add('table-active');
    }
    Utils.addCheckboxToRow(
        row,
        item.thingId,
        Environments.current().pinnedThings.includes(item.thingId),
        false,
        togglePinnedThing,
    );
    Utils.addCellToRow(row, beautifyId(item.thingId), item.thingId);
    activeFields.forEach((field) => {
      let path = field.path.replace(/\//g, '.');
      if (path.charAt(0) !== '.') {
        path = '$.' + path;
      }
      const elem = JSONPath({
        json: item,
        path: path,
      });
      Utils.addCellToRow(row, elem.length !== 0 ? elem[0] : '').setAttribute('jsonPath', path);
    });
  }

  function beautifyId(longId) {
    const uuidRegex = /([0-9a-f]{7})[0-9a-f]-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
    let result = longId;
    if (Environments.current()['shortenUUID']) {
      result = result.replace(uuidRegex, '$1');
    }
    if (Environments.current()['defaultNamespace']) {
      result = result.replace(Environments.current()['defaultNamespace'], 'dn');
    }
    return result;
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

function onThingChanged(thingJson) {
  if (!thingJson) {
    Utils.tableAdjustSelection(dom.thingsTableBody, () => false);
  }
}

export function updateTableRow(thingUpdateJson) {
  const row = document.getElementById(thingUpdateJson.thingId) as HTMLTableRowElement;
  console.assert(row !== null, 'Unexpected thingId for table update. thingId was not loaded before');
  Array.from(row.cells).forEach((cell) => {
    const path = cell.getAttribute('jsonPath');
    if (path) {
      const elem = JSONPath({
        json: thingUpdateJson,
        path: path,
      });
      if (elem.length !== 0) {
        cell.innerHTML = elem[0];
      }
    }
  });
}
