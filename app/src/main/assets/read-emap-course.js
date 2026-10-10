(function () {
  'use strict';
  // Read-only calls in the official origin, using the user's WebView session.
  if (location.hostname !== 'jwxt.lut.edu.cn' || !/^\/jwapp\/sys\/wdkb\/(?:\*|%2a)default\/index\.do$/i.test(location.pathname)) return;
  var text = document.body ? document.body.innerText : '';
  if (document.querySelector('input[type=password]') || /请选择认证方式/.test(text)) return;
  if (window.__lutEmap && window.__lutEmap.pending && window.__lutEmap.href === location.href) return;
  var state = { href: location.href, pending: true, data: null, errors: [], login: false, adapter: 'emap', stages: [] };
  window.__lutEmap = state;
  function failure(message, login, http, networkError) { var e = Error(message); e.login = !!login; e.http = http || 0; e.networkError = !!networkError; return e; }
  function looksLikeLoginHtml(body) { return typeof body === 'string' && /<input[^>]+type\s*=\s*["']?password|请选择认证方式|统一身份认证/i.test(body); }
  function checkedRows(payload, action, all) {
    if (!payload || String(payload.code) !== '0') throw failure('接口返回错误，未保存课程');
    var block = payload.datas && payload.datas[action];
    if (!block || !block.extParams || String(block.extParams.code) !== '1' || !Array.isArray(block.rows)) throw failure('接口未确认查询成功，未保存课程');
    if (all) {
      var count = block.rows.length, total = Number(block.totalSize), size = Number(block.pageSize);
      if (block.totalSize != null && Number.isFinite(total) && total >= 0) {
        if (total !== count) throw failure('课表记录不完整，已保留缓存');
      } else if (block.pageSize == null || size !== 0) throw failure('无法确认接口是否返回全部课程，已保留缓存');
      if (!count || count > 500) throw failure('没有可验证的课程记录或记录过多，已保留缓存');
    }
    return block.rows;
  }
  function post(action, params) {
    var path = '/jwapp/sys/wdkb/modules/' + action + '.do', controller = new AbortController();
    // Reuse the website's AJAX setup when available, including its request headers.
    if(window.jQuery&&typeof window.jQuery.ajax==='function'){state.stages.push(action.split('/').pop());return new Promise(function(resolve,reject){window.jQuery.ajax({url:path,type:'POST',data:params||{},dataType:'json',timeout:6500}).done(resolve).fail(function(xhr,status){var code=Number(xhr&&xhr.status)||0, body=xhr&&typeof xhr.responseText==='string'?xhr.responseText:'';var login=code===401||(code===200&&status==='parsererror'&&looksLikeLoginHtml(body));var network=code===0&&(status==='timeout'||status==='error'||status==='abort');reject(failure(status==='timeout'?'课表接口读取超时':'官网查询未成功',login,code,network));});});}
    var timer = setTimeout(function () { controller.abort(); }, 6500);
    state.stages.push(action.split('/').pop());
    return fetch(path, { method: 'POST', credentials: 'same-origin', signal: controller.signal,
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
      body: new URLSearchParams(params || {}).toString() }).then(function (response) {
      if (response.status === 401) throw failure('官网登录已失效，请重新登录', true, response.status);
        if (!response.ok) throw failure('课表接口暂不可用（HTTP ' + response.status + '）', false, response.status);
        return response.text().then(function(body){ return { body: body, status: response.status }; });
      }).then(function (result) {
        if (looksLikeLoginHtml(result.body)) throw failure('接口返回登录页面，请重新登录', true, result.status);
        try { return JSON.parse(result.body); } catch (_) { throw failure('接口没有返回可识别的课程数据', false, result.status); }
      }).finally(function () { clearTimeout(timer); });
  }
  function bitmap(value) {
    var bits = typeof value === 'string' ? value.trim() : Array.isArray(value) ? value.join('') : '';
    if (!/^[01]{1,60}$/.test(bits)) return null;
    var list = []; for (var i = 0; i < bits.length; i++) if (bits.charAt(i) === '1') list.push(i + 1);
    if (!list.length) throw failure('课程周次位图为空，不能覆盖缓存'); return list;
  }
  function normalize(rows, term, label, calendar, currentWeek, queryDate) {
    var start = calendar && calendar.XQKSRQ, firstMonday = '';
    if (typeof start === 'string') {
      var m = start.match(/^(\d{4})[-/](\d{2})[-/](\d{2})(?:$|\s|T)/);
      if (m && new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]))).getUTCDay() === 1) firstMonday = m[1] + '-' + m[2] + '-' + m[3];
    }
    if(!firstMonday&&currentWeek&&Number(currentWeek.ZC)>=1&&Number(currentWeek.ZC)<=60&&Number(currentWeek.XQJ)>=1&&Number(currentWeek.XQJ)<=7){var date=new Date(queryDate+'T00:00:00Z');if(date.getUTCDay()===Number(currentWeek.XQJ)%7){date.setUTCDate(date.getUTCDate()-(Number(currentWeek.XQJ)-1)-(Number(currentWeek.ZC)-1)*7);firstMonday=date.toISOString().slice(0,10);}}
    var courses = rows.map(function (row) {
      if (row.XNXQDM && String(row.XNXQDM) !== term) throw failure('接口返回了其他学期课程，已保留缓存');
      var weeks = bitmap(row.SKZC); if (!weeks) weeks = row.ZCMC;
      if (!weeks) throw failure('接口课程缺少周次，已保留缓存');
      return { name: row.KCM || row.KCMC, teacher: row.SKJS || row.JSXM || '', room: row.JASMC || row.JSMC || '',
        day: row.SKXQ != null ? row.SKXQ : row.XQJ, rawStart: row.KSJC, rawEnd: row.JSJC, weeks: weeks };
    });
    var count=0;Array.prototype.forEach.call(document.querySelectorAll('[data-unit]'),function(n){count=Math.max(count,Number(n.getAttribute('data-unit'))||0);});
    return { semester: label || term, firstMonday: firstMonday, totalWeeks:Number(calendar&&calendar.ZZC)||0,periodCount:count,courses: courses };
  }
  post('jshkcb/dqxnxq', {}).then(function (payload) {
    var terms = checkedRows(payload, 'dqxnxq', false);
    if (terms.length !== 1) throw failure('接口未提供唯一的当前学期，未猜测学期日期');
    var term = String(terms[0].DM || terms[0].XNXQDM || '');
    if (!/^\d{4}-\d{4}-[123]$/.test(term)) throw failure('当前学期代码无法识别');
    var label = terms[0].MC || terms[0].XNXQDM_DISPLAY || term, pieces = term.split('-');
    var formatted=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date()),dateParts={};formatted.forEach(function(p){dateParts[p.type]=p.value;});var queryDate=dateParts.year+'-'+dateParts.month+'-'+dateParts.day;
    return Promise.all([
      post('xskcb/cxxszhxqkb',{XNXQDM:term,pageNumber:'1',pageSize:'1000'}).then(function(p){return checkedRows(p,'cxxszhxqkb',true);}),
      post('xskcb/cxxljc',{XN:pieces[0]+'-'+pieces[1],XQ:pieces[2]}).then(function(p){return checkedRows(p,'cxxljc',false)[0]||null;}),
      post('jshkcb/dqzc',{XN:pieces[0]+'-'+pieces[1],XQ:pieces[2],RQ:queryDate}).then(function(p){return checkedRows(p,'dqzc',false)[0]||null;})
    ]).then(function(parts){state.data=normalize(parts[0],term,label,parts[1],parts[2],queryDate);state.readAt=Date.now();});
  }).catch(function (error) { state.login = !!error.login; state.http = error.http || 0; state.networkError = !!error.networkError || error.name === 'AbortError' || error.name === 'TypeError'; state.errors = [error.name === 'AbortError' ? '课表接口读取超时，已保留缓存' : error.message]; })
    .finally(function () { state.pending = false; });
})()
