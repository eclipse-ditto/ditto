/* eslint-disable require-jsdoc */
/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import * as API from '../api.js';
import * as Utils from '../utils.js';
import * as Features from './features.js';
import * as Things from './things.js';

/**
 * Backing module for the "Timeseries" tab inside the feature pane.
 * <p>
 * Wires up the tab's controls (path(s) / from / to / limit / aggregation / step / fill /
 * percentile) and a Retrieve button. A single path fetches the first-class single-property
 * resource {@code GET /api/2/timeseries/things/<id>/features/<f>/properties/<p>}; comma-separated
 * paths fetch the multi-property endpoint {@code GET /api/2/timeseries/things/<id>?paths=...}.
 * Each returned series renders as its own stacked block (heading + hand-rolled SVG sparkline +
 * (timestamp, value) table) inside a scrollable results region, so several paths don't fight over
 * one chart and no chart library is pulled in.
 * <p>
 * The path input is auto-prefilled with {@code /features/<currentFeatureId>/properties/} on
 * feature selection so the typical case is a single keystroke ({@code temperature}, etc.). The
 * from/to fields default to "the last 24h" relative to the user's clock — good enough for
 * exploratory use; precise queries land in the URL.
 */

const dom = {
  inputTimeseriesPath: null as HTMLInputElement | null,
  inputTimeseriesFrom: null as HTMLInputElement | null,
  inputTimeseriesTo: null as HTMLInputElement | null,
  inputTimeseriesLimit: null as HTMLInputElement | null,
  selectTimeseriesAgg: null as HTMLSelectElement | null,
  selectTimeseriesStep: null as HTMLSelectElement | null,
  selectTimeseriesFill: null as HTMLSelectElement | null,
  inputTimeseriesPercentile: null as HTMLInputElement | null,
  labelTimeseriesPercentile: null as HTMLElement | null,
  buttonTimeseriesRetrieve: null as HTMLButtonElement | null,
  timeseriesStatus: null as HTMLElement | null,
  timeseriesResults: null as HTMLElement | null,
};

/** Aggregations computed as one $group accumulator per bucket — these require a Step. */
const GROUP_AGGREGATIONS = ['avg', 'min', 'max', 'sum', 'count', 'first', 'last', 'stddev'];

/** Last feature the tab rendered for; used to drop stale series only on an actual context switch. */
let lastFeatureId: string | null = null;

interface TimeseriesPoint {
  t: string;
  v: unknown;
  _gap?: boolean;
}

interface TimeseriesSeries {
  thingId: string;
  path: string;
  result?: { count?: number; unit?: string | null; dataType?: string | null };
  data?: TimeseriesPoint[];
}

export function ready(): void {
  Utils.getAllElementsById(dom);

  // Defaults: from = now-24h, to = now. Datetime-local inputs use the local timezone for display
  // but we read them back as if they were UTC (see toUtcIso), which is the convention the rest
  // of the Ditto Protocol uses for time arguments.
  const now = new Date();
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  if (dom.inputTimeseriesFrom && !dom.inputTimeseriesFrom.value) {
    dom.inputTimeseriesFrom.value = toLocalInputValue(yesterday);
  }
  if (dom.inputTimeseriesTo && !dom.inputTimeseriesTo.value) {
    dom.inputTimeseriesTo.value = toLocalInputValue(now);
  }

  dom.buttonTimeseriesRetrieve?.addEventListener('click', onRetrieveClick);

  // Show the percentile input only when the percentile aggregation is selected, so the row stays
  // uncluttered for every other function.
  dom.selectTimeseriesAgg?.addEventListener('change', updatePercentileVisibility);
  updatePercentileVisibility();

  Features.addChangeListener(onFeatureChanged);
  // Clear the previous thing's results when a different thing is selected — otherwise the
  // sparkline + table + status text from the previous thing carry over and mislead the user
  // until they click Retrieve again. The isNewThingId flag (second arg) is true only when the
  // selected thingId actually changed, so an in-place refresh (e.g. after an edit) keeps the
  // view in place.
  Things.addChangeListener((_thing: unknown, isNewThingId: boolean) => {
    if (isNewThingId) {
      clearResults();
      setStatus('');
    }
  });
}

function updatePercentileVisibility(): void {
  const show = (dom.selectTimeseriesAgg?.value ?? '') === 'percentile';
  if (dom.labelTimeseriesPercentile) dom.labelTimeseriesPercentile.hidden = !show;
  if (dom.inputTimeseriesPercentile) dom.inputTimeseriesPercentile.hidden = !show;
}

