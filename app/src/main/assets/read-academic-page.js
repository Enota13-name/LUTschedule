(function(kind){
  'use strict';
  function clean(v){return v==null?'':String(v).replace(/\s+/g,' ').trim();}
  var body=document.body?document.body.innerText:'',result={kind:kind,complete:false,records:[],links:[],login:false};
  if(location.hostname!=='jwxt.lut.edu.cn')return JSON.stringify(result);
  result.login=!!document.querySelector('input[type=password]')||/请选择认证方式|请先登录|登录超时/.test(body);
  if(result.login)return JSON.stringify(result);
  result.portal=!!document.querySelector('.p_card_name')&&!!document.querySelector('.p_title');
  result.authenticated=result.portal||/我的课表/.test(body)&&/成绩查询/.test(body)||/退出登录/.test(body);
  var nodes=document.querySelectorAll('a,[data-url],[data-href],[onclick]');
  Array.prototype.forEach.call(nodes,function(node){var title=clean(node.innerText||node.getAttribute('title')||node.getAttribute('aria-label'));var url=node.getAttribute('href')||node.getAttribute('data-url')||node.getAttribute('data-href')||'';
    if(!url){var m=(node.getAttribute('onclick')||'').match(/["']((?:https:\/\/jwxt\.lut\.edu\.cn)?\/jwapp\/[^"']+)["']/);if(m)url=m[1];}
    try{var u=new URL(url,location.href);if(title&&url&&u.origin===location.origin&&/^\/jwapp\//.test(u.pathname)&&!/[?&#;](?:ticket|token|code|session|password|pwd)=/i.test(u.href))result.links.push({title:title.substring(0,100),url:u.href});}catch(_){} });
  Array.prototype.forEach.call(document.querySelectorAll('.p_card'),function(card){var name=card.querySelector('.p_card_name'),link=card.querySelector('[data-url]');if(!name||!link)return;try{var u=new URL(link.getAttribute('data-url'),location.href);if(u.origin===location.origin&&/^\/jwapp\/sys\/emaphome\/appShow\.do$/.test(u.pathname)&&/^[a-f0-9]{32}$/.test(u.searchParams.get('id')||''))result.links.push({title:clean(name.textContent),url:u.href});}catch(_){} });
  var scope=clean((document.querySelector('[data-student-id]')||{}).getAttribute&&document.querySelector('[data-student-id]').getAttribute('data-student-id'));
  function pick(row,keys){for(var i=0;i<keys.length;i++)if(row[keys[i]]!=null&&clean(row[keys[i]])!=='')return clean(row[keys[i]]);return '';}
  function parseDate(raw){var m=clean(raw).match(/(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})/);if(!m)return '';return m[1]+'-'+('0'+m[2]).slice(-2)+'-'+('0'+m[3]).slice(-2);}
  function clock(raw){var m=clean(raw).match(/(?:^|\s)(\d{1,2}):(\d{2})/);return m?('0'+m[1]).slice(-2)+':'+m[2]:'';}
  function normalize(row){var name=pick(row,['KCM','KCMC','课程名称','课程名','课程','科目','name']),term=pick(row,['XNXQDM','XNXQ','学年学期','学期','semester']),id=pick(row,['WID','ID','KCH','KCDM','课程号','id']);if(!name)throw Error('缺少课程名称');
    var record={name:name,term:term,key:(term+'|'+(id||name)+'|'+pick(row,['CXCKDM','CXCKDM_DISPLAY','重修重考','考试性质','KSXZ','KSXZDM','attempt']))};
    if(kind==='grades'){record.score=pick(row,['XSZCJMC','ZCJ','CJ','CJCJ','ZPCJ','总评成绩','成绩','总成绩','score']);if(record.score===''&&!Object.keys(row).some(function(k){return /^(ZCJ|CJ|CJCJ|ZPCJ|总评成绩|成绩|总成绩|score)$/.test(k);}))throw Error('缺少成绩字段');}
    else{var date=pick(row,['KSRQ','KSKSRQ','考试日期','考试时间','date']),start=pick(row,['KSSJMS','KSKSSJ','KSSJ','考试开始时间','开始时间','startTime']),end=pick(row,['KSJSSJ','JSSJ','考试结束时间','结束时间','endTime']);
      var range=(date+' '+start).match(/(\d{1,2}:\d{2})\s*[-－–~至]\s*(\d{1,2}:\d{2})/);if(range){start=range[1];end=range[2];}
      record.date=parseDate(date)||parseDate(pick(row,['KSSJMS']));record.startTime=clock(start);record.endTime=clock(end);record.room=pick(row,['JASMC','JSMC','KSDD','考试地点','考场','地点','room']);if(!record.date||!record.startTime||!record.endTime)throw Error('考试时间不完整');
    }
    var student=pick(row,['XH','学号','studentId']);if(student){if(scope&&scope!==student)throw Error('查询包含其他账号');scope=student;}return record;
  }
  if(kind==='catalog'){result.complete=true;return JSON.stringify(result);}
  if(kind!=='grades'&&kind!=='exams'){var label=kind.replace(/^service:/,'');result.records=[{key:kind,name:label,text:clean(body).substring(0,24000)}];result.complete=body.length>30&&body.indexOf(label)>=0&&!(/我的课表/.test(body)&&/缓考申请/.test(body));return JSON.stringify(result);}
  try{
    var query=(window.__lutAcademicQuery||{})[kind];
    if(query&&query.href===location.href){if(query.pending){result.pending=true;return JSON.stringify(result);}if(query.error){result.reason=query.error;return JSON.stringify(result);}var action=kind==='grades'?'xscjcx':'cxxsksap',official=query.data&&query.data.datas&&query.data.datas[action];if(!official||Number(official.totalSize)!==official.rows.length)throw Error('官网查询不完整');
      var source=official.rows;result.unscheduledRecords=[];
      if(kind==='exams')source=source.filter(function(row){if(row.KSRWZT!=null||row.SFFBSJ!=null){if(String(row.KSRWZT)!=='1'&&String(row.SFFBSJ)!=='1')return false;if(String(row.SFXYAPKS)!=='1'||String(row.SFAPSJ)!=='1'){result.unscheduledRecords.push({name:pick(row,['KCM','KCMC']),reason:'官网尚未提供明确考试时间'});return false;}}return true;});
      result.records=source.map(normalize);result.complete=true;result.verifiedEmpty=!source.length;result.account=scope;return JSON.stringify(result);
    }
    var captured=(window.__lutCapture||{}).responses||[],candidates=[];
    captured.forEach(function(response){var data=response.data;if(String(data.code)!=='0')return;Object.keys(data.datas||{}).forEach(function(action){var block=data.datas[action];if(!block||!Array.isArray(block.rows)||!block.extParams||String(block.extParams.code)!=='1')return;
      if(!new RegExp(kind==='grades'?'cj|成绩':'ks|考试','i').test(action+' '+response.url))return;
      if(Number(block.totalSize)!==block.rows.length||block.totalSize==null)return;candidates.push(block.rows);
    });});
    if(candidates.length){var rows=candidates[candidates.length-1];result.records=rows.map(normalize);result.complete=true;result.verifiedEmpty=rows.length===0;result.account=scope;return JSON.stringify(result);}
    var tables=document.querySelectorAll('table,[role=grid],.bh-table,.bh-grid');
    for(var t=0;t<tables.length;t++){var table=tables[t],trs=table.querySelectorAll('tr,[role=row],.bh-grid-row'),headers=null,items=[],started=false;
      var gridHeaders=Array.prototype.map.call(table.querySelectorAll('[role=columnheader],.jqx-grid-column-header'),function(node){return clean(node.innerText);});
      if(gridHeaders.some(function(v){return /^(课程名称|课程名|课程|科目)$/.test(v);})&&gridHeaders.some(function(v){return kind==='grades'?/^(成绩|总评成绩|总成绩)$/.test(v):/^考试(日期|时间)$/.test(v);})){headers=gridHeaders;started=true;}
      for(var i=0;i<trs.length;i++){var cells=trs[i].querySelectorAll('th,td,[role=columnheader],[role=gridcell],.bh-grid-cell');var values=Array.prototype.map.call(cells,function(c){return clean(c.innerText);});
        if(!headers){if(values.some(function(v){return /^(课程名称|课程名|课程|科目)$/.test(v);})&&values.some(function(v){return kind==='grades'?/^(成绩|总评成绩|总成绩)$/.test(v):/^考试(日期|时间)$/.test(v);})){headers=values;started=true;}continue;}
        if(!values.length||values.every(function(v){return !v;}))continue;if(values.some(function(v){return /暂无数据|无记录/.test(v);}))continue;if(values.length<headers.length)continue;var row={};headers.forEach(function(h,k){row[h]=values[k]||'';});items.push(normalize(row));
      }
      if(!started)continue;
      var totalMatch=body.match(/(?:共\s*(\d+)\s*(?:条|项)|总记录数\s*(\d+))/),paginator=document.querySelector('.bh-pager,.pagination,[aria-label*=分页],.pager,.jqx-grid-pager');
      if(totalMatch&&Number(totalMatch[1]||totalMatch[2])!==items.length)throw Error('结果存在未读取分页');if(paginator&&!totalMatch)throw Error('无法确认分页完整性');
      if(!items.length&&!/暂无数据|无记录|没有考试安排|暂无考试/.test(table.innerText))throw Error('没有可确认的查询结果');
      result.records=items;result.complete=true;result.verifiedEmpty=!items.length;result.account=scope;break;
    }
  }catch(error){result.complete=false;result.records=[];result.reason=error.message;}
  if(!result.complete&&!result.reason)result.reason='未识别到完整查询结果；请打开官网对应页面';
  return JSON.stringify(result);
})(__LUT_KIND__)
