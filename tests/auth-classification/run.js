const fs = require('fs');
const vm = require('vm');
const path = require('path');
const assert = require('assert');

const adapterPath = path.resolve(__dirname, '../../app/src/main/assets/read-emap-course.js');
const adapter = fs.readFileSync(adapterPath, 'utf8');
const readerPath = path.resolve(__dirname, '../../app/src/main/assets/read-course-page.js');
const reader = fs.readFileSync(readerPath, 'utf8');
const termPayload = { code: '0', datas: { dqxnxq: { extParams: { code: '1' }, rows: [{ DM: '2026-2027-1', MC: '合成学期' }] } } };
const rowsPayload = { code: '0', datas: { cxxszhxqkb: { extParams: { code: '1' }, totalSize: 1, pageSize: 1000, rows: [{ KCM: '合成课程', SKZC: '1', SKXQ: 1, KSJC: 1, JSJC: 2 }] } } };
const emptyPayload = action => ({ code: '0', datas: { [action]: { extParams: { code: '1' }, rows: [] } } });

function payloadFor(url) {
  if (url.includes('/jshkcb/dqxnxq.')) return termPayload;
  if (url.includes('/xskcb/cxxszhxqkb.')) return rowsPayload;
  if (url.includes('/xskcb/cxxljc.')) return emptyPayload('cxxljc');
  if (url.includes('/jshkcb/dqzc.')) return emptyPayload('dqzc');
  throw new Error('Unexpected synthetic URL: ' + url);
}

function makeEnv(transport, scenario) {
  const location = { hostname: 'jwxt.lut.edu.cn', pathname: '/jwapp/sys/wdkb/*default/index.do', href: 'https://jwxt.lut.edu.cn/jwapp/sys/wdkb/*default/index.do' };
  const window = {};
  const document = { body: { innerText: '' }, querySelector: () => null, querySelectorAll: () => [] };
  const fakeTimers = new Map(); let timerId = 0;
  const setTimeoutFake = fn => { const id = ++timerId; fakeTimers.set(id, fn); return id; };
  const clearTimeoutFake = id => fakeTimers.delete(id);
  const context = { window, document, location, URLSearchParams, AbortController,
    Intl, Date, Error, Promise, Array, String, Number, Math, JSON,
    setTimeout: setTimeoutFake, clearTimeout: clearTimeoutFake,
    fetch: undefined, console: { error() {}, log() {} } };

  if (transport === 'fetch') {
    context.fetch = async url => {
      if (scenario.kind === 'network') throw new TypeError('synthetic network failure');
      if (scenario.kind === 'abort') { const error = new Error('synthetic abort'); error.name = 'AbortError'; throw error; }
      const status = scenario.status ?? 200;
      const body = scenario.kind === 'loginHtml' ? '<html><body><input type="password"> 统一身份认证</body></html>' :
        status >= 400 ? 'synthetic error' : JSON.stringify(payloadFor(url));
      return { status, ok: status >= 200 && status < 300, text: async () => body };
    };
  } else {
    window.jQuery = { ajax(options) {
      let success, failure;
      const request = { done(fn) { success = fn; queueMicrotask(() => {
        if (scenario.kind === 'normal') success(payloadFor(options.url));
        else if (scenario.kind === 'loginHtml') failure({ status: 200, responseText: '<html><input type="password">统一身份认证</html>' }, 'parsererror');
        else if (scenario.kind === 'htmlParseError') failure({ status: 200, responseText: '<html>synthetic private response marker</html>' }, 'parsererror');
        else if (scenario.kind === 'network') failure({ status: 0 }, 'error');
        else if (scenario.kind === 'timeout') failure({ status: 0 }, 'timeout');
        else failure({ status: scenario.status }, 'error');
      }); return request; }, fail(fn) { failure = fn; return request; } };
      return request;
    } };
  }
  vm.runInNewContext(adapter, context, { filename: 'production-read-emap-course.js', timeout: 1000 });
  return { state: window.__lutEmap, context, reader };
}

async function runCase(transport, scenario) {
  const { state, context, reader } = makeEnv(transport, scenario);
  for (let i = 0; i < 30 && state.pending; i++) await Promise.resolve();
  assert.strictEqual(state.pending, false, `${transport}/${scenario.name} did not settle`);
  const page = JSON.parse(vm.runInNewContext(reader, context, { filename: 'production-read-course-page.js', timeout: 1000 }));
  return { transport, scenario: scenario.name, login: state.login, http: state.http || 0,
    networkError: !!state.networkError, complete: !!state.data, page: {
      login: !!page.login, http: page.http || 0, networkError: !!page.networkError, complete: !!page.complete
    }, error: state.errors[0] || null };
}

async function main() {
  const cases = [
    { name: 'HTTP 401', status: 401, expected: { fetch: [true, 401, false, false], jquery: [true, 401, false, false] } },
    { name: 'HTTP 403', status: 403, expected: { fetch: [false, 403, false, false], jquery: [false, 403, false, false] } },
    { name: 'HTTP 429', status: 429, expected: { fetch: [false, 429, false, false], jquery: [false, 429, false, false] } },
    { name: 'HTTP 503', status: 503, expected: { fetch: [false, 503, false, false], jquery: [false, 503, false, false] } },
    { name: 'HTTP 200 login HTML', kind: 'loginHtml', status: 200, expected: { fetch: [true, 200, false, false], jquery: [true, 200, false, false] } },
    { name: 'jQuery non-login parsererror', kind: 'htmlParseError', expected: { jquery: [false, 200, false, false] } },
    { name: 'network failure', kind: 'network', expected: { fetch: [false, 0, true, false], jquery: [false, 0, true, false] } },
    { name: 'fetch AbortError', kind: 'abort', expected: { fetch: [false, 0, true, false] } },
    { name: 'jQuery timeout', kind: 'timeout', expected: { jquery: [false, 0, true, false] } },
    { name: 'normal synthetic data', kind: 'normal', status: 200, expected: { fetch: [false, 0, false, true], jquery: [false, 0, false, true] } }
  ];
  const results = [];
  for (const transport of ['fetch', 'jquery']) {
    for (const scenario of cases) if (scenario.expected[transport]) results.push(await runCase(transport, scenario));
  }
  for (const result of results) {
    const expected = cases.find(c => c.name === result.scenario).expected[result.transport];
    assert(expected, `Missing expectations for ${result.transport}/${result.scenario}`);
    assert.deepStrictEqual([result.login, result.http, result.networkError, result.complete], expected,
      `${result.transport}/${result.scenario} adapter classification`);
    assert.deepStrictEqual([result.page.login, result.page.http, result.page.networkError, result.page.complete], expected,
      `${result.transport}/${result.scenario} read-course-page propagation`);
    assert(!String(result.error || '').includes('synthetic private response marker'),
      `${result.transport}/${result.scenario} must not expose response body`);
  }
  process.stdout.write(JSON.stringify({ source: path.relative(process.cwd(), adapterPath), reader: path.relative(process.cwd(), readerPath),
    networkAccess: false, scenarioCount: results.length,
    note: 'Synthetic behavior of current production scripts; not a fix or live-site measurement.', results }, null, 2) + '\n');
}

main().catch(error => { console.error(error.stack || error); process.exitCode = 1; });