function onFeatureChanged(featureId: string): void {
  // Drop stale series only when the feature actually changes (incl. when a different Thing is
  // selected) — NOT on every in-place refresh, so a live-updating graph the user is watching is
  // not wiped each time new data arrives via SSE.
  if (featureId !== lastFeatureId) {
    lastFeatureId = featureId;
    clearResults();
    setStatus('');
  }
  if (!dom.inputTimeseriesPath) return;
  const currentValue = dom.inputTimeseriesPath.value;
  const looksLikePrefix = !currentValue || /^\/features\/[^/]*\/properties\/?$/.test(currentValue);
  // Only auto-update when the field is empty or still holds an auto-generated prefix from the
  // previous selection — never clobber a path the user has actually typed into.
  if (looksLikePrefix) {
    dom.inputTimeseriesPath.value = featureId ? `/features/${featureId}/properties/` : '';
  }
}

async function onRetrieveClick(): Promise<void> {
  Utils.assert(Things.theThing, 'No Thing selected');
  const path = (dom.inputTimeseriesPath?.value ?? '').trim();
  const fromLocal = dom.inputTimeseriesFrom?.value ?? '';
  const toLocal = dom.inputTimeseriesTo?.value ?? '';
  const limit = (dom.inputTimeseriesLimit?.value ?? '').trim();
  const agg = (dom.selectTimeseriesAgg?.value ?? '').trim();
  const step = (dom.selectTimeseriesStep?.value ?? '').trim();
  const fill = (dom.selectTimeseriesFill?.value ?? '').trim();
  const percentile = (dom.inputTimeseriesPercentile?.value ?? '').trim();
  Utils.assert(path, 'Path must not be empty', dom.inputTimeseriesPath);
  Utils.assert(fromLocal, 'From must not be empty', dom.inputTimeseriesFrom);
  Utils.assert(toLocal, 'To must not be empty', dom.inputTimeseriesTo);
  // Per-bucket aggregations are computed over Step-sized windows, so a Step is required for them;
  // the advanced functions (derivative/rate/integral/percentile) also work on raw points.
  Utils.assert(!(GROUP_AGGREGATIONS.includes(agg) && !step),
      `Aggregation "${agg}" needs a Step (pick e.g. 1h).`, dom.selectTimeseriesStep);
  Utils.assert(!(agg === 'percentile' && !percentile),
      'The percentile aggregation needs a percentile value (0-100).', dom.inputTimeseriesPercentile);

  // Comma-separated paths -> several series in one call (multi-property endpoint); a single path
  // keeps the original single-property URL shape. Each path must be a full Ditto pointer.
  const paths = path.split(',').map((p) => p.trim()).filter((p) => p.length > 0);
  for (const p of paths) {
    Utils.assert(/^\/(features\/[^/]+\/properties|attributes)\/.+$/.test(p),
        `Path "${p}" must look like /features/<feature>/properties/<property> or /attributes/<path>`,
        dom.inputTimeseriesPath);
  }

  const params = new URLSearchParams();
  params.append('from', toUtcIso(fromLocal));
  params.append('to', toUtcIso(toLocal));
  if (limit) {
    params.append('limit', limit);
  }
  if (agg) {
    params.append('agg', agg);
  }
  if (step) {
    params.append('step', step);
  }
  if (fill) {
    params.append('fill', fill);
  }
  if (agg === 'percentile' && percentile) {
    params.append('percentile', percentile);
  }

  const thingId = encodeURIComponent(Things.theThing.thingId);
  const split = paths.length === 1 ? splitFullPath(paths[0]) : null;
  let url: string;
  if (split) {
    // Single feature property -> first-class single-property resource.
    url = `/timeseries/things/${thingId}/features/${encodeURIComponent(split.featureId)}` +
        `/properties/${encodePointer(split.propertyPointer)}?${params.toString()}`;
  } else {
    // Multiple paths, or a single attribute path -> multi-property endpoint with ?paths=.
    params.append('paths', paths.join(','));
    url = `/timeseries/things/${thingId}?${params.toString()}`;
  }

  setStatus('Loading…');
  clearResults();

  try {
    const response = await API.callDittoREST('GET', url);
    renderResults(response);
  } catch (err) {
    // callDittoREST already toasts the error via Utils.showError; we just blank the status.
    setStatus('');
  }
}

/**
 * Splits a full Ditto pointer of the form /features/{featureId}/properties/{rest} into its parts.
 * Returns null if the input doesn't have that shape — the caller surfaces a UI-side validation
 * error rather than letting the gateway 400 on it.
 */
function splitFullPath(fullPath: string): { featureId: string; propertyPointer: string } | null {
  const match = /^\/features\/([^/]+)\/properties\/(.+)$/.exec(fullPath);
  if (!match) return null;
  return { featureId: match[1], propertyPointer: match[2] };
}

/**
 * Encodes a slash-separated property pointer for inclusion in a URL path. We percent-encode each
 * segment individually so segment-level slashes survive (the gateway expects multi-segment property
 * pointers like {@code temperature/avg}), then join with raw slashes.
 */
