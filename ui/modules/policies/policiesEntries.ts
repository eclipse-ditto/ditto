/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import * as Utils from '../utils.js';
import * as API from '../api.js';
import { Observable } from '../utils/observable.js';
import { CrudOperation, CrudToolbar } from '../utils/crudToolbar.js';
import * as Policies from './policies.js';

export let observable = Observable();

export let selectedEntry: string;

type DomElements = {
  tbodyPolicyEntries: HTMLTableElement,
  tableValidationEntries: HTMLInputElement,
  selectImportable: HTMLSelectElement,
  crudEntry: CrudToolbar,
}

let dom: DomElements = {
  tbodyPolicyEntries: null,
  tableValidationEntries: null,
  selectImportable: null,
  crudEntry: null,
};

export function ready() {
  
  Utils.getAllElementsById(dom);
  Policies.observable.addChangeListener(onPolicyChanged);

  Utils.addValidatorToTable(dom.tbodyPolicyEntries, dom.tableValidationEntries);

  dom.crudEntry.editDisabled = true;
  dom.crudEntry.addEventListener('onCreateClick', onCreateEntryClick);
  dom.crudEntry.addEventListener('onDeleteClick', onDeleteEntryClick);
  dom.crudEntry.addEventListener('onUpdateClick', onUpdateEntryClick);
  dom.crudEntry.addEventListener('onEditToggle', onToggleEditEntry);

  dom.tbodyPolicyEntries.onclick = onPolicyEntriesClick;
}

function onToggleEditEntry(event) {
  dom.selectImportable.disabled = !event.detail.isEditing;
  if (event.detail.isCancel) {
    updateEditors(selectedEntry);
  }
}

function onPolicyEntriesClick(event) {
  if (selectedEntry === event.target.textContent) {
    selectedEntry = null;
  } else {
    selectedEntry = event.target.textContent;
  }
  setEntry(selectedEntry);
}

function onCreateEntryClick() {
  // validations(true);
  Utils.assert(dom.crudEntry.idValue, 'Please enter a label for the entry', dom.crudEntry.validationElement);
  Utils.assert(!Object.keys(Policies.thePolicy.entries).includes(dom.crudEntry.idValue),
      `Entry with label ${dom.crudEntry.idValue} already exists`, dom.crudEntry.validationElement);
  selectedEntry = dom.crudEntry.idValue;
  putOrDeletePolicyEntry(dom.crudEntry.idValue, {
    subjects: {},
    resources: {},
    importable: dom.selectImportable.value
  }, Policies.finishEditing(dom.crudEntry, CrudOperation.CREATE));
}

function onUpdateEntryClick() {
  Policies.thePolicy.entries[selectedEntry].importable = dom.selectImportable.value;
  putOrDeletePolicyEntry(selectedEntry, Policies.thePolicy.entries[selectedEntry], Policies.finishEditing(dom.crudEntry, CrudOperation.UPDATE));
}

function onDeleteEntryClick() {
  // validations(false, true);
  validateSelected();
  Utils.confirm(`Are you sure you want to delete policy entry<br>'${dom.crudEntry.idValue}'?`, 'Delete', () => {
    putOrDeletePolicyEntry(selectedEntry, null, Policies.finishEditing(dom.crudEntry, CrudOperation.DELETE));
  });
}

function putOrDeletePolicyEntry(entry, value, onSuccess) {
  API.callDittoREST(value ? 'PUT' : 'DELETE',
      `/policies/${Policies.thePolicy.policyId}/entries/${entry}`,
      value
  ).then(onSuccess);
};

function onPolicyChanged(policy: Policies.Policy) {
  dom.tbodyPolicyEntries.innerHTML = '';
  dom.crudEntry.idValue = null;
  dom.crudEntry.editDisabled = (policy === null);
  
  if (policy) {
    let policyHasEntry = false;
    Object.keys(policy.entries).forEach((key) => {
      Utils.addTableRow(dom.tbodyPolicyEntries, key, key === selectedEntry);
      if (key === selectedEntry) {
        setEntry(key);
        policyHasEntry = true;
      }
    });
    if (!policyHasEntry) {
      selectedEntry = null;
      setEntry(null);
    }
  } else {
    setEntry(null);
  }
}

function setEntry(entryLabel: string) {
  updateEditors(entryLabel);
  observable.notifyAll(selectedEntry);
};

function updateEditors(entryLabel: string) {
  dom.crudEntry.idValue = entryLabel;
  dom.selectImportable.value = entryLabel && Policies.thePolicy.entries[entryLabel].importable
}


export function validateSelected() {
  Utils.assert(selectedEntry, 'Please select an entry', dom.tableValidationEntries);
}

