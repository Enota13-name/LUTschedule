(function () {
  'use strict';
  function clean(s) { return String(s == null ? '' : s).replace(/\u00a0/g, ' ').trim(); }
  function pick(o, keys) { for (var i = 0; i < keys.length; i++) if (o && o[keys[i]] != null && clean(o[keys[i]]) !== '') return o[keys[i]]; return null; }
  function weeks(value) {
    if (Array.isArray(value)) {
      var array = value.map(Number); if (!array.length || array.some(function (n) { return !Number.isInteger(n) || n < 1 || n > 60; })) throw Error('课程周次无效');
      return Array.from(new Set(array)).sort(function (a, b) { return a - b; });
    }
    var s = clean(value).replace(/[，、；;]/g, ',').replace(/[－—–~～至]/g, '-');
    if (!s) throw Error('缺少课程周次；请选择含周次的学期课表');
    if (/^[01]{10,60}$/.test(s)) { var bit = []; for (var i = 0; i < s.length; i++) if (s[i] === '1') bit.push(i + 1); if (!bit.length) throw Error('课程没有有效周次'); return bit; }
    var odd = /单/.test(s), even = /双/.test(s);
    if (odd && even) throw Error('单双周标记冲突');
    var tokens = s.match(/\d+\s*-\s*\d+|\d+/g), out = [];
    if (!tokens) throw Error('无法识别课程周次');
    tokens.forEach(function (t) { var pair = t.split('-').map(Number), first = pair[0], last = pair.length === 1 ? first : pair[1];
      if (first < 1 || last < first || last > 60) throw Error('课程周次超出范围');
      for (var w = first; w <= last; w++) if ((!odd || w % 2 === 1) && (!even || w % 2 === 0)) out.push(w);
    });
    out = Array.from(new Set(out)).sort(function (a, b) { return a - b; }); if (!out.length) throw Error('没有有效课程周次'); return out;
  }
  function weekday(value) {
    var s = clean(value), match = s.match(/^(?:星期|周|礼拜)?\s*([一二三四五六日天1-7])$/);
    if (!match) throw Error('缺少或无法识别星期');
    return /[1-7]/.test(match[1]) ? Number(match[1]) : ({ '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 })[match[1]];
  }
  function periods(first, last) {
    var s = clean(first).replace(/[－—–~～至]/g, '-'), match = s.match(/^(?:第)?\s*(\d{1,2})(?:\s*[-,，]\s*(\d{1,2}))?\s*(?:节|小节)?$/);
    if (!match) throw Error('缺少或无法识别课程节次');
    var a = Number(match[1]), b = last != null ? Number(last) : Number(match[2] || match[1]);
    if (a < 1 || b < a || b > 24 || !Number.isInteger(b)) throw Error('课程节次范围无效'); return [a, b];
  }
  function course(name, teacher, room, day, first, last, rawWeeks) {
    name = clean(name); if (!name || name.length > 200) throw Error('课程名称无效');
    var p = periods(first, last); return { name: name, teacher: clean(teacher), room: clean(room), day: weekday(day), rawStart: p[0], rawEnd: p[1], weeks: weeks(rawWeeks) };
  }
  function meta(o) {
    return { semester: clean(pick(o, ['semester', 'semesterName', 'XNXQMC', 'xnxqmc', '学期'])),
      firstMonday: clean(pick(o, ['firstMonday', 'semesterFirstMonday', 'termFirstMonday'])), totalWeeks: Number(pick(o, ['totalWeeks', 'termWeeks'])) || 0, periodCount: Number(pick(o,['periodCount','totalPeriods'])) || 0 };
  }
  var body = document.body ? (document.body.innerText || '') : '';
  var result = { login: !!document.querySelector('input[type=password]') || (/请选择认证方式/.test(body) && /统一身份认证/.test(body)),
    courses: [], complete: false, semester: '', firstMonday: '', totalWeeks: 0, errors: [], tableCount: 0, links: [], structure: [] };
  if (result.login) return JSON.stringify(result);
  result.links = Array.from(document.querySelectorAll('a[href]')).map(function (a) { return { title: clean(a.innerText).slice(0, 60), url: a.href }; }).filter(function (a) { return a.title; }).slice(0, 120);
  var docs = [document];
  Array.from(document.querySelectorAll('iframe')).forEach(function (frame) { try { if (frame.contentDocument) docs.push(frame.contentDocument); } catch (_) {} });
  function structured(data) {
    var queue = [{ node: data, metadata: meta(data) }], visited = 0;
    while (queue.length && visited++ < 2000) {
      var entry = queue.shift(), node = entry.node;
      if (!node || typeof node !== 'object') continue;
      if (Array.isArray(node)) {
        var looks = node.filter(function (v) { return v && typeof v === 'object' && pick(v, ['KCMC', 'kcmc', 'courseName', 'name', '课程名称']) != null &&
          pick(v, ['XQJ', 'SKXQ', 'xqj', 'day', 'weekday', '星期']) != null; });
        if (looks.length && looks.length >= node.length / 2) {
          var normalized = [], failures = [];
          node.forEach(function (v) { try { normalized.push(course(pick(v, ['KCMC', 'kcmc', 'courseName', 'name', '课程名称']),
            pick(v, ['JSXM', 'jsxm', 'teacher', 'teacherName', '教师']), pick(v, ['JASMC', 'JSMC', 'jasmc', 'room', 'classroom', '教室']),
            pick(v, ['XQJ', 'SKXQ', 'xqj', 'day', 'weekday', '星期']), pick(v, ['KSJC', 'ksjc', 'startPeriod', 'rawStart', 'start', 'SKJC', 'skjc', '节次']),
            pick(v, ['JSJC', 'jsjc', 'endPeriod', 'rawEnd', 'end']), pick(v, ['weeks', 'SKZC', 'skzc', 'ZCMC', 'zcmc', 'weekText', '周次']))); } catch (e) { failures.push(e.message); } });
          return { courses: normalized, errors: failures, metadata: entry.metadata };
        }
        node.slice(0, 500).forEach(function (v) { queue.push({ node: v, metadata: entry.metadata }); });
      } else {
        var own = meta(node), inherited = { semester: own.semester || entry.metadata.semester, firstMonday: own.firstMonday || entry.metadata.firstMonday, totalWeeks: own.totalWeeks || entry.metadata.totalWeeks, periodCount: own.periodCount || entry.metadata.periodCount };
        Object.keys(node).slice(0, 100).forEach(function (key) { if (node[key] && typeof node[key] === 'object') queue.push({ node: node[key], metadata: inherited }); });
      }
    }
    return null;
  }
  function apply(parsed) {
    if (!parsed || !parsed.courses.length || parsed.errors.length) { if (parsed) result.errors = result.errors.concat(parsed.errors); return false; }
    result.courses = parsed.courses; result.complete = true;
    if (parsed.metadata) { result.semester = parsed.metadata.semester; result.firstMonday = parsed.metadata.firstMonday; result.totalWeeks = parsed.metadata.totalWeeks; result.periodCount=parsed.metadata.periodCount; }
    return true;
  }
  var emap = window.__lutEmap;
  if (emap && emap.href === location.href) {
    result.adapter = 'emap'; result.pending = emap.pending; result.login = emap.login; result.http = emap.http || 0; result.networkError = !!emap.networkError;
    result.structure = [{ adapter: 'emap', stages: emap.stages, pending: emap.pending }];
    if (emap.pending) return JSON.stringify(result);
    if (emap.login || emap.errors.length) { result.errors = emap.errors; return JSON.stringify(result); }
    apply(structured(emap.data)); result.pageUrl = location.href; result.readAt = emap.readAt;
    return JSON.stringify(result);
  }
  for (var di = 0; di < docs.length && !result.complete; di++) {
    var doc = docs[di], scripts = Array.from(doc.querySelectorAll('script[type="application/json"]'));
    var paged = !!doc.querySelector('.pagination,.pager,.datagrid-pager,.el-pagination,.ant-pagination,.layui-laypage,[role=navigation][aria-label*=pagination]');
    result.structure.push({ tables: doc.querySelectorAll('table').length, jsonBlocks: scripts.length, frames: doc.querySelectorAll('iframe').length, paged: paged });
    if (paged) { result.errors.push('页面有分页控件；请打开不分页的完整学期课表，避免只保存部分课程'); continue; }
    for (var si = 0; si < scripts.length; si++) { try { if (apply(structured(JSON.parse(scripts[si].textContent)))) break; } catch (_) {} }
    if (result.complete) { result.pageUrl = doc.URL || ''; break; }
    var tables = Array.from(doc.querySelectorAll('table')); result.tableCount += tables.length;
    for (var ti = 0; ti < tables.length && !result.complete; ti++) {
      var table = tables[ti], rows = Array.from(table.rows || []); if (!rows.length) continue;
      // List layout: require explicit name, weekday, periods and weeks, never infer an entire term from a week-only view.
      var header = -1, columns;
      for (var ri = 0; ri < Math.min(rows.length, 5); ri++) {
        var labels = Array.from(rows[ri].cells).map(function (c) { return clean(c.innerText || c.textContent).replace(/\s/g, ''); });
        function idx(regex) { return labels.findIndex(function (s) { return regex.test(s); }); }
        columns = { name: idx(/^(课程名称|课程|科目)$/), day: idx(/^(星期|星期几|上课星期|星期序号)$/), periods: idx(/^(节次|上课节次|小节)$/), weeks: idx(/^(周次|上课周次|周数)$/), teacher: idx(/^(教师|任课教师|教师姓名)$/), room: idx(/^(教室|上课地点|地点)$/) };
        if (columns.name >= 0 && columns.day >= 0 && columns.periods >= 0 && columns.weeks >= 0) { header = ri; break; }
      }
      if (header >= 0) {
        var items = [], failures = [];
        for (var r = header + 1; r < rows.length; r++) {
          var cells = Array.from(rows[r].cells).map(function (c) { return clean(c.innerText || c.textContent); });
          if (!cells.join('').trim() || /^(暂无|没有|无课程)/.test(cells.join(''))) continue;
          try { items.push(course(cells[columns.name], cells[columns.teacher], cells[columns.room], cells[columns.day], cells[columns.periods], null, cells[columns.weeks])); } catch (e) { failures.push(e.message); }
        }
        if (apply({ courses: items, errors: failures })) break;
        continue;
      }
      // Week-grid layout. Expand row/column spans so twelve actual periods remain intact.
      var matrix = [], placements = [];
      rows.forEach(function (row, r) { if (!matrix[r]) matrix[r] = []; var col = 0;
        Array.from(row.cells).forEach(function (cell) { while (matrix[r][col]) col++; var rs = Math.max(1, cell.rowSpan || 1), cs = Math.max(1, cell.colSpan || 1);
          var placement = { cell: cell, row: r, col: col, rowSpan: rs, colSpan: cs }; placements.push(placement);
          for (var rr = r; rr < Math.min(r + rs, 40); rr++) { if (!matrix[rr]) matrix[rr] = []; for (var cc = col; cc < Math.min(col + cs, 25); cc++) matrix[rr][cc] = placement; } col += cs;
        });
      });
      var dayCols = {}, headerRow = -1;
      placements.forEach(function (p) { var s = clean(p.cell.innerText || p.cell.textContent); var m = s.match(/^(?:星期|周)([一二三四五六日天])(?:\s|$)/); if (m) { dayCols[p.col] = weekday(m[1]); headerRow = Math.max(headerRow, p.row); } });
      if (Object.keys(dayCols).length < 5) continue;
      var rawRows = {};
      for (var gr = headerRow + 1; gr < matrix.length; gr++) {
        var start = Math.min.apply(null, Object.keys(dayCols).map(Number)), leftText = [];
        for (var gc = 0; gc < start; gc++) if (matrix[gr] && matrix[gr][gc] && matrix[gr][gc].row === gr) leftText.push(clean(matrix[gr][gc].cell.innerText || matrix[gr][gc].cell.textContent));
        var direct = leftText.join(' ').match(/(?:^|\s)(?:第)?(\d{1,2})(?:\s*[-－]\s*(\d{1,2}))?\s*(?:节|小节)?(?:\s|$)/);
        if (direct) rawRows[gr] = [Number(direct[1]), Number(direct[2] || direct[1])];
      }
      var gridCourses = [], gridErrors = [];
      placements.forEach(function (p) { if (!dayCols[p.col] || p.row <= headerRow) return; var cellText = clean(p.cell.innerText || p.cell.textContent); if (!cellText || /^无课$/.test(cellText)) return;
        if (!rawRows[p.row]) { gridErrors.push('课表行缺少明确节次'); return; }
        if (p.colSpan > 1) { gridErrors.push('课程跨多个星期列，当前格式无法可靠拆分'); return; }
        var blockTexts = cellText.split(/\n\s*\n+/).filter(Boolean);
        blockTexts.forEach(function (block) { var lines = block.split(/\n/).map(clean).filter(Boolean), weekMatch = block.match(/((?:\d+\s*(?:[-－—–~～至]\s*\d+)?\s*[,，、]?\s*)+)(?:\s*周)(?:\s*[（(]?([单双])(?:周)?[)）]?)?/);
          if ((block.match(/\d\s*周/g) || []).length > 1) { gridErrors.push('同一单元格有多条课程，当前格式无法完整分离'); return; }
          if (!weekMatch) { gridErrors.push('课程缺少明确周次，请打开学期课表'); return; }
          var slotMatch = block.match(/(?:第|节次[:：]?\s*)(\d{1,2})(?:\s*[-－]\s*(\d{1,2}))?\s*节/);
          var period = rawRows[p.row], last = rawRows[p.row + p.rowSpan - 1];
          if (!slotMatch && p.rowSpan > 1 && !last) { gridErrors.push('跨行课程缺少结束节次，不能截断课程'); return; }
          var first = slotMatch ? Number(slotMatch[1]) : period[0], end = slotMatch ? Number(slotMatch[2] || slotMatch[1]) : last ? last[1] : period[1];
          var name = lines[0].replace(/^(课程名称|课程)[:：]\s*/, ''), teacher = '', room = '';
          lines.slice(1).forEach(function (line) { if (/^(教师|任课教师)[:：]/.test(line)) teacher = line.replace(/^[^:：]+[:：]\s*/, ''); if (/^(地点|教室|上课地点)[:：]/.test(line)) room = line.replace(/^[^:：]+[:：]\s*/, ''); });
          try { gridCourses.push(course(name, teacher, room, dayCols[p.col], first, end, weekMatch[1] + (weekMatch[2] || ''))); } catch (e) { gridErrors.push(e.message); }
        });
      });
      apply({ courses: gridCourses, errors: gridErrors });
      if(result.complete){var maxPeriod=0;Object.keys(rawRows).forEach(function(row){maxPeriod=Math.max(maxPeriod,rawRows[row][1]);});result.periodCount=maxPeriod;}
    }
    if (result.complete) result.pageUrl = doc.URL || '';
  }
  if (!result.semester) { var semesters = Array.from(new Set(body.match(/\d{4}\s*[-－]\s*\d{4}\s*学年\s*第?[一二12]\s*学期/g) || [])); if (semesters.length === 1) result.semester = semesters[0]; }
  result.errors = Array.from(new Set(result.errors)).slice(0, 5);
  if (!result.complete && !result.errors.length) result.errors.push('未找到含课程、星期、节次和周次的学期课表');
  return JSON.stringify(result);
})()
