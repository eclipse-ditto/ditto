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
// @ts-check

import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';
import * as ThingsSearch from './thingsSearch.js';

const filterExamples = [
  'eq(attributes/location,"kitchen")',
  'ge(thingId,"myThing1")',
  'gt(_created,"2020-08-05T12:17")',
  'exists(features/featureId)',
  'and(eq(attributes/location,"kitchen"),eq(attributes/color,"red"))',
  'or(eq(attributes/location,"kitchen"),eq(attributes/location,"living-room"))',
  'like(attributes/key1,"known-chars-at-start*")',
];

const filterHistory = [];

let keyStrokeTimeout;

const FILTER_PLACEHOLDER = '...';

let autoCompleteJS;

const dom = {
  favIcon: null,
  searchFilterEdit: null,
  searchThings: null,
  searchFavorite: null,
  tabThings: null,
  pinnedThings: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);

  dom.pinnedThings.onclick = ThingsSearch.pinnedTriggered;

  autoCompleteJS = Utils.createAutoComplete('#searchFilterEdit', createFilterList, 'Search for Things...');
  autoCompleteJS.input.addEventListener('selection', (event) => {
    const selection = event.detail.selection.value;
    fillSearchFilterEdit(selection.rql);
  });

  dom.searchThings.onclick = () => {
    fillHistory(dom.searchFilterEdit.value);
    ThingsSearch.searchTriggered(dom.searchFilterEdit.value);
  };

  dom.searchFavorite.onclick = () => {
    if (toggleFilterFavorite(dom.searchFilterEdit.value)) {
      dom.favIcon.classList.toggle('bi-star');
      dom.favIcon.classList.toggle('bi-star-fill');
    }
  };

  dom.searchFilterEdit.onkeyup = (event) => {
    if ((event.key === 'Enter' || event.code === 13) && dom.searchFilterEdit.value.indexOf(FILTER_PLACEHOLDER) < 0) {
      fillHistory(dom.searchFilterEdit.value);
      ThingsSearch.searchTriggered(dom.searchFilterEdit.value);
    } else {
      clearTimeout(keyStrokeTimeout);
      keyStrokeTimeout = setTimeout(checkIfFavorite, 1000);
    }
  };

  dom.searchFilterEdit.onchange = () => {
    ThingsSearch.removeMoreFromThingList();
  };

  dom.searchFilterEdit.focus();
}

/**
 * Callback to initialize searchFilters if the environment changed
 */
function onEnvironmentChanged() {
  if (!Environments.current()['filterList']) {
    Environments.current().filterList = [];
  }
  if (!Environments.current()['pinnedThings']) {
    Environments.current().pinnedThings = [];
  }
}

function fillSearchFilterEdit(fillString) {
  dom.searchFilterEdit.value = fillString;

  checkIfFavorite();
  const filterEditNeeded = checkAndMarkParameter();
  if (!filterEditNeeded) {
    ThingsSearch.searchTriggered(dom.searchFilterEdit.value);
  }
}

async function createFilterList(query) {
  const date24h = new Date();
  const date1h = new Date();
  const date1m = new Date();
  date24h.setDate(date24h.getDate() - 1);
  date1h.setHours(date1h.getHours() - 1);
  date1m.setMinutes(date1m.getMinutes() -1);

  return [
    {
      label: 'Created since 1m',
      rql: `gt(_created,"${date1m.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Created since 1h',
      rql: `gt(_created,"${date1h.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Created since 24h',
      rql: `gt(_created,"${date24h.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Modified since 1m',
      rql: `gt(_modified,"${date1m.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Modified since 1h',
      rql: `gt(_modified,"${date1h.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Modified since 24h',
      rql: `gt(_modified,"${date24h.toISOString()}")`,
      group: 'Time',
    },
    {
      label: `thingId = ${FILTER_PLACEHOLDER}`,
      rql: `eq(thingId,"${FILTER_PLACEHOLDER}")`,
      group: 'ThingId',
    },
    {
      label: `thingId ~ ${FILTER_PLACEHOLDER}`,
      rql: `like(thingId,"${FILTER_PLACEHOLDER}*")`,
      group: 'ThingId',
    },
    ...(Environments.current().filterList ?? []).map((f) => ({label: f, rql: f, group: 'Favorite'})),
    ...(Environments.current().fieldList ?? []).map((f) => ({
      label: `${f.label} = ${FILTER_PLACEHOLDER}`,
      rql: `eq(${f.path},"${FILTER_PLACEHOLDER}")`,
      group: 'Field',
    })),
    ...(Environments.current().fieldList ?? []).map((f) => ({
      label: `${f.label} ~ ${FILTER_PLACEHOLDER}`,
      rql: `like(${f.path},"*${FILTER_PLACEHOLDER}*")`,
      group: 'Field',
    })),
    ...filterHistory.map((f) => ({label: f, rql: f, group: 'Recent'})),
    ...filterExamples.map((f) => ({label: f, rql: f, group: 'Example'})),
  ];
}

/**
 * Adds or removes the given filter from the list of search filters
 * @param {String} filter filter
 * @return {boolean} true if the filter was toggled
 */
function toggleFilterFavorite(filter) {
  if (!filter || filter === '') {
    return false;
  }
  const i = Environments.current().filterList.indexOf(filter);
  if (i >= 0) {
    Environments.current().filterList.splice(i, 1);
  } else {
    Environments.current().filterList.push(filter);
  }
  Environments.environmentsJsonChanged('filterList');
  return true;
}

/**
 * Initializes the UI for the favicon dependent on wether the search filter is in the search filters
 */
function checkIfFavorite() {
  if (Environments.current().filterList.indexOf(dom.searchFilterEdit.value) >= 0) {
    dom.favIcon.classList.replace('bi-star', 'bi-star-fill');
  } else {
    dom.favIcon.classList.replace('bi-star-fill', 'bi-star');
  }
}

function checkAndMarkParameter() {
  const index = dom.searchFilterEdit.value.indexOf(FILTER_PLACEHOLDER);
  if (index >= 0) {
    // filterString.replace(FILTER_PLACEHOLDER, '');
    // dom.searchFilterEdit.value = filterString;
    dom.searchFilterEdit.focus();
    dom.searchFilterEdit.setSelectionRange(index, index + FILTER_PLACEHOLDER.length);
    return true;
  } else {
    return false;
  }
}

function fillHistory(filter) {
  if (!filterHistory.includes(filter)) {
    filterHistory.unshift(filter);
  }
}

