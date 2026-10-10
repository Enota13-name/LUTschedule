'use strict';
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/read-login-form.js'), 'utf8');

function element(tagName, id, name, type) {
  return { tagName, id, name, type, className: '', title: '', attrs: {},
    getAttribute(name) { return this.attrs[name] || ''; },
    getBoundingClientRect() { return { width: 100, height: 20 }; },
    dispatchEvent() {}, click() { this.clicked = true; } };
}

function run({ origin, inFrame = false, challenge = false, username = '', password = '' }) {
  const htmlInputPrototype = {};
  Object.defineProperty(htmlInputPrototype, 'value', { get() { return this._value || ''; }, set(v) { this._value = v; } });
  const user = element('INPUT', 'userId', 'userId', 'text'); Object.setPrototypeOf(user, htmlInputPrototype); user.value = username;
  const pass = element('INPUT', 'password', 'password', 'password'); Object.setPrototypeOf(pass, htmlInputPrototype); pass.value = password;
  const button = element('BUTTON', 'loginBtn', '', 'button');
  const challengeInput = element('INPUT', 'vcode', 'vcode', 'text');
  const controls = challenge ? [challengeInput] : [];
  const document = { body: { innerText: challenge ? '验证码' : '' },
    querySelector(selector) { if (selector.startsWith('#userId')) return user; if (selector.startsWith('#password')) return pass; if (selector.startsWith('button#loginBtn')) return button; return null; },
    querySelectorAll() { return controls; } };
  const window = { getComputedStyle() { return { display: 'block', visibility: 'visible', opacity: '1' }; } };
  window.top = inFrame ? {} : window; window.self = window;
  const context = { window, document, location: { protocol: new URL(origin).protocol, hostname: new URL(origin).hostname, pathname: '/jwapp/sys/yjsrzfwapp/dbLogin/index.do', origin },
    HTMLInputElement: { prototype: htmlInputPrototype }, Event: function Event() {}, JSON, console: { log() { throw new Error('unexpected log'); }, error() { throw new Error('unexpected error log'); } } };
  const wrapper = `(function(savedUser,savedPassword,canSubmit){${source}\n})(${JSON.stringify('synthetic-user\u0022);window.pwned=true;//')},${JSON.stringify('synthetic-password')},true)`;
  let result;
  try { result = vm.runInNewContext(wrapper, context); } catch (e) { throw e; }
  return { result: JSON.parse(result), user, pass, button, window };
}

const official = 'https://jwxt.lut.edu.cn';
let result = run({ origin: official });
assert.strictEqual(result.result.status, 'submitted', JSON.stringify({ user: result.user.value, pass: result.pass.value, clicked: result.button.clicked }));
assert.strictEqual(result.user.value, 'synthetic-user");window.pwned=true;//');
assert.strictEqual(result.pass.value, 'synthetic-password');
assert.strictEqual(result.button.clicked, true);
assert.strictEqual(result.window.pwned, undefined);
assert.strictEqual(result.window.LutCredentialBridge, undefined);
assert.strictEqual(result.window.savedUser, undefined);
assert.strictEqual(result.window.savedPassword, undefined);

result = run({ origin: 'https://attacker.example' });
assert.strictEqual(result.result.status, 'blocked');
assert.strictEqual(result.user.value, '');
assert.strictEqual(result.button.clicked, undefined);

result = run({ origin: official, inFrame: true });
assert.strictEqual(result.result.status, 'blocked');
assert.strictEqual(result.user.value, '');
assert.strictEqual(result.button.clicked, undefined);

result = run({ origin: official, challenge: true });
assert.strictEqual(result.result.status, 'challenge');
assert.strictEqual(result.button.clicked, undefined);

result = run({ origin: official, username: 'manual-user' });
assert.strictEqual(result.result.status, 'manual');
assert.strictEqual(result.pass.value, '');
assert.strictEqual(result.button.clicked, undefined);

process.stdout.write(JSON.stringify({ scenarioCount: 5, networkAccess: false, result: 'passed' }) + '\n');
