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

import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';
import * as Things from './things.js';

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

let lastSearch = '';

const FILTER_PLACEHOLDER = '*****';

const dom = {
  filterList: null,
  favIcon: null,
  searchFilterEdit: null,
  searchThings: null,
  searchFavourite: null,
  tabThings: null,
  pinnedThings: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);

  dom.filterList.addEventListener('click', (event) => {
    if (event.target && event.target.classList.contains('dropdown-item')) {
      dom.searchFilterEdit.value = event.target.textContent;
      checkIfFavourite();
      const filterEditNeeded = checkAndMarkParameter();
      if (!filterEditNeeded) {
        Things.searchThings(event.target.textContent);
      }
    }
  });

  dom.searchThings.onclick = () => {
    searchTriggered(dom.searchFilterEdit.value);
  };

  dom.searchFavourite.onclick = () => {
    if (toggleFilterFavourite(dom.searchFilterEdit.value)) {
      dom.favIcon.classList.toggle('bi-star');
      dom.favIcon.classList.toggle('bi-star-fill');
    }
  };

  dom.searchFilterEdit.onkeyup = (event) => {
    if (event.key === 'Enter' || event.code === 13) {
      searchTriggered(dom.searchFilterEdit.value);
    } else {
      clearTimeout(keyStrokeTimeout);
      keyStrokeTimeout = setTimeout(checkIfFavourite, 1000);
    }
  };

  dom.searchFilterEdit.onclick = (event) => {
    if (event.target.selectionStart === event.target.selectionEnd) {
      event.target.select();
    }
  };

  dom.pinnedThings.onclick = pinnedTriggered;
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
  updateFilterList();
}

/**
 * Tests if the search filter is an RQL. If yes, things search is called otherwise just things get
 * @param {String} filter search filter string containing an RQL or a thingId
 */
function searchTriggered(filter) {
  lastSearch = filter;
  fillHistory(filter);
  const regex = /^(eq\(|ne\(|gt\(|ge\(|lt\(|le\(|in\(|like\(|exists\(|and\(|or\(|not\().*/;
  if (filter === '' || regex.test(filter)) {
    Things.searchThings(filter);
  } else {
    Things.getThings([filter]);
  }
}

/**
 * Gets the list of pinned things
 */
function pinnedTriggered() {
  lastSearch = 'pinned';
  dom.searchFilterEdit.value = null;
  dom.favIcon.classList.replace('bi-star-fill', 'bi-star');
  Things.getThings(Environments.current()['pinnedThings']);
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
 * Updates the UI filterList
 */
function updateFilterList() {
  dom.filterList.innerHTML = '';
  Utils.addDropDownEntries(dom.filterList, ['Favourite search filters'], true);
  Utils.addDropDownEntries(dom.filterList, Environments.current().filterList);
  Utils.addDropDownEntries(dom.filterList, ['Field search filters'], true);
  Utils.addDropDownEntries(dom.filterList, Environments.current().fieldList
      .map((f) => `eq(${f.path},${FILTER_PLACEHOLDER})`));
  Utils.addDropDownEntries(dom.filterList, ['Example search filters'], true);
  Utils.addDropDownEntries(dom.filterList, filterExamples);
  Utils.addDropDownEntries(dom.filterList, ['Recent search filters'], true);
  Utils.addDropDownEntries(dom.filterList, filterHistory);
}

/**
 * Adds or removes the given filter from the list of search filters
 * @param {String} filter filter
 * @return {boolean} true if the filter was toggled
 */
function toggleFilterFavourite(filter) {
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
function checkIfFavourite() {
  if (Environments.current().filterList.indexOf(dom.searchFilterEdit.value) >= 0) {
    dom.favIcon.classList.replace('bi-star', 'bi-star-fill');
  } else {
    dom.favIcon.classList.replace('bi-star-fill', 'bi-star');
  }
}

function checkAndMarkParameter() {
  const index = dom.searchFilterEdit.value.indexOf(FILTER_PLACEHOLDER);
  if (index >= 0) {
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
    updateFilterList();
  }
}