function encodePointer(pointer: string): string {
  return pointer.split('/').map(encodeURIComponent).join('/');
}

function renderResults(response: unknown): void {
  // The timeseries-service returns a JSON array of per-path series (one entry per requested path);
  // tolerate both a bare array and a `{results: [...]}` envelope. Each series renders as its own
  // stacked block (heading + sparkline + table) so multiple paths don't fight over one chart.
  const seriesList: TimeseriesSeries[] = Array.isArray(response)
      ? (response as TimeseriesSeries[])
      : (((response as { results?: TimeseriesSeries[] }).results) ?? []);
  clearResults();
  if (seriesList.length === 0) {
    setStatus('Empty response.');
    return;
  }
  let totalPoints = 0;
  for (const series of seriesList) {
    totalPoints += (series.data ?? []).length;
    renderSeriesBlock(series);
  }
  setStatus(`${seriesList.length} series · ${totalPoints} point(s)`);
}

const SVG_NS = 'http://www.w3.org/2000/svg';

function renderSeriesBlock(series: TimeseriesSeries): void {
  if (!dom.timeseriesResults) return;
  const points = series.data ?? [];

  const block = document.createElement('div');
  block.className = 'mb-2';

  const heading = document.createElement('div');
  heading.className = 'small fw-semibold text-truncate';
  heading.textContent = series.path ?? '(unknown path)';
  heading.title = series.path ?? '';
  block.appendChild(heading);

  const meta = document.createElement('div');
  meta.className = 'text-muted small mb-1';
  const m = series.result;
  meta.textContent = `${points.length} point(s)` +
      (m?.unit ? ` · unit ${m.unit}` : '') +
      (m?.dataType ? ` · type ${m.dataType}` : '');
  block.appendChild(meta);

  const svgWrap = document.createElement('div');
  svgWrap.className = 'border rounded p-1 mb-1';
  svgWrap.style.backgroundColor = 'var(--bs-body-bg)';
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('width', '100%');
  svg.setAttribute('height', '120');
  svg.setAttribute('preserveAspectRatio', 'none');
  svg.setAttribute('viewBox', '0 0 600 120');
  svg.style.display = 'block';
  svgWrap.appendChild(svg);
  block.appendChild(svgWrap);

  const tableWrap = document.createElement('div');
  tableWrap.className = 'table-wrap';
  // Keep each per-series table compact so several series' charts stay visible in the scroll
  // region rather than one long table pushing the next chart out of view.
  tableWrap.style.maxHeight = '140px';
  tableWrap.style.overflowY = 'auto';
  const table = document.createElement('table');
  table.className = 'table table-striped table-hover table-sm';
  const thead = document.createElement('thead');
  const headRow = document.createElement('tr');
  const thT = document.createElement('th');
  thT.style.width = '50%';
  thT.textContent = 'Timestamp (UTC)';
  const thV = document.createElement('th');
  thV.textContent = 'Value';
  headRow.appendChild(thT);
  headRow.appendChild(thV);
  thead.appendChild(headRow);
  const tbody = document.createElement('tbody');
  table.appendChild(thead);
  table.appendChild(tbody);
  tableWrap.appendChild(table);
  block.appendChild(tableWrap);

  dom.timeseriesResults.appendChild(block);

  renderSparklineInto(svg, points);
  renderTableInto(tbody, points);
}

function renderTableInto(tbody: HTMLTableSectionElement, points: TimeseriesPoint[]): void {
  // textContent on every cell — no innerHTML — so user-controlled values can't escape the cell.
  const rows = points.map((p) => {
    const tr = document.createElement('tr');
    const tdT = document.createElement('td');
    tdT.textContent = String(p.t);
    const tdV = document.createElement('td');
    tdV.textContent = formatValue(p.v);
    if (p._gap) {
      // Gap-filled point (fill strategy): mute the row and tag it so it's distinguishable from a
      // real observation.
      tr.classList.add('text-muted', 'fst-italic');
      const badge = document.createElement('span');
      badge.className = 'badge bg-secondary ms-1';
      badge.textContent = 'gap';
      tdV.appendChild(document.createTextNode(' '));
      tdV.appendChild(badge);
    }
    tr.appendChild(tdT);
    tr.appendChild(tdV);
    return tr;
  });
  tbody.replaceChildren(...rows);
}

