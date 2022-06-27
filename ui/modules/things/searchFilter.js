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

// const filterExamples = [
//   'eq(attributes/location,"kitchen")',
//   'ge(thingId,"myThing1")',
//   'gt(_created,"2020-08-05T12:17")',
//   'exists(features/featureId)',
//   'and(eq(attributes/location,"kitchen"),eq(attributes/color,"red"))',
//   'or(eq(attributes/location,"kitchen"),eq(attributes/location,"living-room"))',
//   'like(attributes/key1,"known-chars-at-start*")',
// ];

let keyStrokeTimeout;

const dom = {
  filterList: null,
  favIcon: null,
  searchFilterEdit: null,
  searchThings: null,
  searchFavourite: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.addTab(
      document.getElementById('thingsTabsItems'),
      document.getElementById('thingsTabsContent'),
      'Search Filter',
      await( await fetch('modules/things/searchFilter.html')).text(),
  );

  Utils.getAllElementsById(dom);

  dom.filterList.addEventListener('click', (event) => {
    Things.setSearchFilterEdit(event.target.textContent);
    checkIfFavourite();
    Things.searchThings(event.target.textContent);
  });

  dom.searchThings.onclick = () => {
    searchTriggered(dom.searchFilterEdit.value);
  };

  dom.searchFavourite.onclick = () => {
    if (toggleFilterFavourite(dom.searchFilterEdit.value)) {
      dom.favIcon.classList.toggle('bi-star');
      dom.favIcon.classList.toggle('bi-star-fill');
    };
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
    };
  };
};

/**
 * Callback to initialize searchFilters if the environment changed
 */
function onEnvironmentChanged() {
  if (!Environments.current()['filterList']) {
    Environments.current().filterList = [];
  };
  updateFilterList();

  dom.searchFilterEdit.focus();
};

/**
 * Tests if the search filter is an RQL. If yes, things search is called otherwise just things get
 * @param {String} filter search filter string containing an RQL or a thingId
 */
function searchTriggered(filter) {
  const regex = /^(eq\(|ne\(|gt\(|ge\(|lt\(|le\(|in\(|like\(|exists\(|and\(|or\(|not\().*/;
  if (filter === '' || regex.test(filter)) {
    Things.searchThings(filter);
  } else {
    Things.getThings([filter]);
  }
}

/**
 * Updates the UI filterList
 */
function updateFilterList() {
  dom.filterList.innerHTML = '';
  Environments.current().filterList.forEach((filter, i) => {
    Utils.addTableRow(dom.filterList, filter);
  });
};

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
  Environments.environmentsJsonChanged();
  return true;
};

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

