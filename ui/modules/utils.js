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

/* eslint-disable quotes */

const dom = {
  modalBodyConfirm: null,
  buttonConfirmed: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export function ready() {
  getAllElementsById(dom);
}

/**
 * Adds a table to a table element
 * @param {HTMLElement} table tbody element the row is added to
 * @param {String} key first column text of the row. Acts as id of the row
 * @param {boolean} selected if true, the new row will be marked as selected
 * @param {boolean} withClipBoardCopy add a clipboard button at the last column of the row
 * @param {array} columnValues texts for additional columns of the row
 */
export const addTableRow = function(table, key, selected, withClipBoardCopy, ...columnValues) {
  const row = table.insertRow();
  row.id = key;
  row.insertCell(0).innerHTML = key;
  columnValues.forEach((value) => {
    row.insertCell().innerHTML = value;
  });
  if (selected) {
    row.classList.add('table-active');
  }
  if (withClipBoardCopy) {
    addClipboardCopyToRow(row);
  }
};

/**
 * Adds a checkbox as a firs column of a row
 * @param {HTMLTableRowElement} row target row
 * @param {String} id an id for the checkbox element
 * @param {boolean} checked check the checkbox
 * @param {function} onToggle callback for the onchange event of the checkbox
 */
export function addCheckboxToRow(row, id, checked, onToggle) {
  const td = row.insertCell(0);
  td.style.verticalAlign = 'middle';
  const checkBox = document.createElement('input');
  checkBox.type = 'checkbox';
  checkBox.id = id;
  checkBox.checked = checked;
  checkBox.onchange = onToggle;
  td.append(checkBox);
}

/**
 * Adds a clipboard copy button to a row. The text of the previous table cell will be copied
 * @param {HTMLTableRowElement} row target row
 */
export function addClipboardCopyToRow(row) {
  const td = row.insertCell();
  td.style.textAlign = 'right';
  const button = document.createElement('button');
  button.classList.add('btn', 'btn-sm');
  button.style.padding = 0;
  button.innerHTML = `<i class="bi bi-clipboard"></i>`;
  button.onclick = (evt) => {
    const td = evt.currentTarget.parentNode.previousSibling;
    navigator.clipboard.writeText(td.innerText);
  };
  td.appendChild(button);
}

/**
 * Adds a header cell to the given table row
 * @param {HTMLTableRowElement} row target row
 * @param {String} label label for the header cell
 */
export function insertHeaderCell(row, label) {
  const th = document.createElement('th');
  th.innerHTML = label;
  row.appendChild(th);
}

/**
 * Create a radio button element
 * @param {HTMLElement} target target element
 * @param {String} groupName group for consecutive added radio buttons
 * @param {String} value name of the radio button
 * @param {boolean} checked check the radio button
 */
export function addRadioButton(target, groupName, value, checked) {
  const radio = document.createElement('div');
  radio.innerHTML = `<div class="form-check">
    <input class="form-check-input" type="radio" id="${value}" name="${groupName}" value="${value}"
        ${checked ? 'checked' : ''}>
    <label class="form-check-label" for="${value}">
      ${value}
    </label>
  </div>`;
  target.appendChild(radio);
}

/**
 * Creates a drop down item or header
 * @param {HTMLElement} target target element
 * @param {array} items array of items for the drop down
 * @param {boolean} isHeader (optional) true to add a header line
 */
export function addDropDownEntries(target, items, isHeader) {
  items.forEach((value) => {
    const li = document.createElement('li');
    li.innerHTML = isHeader ?
        `<h6 class="dropdown-header">${value}</h6>` :
        `<a class="dropdown-item">${value}</a>`;
    target.appendChild(li);
  });
}

/**
 * Add a tab pane in the UI. tab header and tab contens are added
 * @param {HTMLElement} tabItemsNode root node for the tabs
 * @param {HTMLElement} tabContentsNode root node for the tab contents
 * @param {String} title name of the new tab
 * @param {String} contentHTML tab content for the new tab
 */
export function addTab(tabItemsNode, tabContentsNode, title, contentHTML) {
  const id = 'tab' + title.replace(/\s/g, '');

  const li = document.createElement('li');
  li.classList.add('nav-item');
  li.innerHTML = `<a class="nav-link" data-bs-toggle="tab" data-bs-target="#${id}">${title}</a>`;
  tabItemsNode.appendChild(li);

  const template = document.createElement('template');
  template.innerHTML = contentHTML;
  template.content.firstElementChild.id = id;
  tabContentsNode.appendChild(template.content.firstElementChild);
}

/**
 * Get the HTMLElements of all the given ids. The HTMLElements will be returned in the original object
 * @param {Object} domObjects object with empty keys that are used as ids of the dom elements
 */
export function getAllElementsById(domObjects) {
  Object.keys(domObjects).forEach((id) => {
    domObjects[id] = document.getElementById(id);
    if (!domObjects[id]) {
      throw new Error(`Element ${id} not found.`);
    }
  });
}

let errorToast = null;

/**
 * Show an error toast
 * @param {String} message Message for toast
 * @param {String} header Header for toast
 * @param {String} status Status text for toas
 */
export function showError(message, header, status) {
  if (!errorToast) {
    errorToast = new bootstrap.Toast(document.getElementById('errorToast'));
  }
  document.getElementById('errorHeader').innerText = header ? header : 'Error';
  document.getElementById('errorBody').innerText = message;
  document.getElementById('errorStatus').innerText = status ? status : '';
  errorToast.show();
}

/**
 * Error object for user errors
 * @param {String} message Message that was displayed to the user in an error toast
 */
function UserException(message) {
  this.message = message;
  this.name = 'UserException';
}

/**
 * User assertion. If the condition is false, a message is displayed to the user and execution breaks by throwing
 * an error.
 * @param {boolean} condition If false, an error is shown to the user
 * @param {String} message Message to be shown to the user
 * @param {HTMLElement} validatedElement Optional element that was validated
 */
export function assert(condition, message, validatedElement) {
  if (validatedElement) {
    validatedElement.classList.remove('is-invalid');
  }
  if (!condition) {
    if (validatedElement) {
      validatedElement.parentNode.getElementsByClassName('invalid-feedback')[0].innerHTML = message;
      validatedElement.classList.add('is-invalid');
    } else {
      showError(message, 'Error');
    }
    throw new UserException(message);
  }
}

/**
 * Simple Date format that makes UTC string more readable and cuts off the milliseconds
 * @param {Date} date to format
 * @param {boolean} withMilliseconds don t cut off milliseconds if true
 * @return {String} formatted date
 */
export function formatDate(date, withMilliseconds) {
  if (withMilliseconds) {
    return date.replace('T', ' ').replace('Z', '').replace('.', ' ');
  } else {
    return date.split('.')[0].replace('T', ' ');
  }
}

let modalConfirm;

/**
 * Like from bootbox or bootprompt
 * @param {String} message confirm message
 * @param {String} action button text
 * @param {function} callback true if confirmed
 */
export function confirm(message, action, callback) {
  modalConfirm = modalConfirm ?? new bootstrap.Modal('#modalConfirm');
  dom.modalBodyConfirm.innerHTML = message;
  dom.buttonConfirmed.innerText = action;
  dom.buttonConfirmed.onclick = callback;
  modalConfirm.show();
}

/**
 * Creates and configures an ace editor
 * @param {String} domId id of the dom element for the ace editor
 * @param {*} sessionMode session mode of the ace editor
 * @param {*} readOnly sets the editor to read only and removes the line numbers
 * @return {*} created ace editor
 */
export function createAceEditor(domId, sessionMode, readOnly) {
  const result = ace.edit(domId);

  result.session.setMode(sessionMode);
  if (readOnly) {
    result.setReadOnly(true);
    result.renderer.setShowGutter(false);
  }

  return result;
}