function renderSparklineInto(svg: SVGSVGElement, points: TimeseriesPoint[]): void {
  // Inline SVG path — works for numeric data; non-numeric / gap (null) points produce NaN, which
  // we filter out. ViewBox is fixed at 600x120 and the SVG uses preserveAspectRatio="none" so it
  // stretches to the container.
  const numeric: { t: number; v: number }[] = points
      .map((p) => ({ t: Date.parse(String(p.t)), v: Number(p.v) }))
      .filter((p) => Number.isFinite(p.t) && Number.isFinite(p.v));
  svg.replaceChildren();
  if (numeric.length === 0) {
    return;
  }
  if (numeric.length === 1) {
    // A single aggregated value (e.g. one downsample bucket) can't form a line — draw a labelled
    // dot so the panel isn't blank and the user still sees the value plotted.
    const circle = document.createElementNS(SVG_NS, 'circle');
    circle.setAttribute('cx', '300');
    circle.setAttribute('cy', '60');
    circle.setAttribute('r', '3');
    circle.setAttribute('fill', 'var(--bs-primary, #0d6efd)');
    svg.appendChild(circle);
    const lbl = document.createElementNS(SVG_NS, 'text');
    lbl.setAttribute('x', '309');
    lbl.setAttribute('y', '63');
    lbl.setAttribute('font-size', '9');
    lbl.setAttribute('fill', 'currentColor');
    lbl.textContent = formatNumeric(numeric[0].v);
    svg.appendChild(lbl);
    return;
  }
  const minT = numeric[0].t;
  const maxT = numeric[numeric.length - 1].t;
  let minV = Infinity;
  let maxV = -Infinity;
  for (const p of numeric) {
    if (p.v < minV) minV = p.v;
    if (p.v > maxV) maxV = p.v;
  }
  const xSpan = maxT - minT || 1;
  const ySpan = maxV - minV || 1;
  const w = 600;
  const h = 120;
  const padX = 4;
  const padY = 8;
  const innerW = w - 2 * padX;
  const innerH = h - 2 * padY;
  const d = numeric.map((p, i) => {
    const x = padX + ((p.t - minT) / xSpan) * innerW;
    const y = padY + innerH - ((p.v - minV) / ySpan) * innerH;
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');

  const path = document.createElementNS(SVG_NS, 'path');
  path.setAttribute('d', d);
  path.setAttribute('fill', 'none');
  path.setAttribute('stroke', 'var(--bs-primary, #0d6efd)');
  path.setAttribute('stroke-width', '1.5');
  svg.appendChild(path);
  // Min/max value labels at top-left and bottom-left so the user can read the range without
  // adding axis ticks (which would balloon scope for what's meant to be a sparkline).
  const lblMax = document.createElementNS(SVG_NS, 'text');
  lblMax.setAttribute('x', String(padX));
  lblMax.setAttribute('y', String(padY + 4));
  lblMax.setAttribute('font-size', '9');
  lblMax.setAttribute('fill', 'currentColor');
  lblMax.textContent = formatNumeric(maxV);
  svg.appendChild(lblMax);
  const lblMin = document.createElementNS(SVG_NS, 'text');
  lblMin.setAttribute('x', String(padX));
  lblMin.setAttribute('y', String(h - padY));
  lblMin.setAttribute('font-size', '9');
  lblMin.setAttribute('fill', 'currentColor');
  lblMin.textContent = formatNumeric(minV);
  svg.appendChild(lblMin);
}

function clearResults(): void {
  if (dom.timeseriesResults) dom.timeseriesResults.replaceChildren();
}

function setStatus(text: string): void {
  if (dom.timeseriesStatus) dom.timeseriesStatus.textContent = text;
}

function formatValue(v: unknown): string {
  if (v === null || v === undefined) return '';
  if (typeof v === 'number' || typeof v === 'string' || typeof v === 'boolean') return String(v);
  try {
    return JSON.stringify(v);
  } catch {
    return String(v);
  }
}

function formatNumeric(n: number): string {
  // Three significant digits is enough for a sparkline label without overlapping the line.
  if (!Number.isFinite(n)) return '';
  const abs = Math.abs(n);
  if (abs >= 1000 || abs < 0.01) return n.toExponential(2);
  return n.toFixed(2);
}

/**
 * Converts a value from a {@code <input type="datetime-local">} (e.g. "2026-05-08T10:00") into a
 * UTC ISO-8601 instant ("2026-05-08T10:00:00Z"). The browser stores datetime-local in the user's
 * local clock, but we want the wire value to be UTC — so we treat the input as if the wall-clock
 * time the user typed was already UTC. This matches how operators usually think about
 * timeseries windows ("show me 10:00–11:00 UTC") and keeps the conversion logic-free.
 */
function toUtcIso(local: string): string {
  if (!local) return '';
  const trimmed = local.length === 16 ? `${local}:00` : local;
  return trimmed.endsWith('Z') ? trimmed : `${trimmed}Z`;
}

function toLocalInputValue(date: Date): string {
  // datetime-local expects "YYYY-MM-DDTHH:MM" without seconds/zone — but with step="1" we keep
  // seconds for precision. Render as if the date's UTC components were the local clock so the
  // user sees / writes UTC consistently across the form.
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}` +
      `T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`;
}
