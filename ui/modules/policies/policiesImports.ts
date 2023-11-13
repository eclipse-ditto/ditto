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

import * as ace from 'ace-builds/src-noconflict/ace';
import * as Utils from '../utils.js';
import * as API from '../api.js';
import * as Policies from './policies.js';
import * as PolicyEntries from './policiesEntries.js';
import { CrudOperation, CrudToolbar } from '../utils/crudToolbar.js';


let selectedImport: string;

type DomElements = {
  tbodyPolicyImports: HTMLTableElement,
  tbodyPolicyImportEntries: HTMLTableElement,
  crudImport: CrudToolbar,
}

let dom: DomElements = {
  tbodyPolicyImports: null,
  tbodyPolicyImportEntries: null,
  crudImport: null,
} ;

// let importEditor: ace.Editor;

export function ready() {
  Utils.getAllElementsById(dom);
  Policies.observable.addChangeListener(onPolicyChanged);

  // Utils.addValidatorToTable(dom.tbodyPolicySubjects, dom.tableValidationSubjects);

  // importEditor = Utils.createAceEditor('importEditor', 'ace/mode/json');

  dom.tbodyPolicyImports.onclick = onPolicyImportsClick;

  dom.crudImport.editDisabled = true;
  dom.crudImport.addEventListener('onCreateClick', onCreatePolicyImportClick);
  dom.crudImport.addEventListener('onUpdateClick', onUpdatePolicyImportClick);
  dom.crudImport.addEventListener('onDeleteClick', onDeletePolicyImportClick);
  dom.crudImport.addEventListener('onEditToggle', onEditToggleImport);
  dom.crudImport.addEventListener('onIdValueChange', onIdValueChange);
}

async function onIdValueChange() {
  await setImport(dom.crudImport.idValue);
  setExplicitCheckboxesDisabledState(false);
}

function onPolicyImportsClick(event: MouseEvent): any {
  const target = event.target as HTMLElement;
  if (selectedImport === target.parentElement.id) {
    selectedImport = null;
  } else {
    selectedImport = target.parentElement.id;
  }
  setImport(selectedImport);
}

function modifyImport(key: string, value: object, onSuccess: (value: any) => any) {
  API.callDittoREST(value ? 'PUT' : 'DELETE',
      `/policies/${Policies.thePolicy.policyId}/imports/${key}`, value
  ).then(onSuccess);
};

function onCreatePolicyImportClick() {
  selectedImport = dom.crudImport.idValue;
  modifyImport(selectedImport, importValueFromCheckboxes(), Policies.finishEditing(dom.crudImport, CrudOperation.CREATE));
}

function onUpdatePolicyImportClick() {
  modifyImport(selectedImport, importValueFromCheckboxes(), Policies.finishEditing(dom.crudImport, CrudOperation.UPDATE));
}

function onDeletePolicyImportClick() {
  Utils.confirm(`Are you sure you want to delete policy import<br>'${dom.crudImport.idValue}'?`, 'Delete', () => {
    modifyImport(selectedImport, null, Policies.finishEditing(dom.crudImport, CrudOperation.DELETE));
  });
}

function importValueFromCheckboxes() {
  const importValue = {};
  const checkedBoxes = document.querySelectorAll('#tbodyPolicyImportEntries input:checked[data-importable="explicit"]');
  if (checkedBoxes.length > 0) {
    importValue['entries'] = Array.from(checkedBoxes).map((checkbox) => checkbox.id);
  }
  return importValue;
}

function onEditToggleImport(event: CustomEvent) {
  setExplicitCheckboxesDisabledState(!event.detail.isEditing)
  if (event.detail.isCancel) {
    setImport(selectedImport);
  }
}

function setExplicitCheckboxesDisabledState(disabled: boolean) {
  document.querySelectorAll('#tbodyPolicyImportEntries input[data-importable="explicit"]')
    .forEach((e: HTMLInputElement) => e.disabled = disabled);
}

function onPolicyChanged(policy: Policies.Policy) {
  dom.tbodyPolicyImports.innerHTML = '';
  dom.crudImport.idValue = null;
  dom.crudImport.editDisabled = (policy === null);
  
  if (policy) {
    let policyHasImport = false;
    Object.keys(policy.imports).forEach((key) => {
      const row = Utils.addTableRow(dom.tbodyPolicyImports, key, key === selectedImport);
      Utils.addActionToRow(row, 'bi-arrow-up-right-square', getNavigatePolicyAction(key), 'Open policy');
      if (key === selectedImport) {
        setImport(key);
        policyHasImport = true;
      }
    });
    if (!policyHasImport) {
      selectedImport = null;
      setImport(null);
    }
  } else {
    setImport(null);
  }

  function getNavigatePolicyAction(policyId: String) {
    return (evt: Event) => {
      API.callDittoREST('GET', '/policies/' + policyId).then((targetPolicy: Policies.Policy) => {
        Policies.setThePolicy(targetPolicy);
      })
    }
  }
}

async function setImport(importedPolicyId: string) {

  dom.crudImport.idValue = importedPolicyId;
  dom.tbodyPolicyImportEntries.innerHTML = '';

  if (importedPolicyId) {
    const importedPolicy: Policies.Policy = await API.callDittoREST('GET', '/policies/' + importedPolicyId)
    Object.keys(importedPolicy.entries).forEach((entry) => {
      const row = dom.tbodyPolicyImportEntries.insertRow();
      const checkbox: HTMLInputElement = Utils.addCheckboxToRow(row, entry, isImportedOrImplicit(entry, importedPolicy), true);
      checkbox.dataset.importable = importedPolicy.entries[entry].importable;
      Utils.addCellToRow(row, entry, JSON.stringify(importedPolicy.entries[entry], null, 4));
      Utils.addCellToRow(row, importedPolicy.entries[entry].importable);
    });
  }

  function isImportedOrImplicit(entry: string, importedPolicy: Policies.Policy): boolean {
    const policyImport = Policies.thePolicy.imports[importedPolicyId];
    const isImported = policyImport && policyImport['entries'] && policyImport.entries.includes(entry);
    const isImplicit = importedPolicy.entries[entry].importable === 'implicit';
    return isImported || isImplicit;
  }
};




