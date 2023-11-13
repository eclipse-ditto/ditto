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


let selectedSubject: string;

type DomElements = {
  tbodyPolicySubjects: HTMLTableElement,
  tableValidationSubjects: HTMLInputElement,
  crudSubject: CrudToolbar,
}

let dom: DomElements = {
  tbodyPolicySubjects: null,
  tableValidationSubjects: null,
  crudSubject: null,
} ;

let subjectEditor: ace.Editor;

export function ready() {
  Utils.getAllElementsById(dom);
  PolicyEntries.observable.addChangeListener(onEntryChanged);

  Utils.addValidatorToTable(dom.tbodyPolicySubjects, dom.tableValidationSubjects);

  subjectEditor = Utils.createAceEditor('subjectEditor', 'ace/mode/json', true);

  dom.tbodyPolicySubjects.onclick = onPolicySubjectsClick;

  dom.crudSubject.editDisabled = true;
  dom.crudSubject.addEventListener('onCreateClick', onCreatePolicySubjectClick);
  dom.crudSubject.addEventListener('onUpdateClick', onUpdatePolicySubjectClick);
  dom.crudSubject.addEventListener('onDeleteClick', onDeletePolicySubjectClick);
  dom.crudSubject.addEventListener('onEditToggle', onEditToggleSubject);
}

function onPolicySubjectsClick(event: MouseEvent): any {
  const target = event.target as HTMLElement;
  if (selectedSubject === target.parentElement.id) {
    selectedSubject = null;
    // Utils.tableAdjustSelection(dom.tbodyWhoami, () => false);
    subjectEditor.setValue('');
  } else {
    selectedSubject = target.parentElement.id;
    // Utils.tableAdjustSelection(dom.tbodyWhoami, (row) => row.id === selectedSubject);
    subjectEditor.setValue(
        JSON.stringify(Policies.thePolicy.entries[PolicyEntries.selectedEntry].subjects[selectedSubject], null, 2), -1);
  }
  dom.crudSubject.idValue = selectedSubject;
}

function onCreatePolicySubjectClick() {
  // validations(false, true, true);
  PolicyEntries.validateSelected();
  Utils.assert(dom.crudSubject.idValue, 'Please enter a subject or select one above', dom.crudSubject.validationElement);
  Utils.assert(!Object.keys(Policies.thePolicy.entries[PolicyEntries.selectedEntry].subjects).includes(dom.crudSubject.idValue),
      `Subject already exists`, dom.crudSubject.validationElement);
  selectedSubject = dom.crudSubject.idValue;
  modifySubject(dom.crudSubject.idValue,
      subjectEditor.getValue() !== '' ?
      JSON.parse(subjectEditor.getValue()) :
      {type: 'generated'},
      Policies.finishEditing(dom.crudSubject, CrudOperation.CREATE));
}

function modifySubject(key: string, value: object, onSuccess: (value: any) => any) {
  API.callDittoREST(value ? 'PUT' : 'DELETE',
      `/policies/${Policies.thePolicy.policyId}/entries/${PolicyEntries.selectedEntry}/subjects/${key}`, value
  ).then(onSuccess);
};

function onUpdatePolicySubjectClick() {
  // validations(false, true, false, true);
  PolicyEntries.validateSelected();
  Utils.assert(selectedSubject, 'Please select a subject', dom.tableValidationSubjects);
  modifySubject(
    selectedSubject,
    JSON.parse(subjectEditor.getValue()),
    Policies.finishEditing(dom.crudSubject, CrudOperation.UPDATE)
  );
}

function onDeletePolicySubjectClick() {
//  validations(false, true, false, true);
  PolicyEntries.validateSelected();
  Utils.assert(selectedSubject, 'Please select a subject', dom.tableValidationSubjects);
  modifySubject(selectedSubject, null, Policies.finishEditing(dom.crudSubject, CrudOperation.DELETE));
}

function onEditToggleSubject(event: CustomEvent) {
  subjectEditor.setReadOnly(!event.detail.isEditing);
  subjectEditor.renderer.setShowGutter(event.detail.isEditing);
  if (event.detail.isCancel) {
    dom.crudSubject.idValue = selectedSubject;
    if (Policies.thePolicy && PolicyEntries.selectedEntry && selectedSubject) {
      subjectEditor.setValue(JSON.stringify(Policies.thePolicy.entries[PolicyEntries.selectedEntry].subjects[selectedSubject], null, 2), -1);
    } else {
      subjectEditor.setValue('');
    }
  }
}

function onEntryChanged(entryLabel: string) {
  selectedSubject = null;

  dom.tbodyPolicySubjects.innerHTML = '';
  dom.crudSubject.idValue = null;
  dom.crudSubject.editDisabled = (entryLabel === null);
  subjectEditor.setValue('');

  if (Policies.thePolicy && entryLabel) {
    Object.keys(Policies.thePolicy.entries[entryLabel].subjects).forEach((key) => {
      Utils.addTableRow(dom.tbodyPolicySubjects, key, key === selectedSubject, null,
          JSON.stringify(Policies.thePolicy.entries[entryLabel].subjects[key])
      );
      if (key === selectedSubject) {
        dom.crudSubject.idValue = key;
        subjectEditor.setValue(JSON.stringify(Policies.thePolicy.entries[entryLabel].subjects[key], null, 2), -1);
      }
    });
  }

}