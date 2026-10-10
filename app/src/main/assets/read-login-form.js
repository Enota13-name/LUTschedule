'use strict';
if (window.top !== window.self || location.protocol !== 'https:'
    || location.hostname !== 'jwxt.lut.edu.cn'
    || location.pathname !== '/jwapp/sys/yjsrzfwapp/dbLogin/index.do') {
  return JSON.stringify({ status: 'blocked' });
}

var username = document.querySelector("#userId[name='userId'][type='text']");
var password = document.querySelector("#password[name='password'][type='password']");
var button = document.querySelector("button#loginBtn[type='button']");
if (!username || !password || !button || !canSubmit) return JSON.stringify({ status: 'manual' });

// Respect manually entered values and never combine unrelated credentials.
if ((username.value && username.value !== savedUser) || (password.value && password.value !== savedPassword)) {
  return JSON.stringify({ status: 'manual' });
}
if (!username.value) setValue(username, savedUser);
if (!password.value) setValue(password, savedPassword);
if (!username.value || !password.value) return JSON.stringify({ status: 'manual' });
if (hasVisibleChallenge()) return JSON.stringify({ status: 'challenge' });

// Click only the verified official button; never submit the empty-action GET form.
button.click();
return JSON.stringify({ status: 'submitted' });

function setValue(input, value) {
  var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
  setter.call(input, value);
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
}
function visible(element) {
  if (!element || !element.getBoundingClientRect) return false;
  var style = window.getComputedStyle(element), rect = element.getBoundingClientRect();
  return style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity) !== 0
    && rect.width > 0 && rect.height > 0;
}
function hasVisibleChallenge() {
  var challengeName = /captcha|verify|vcode|sms|otp|token|验证码|短信|二次认证|动态口令|安全验证/i;
  var controls = document.querySelectorAll('input,textarea,select,iframe,canvas,img,[role="dialog"],[role="alertdialog"]');
  for (var i = 0; i < controls.length; i++) {
    var el = controls[i];
    if (!visible(el)) continue;
    if (el === username || el === password) continue;
    var marker = [el.id, el.name, el.className, el.getAttribute('aria-label'), el.title].join(' ');
    if (challengeName.test(marker)) return true;
    if (el.tagName === 'IFRAME' || el.tagName === 'CANVAS') return true;
    if (el.tagName === 'INPUT' && /^(text|tel|number)$/i.test(el.type || 'text')) return true;
  }
  var visibleText = document.body ? document.body.innerText || '' : '';
  return /验证码|短信验证|二次认证|动态口令|安全验证|输入校验码/i.test(visibleText);
}
