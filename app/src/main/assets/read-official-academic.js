(function(kind){
  'use strict';
  if(location.hostname!=='jwxt.lut.edu.cn'||document.querySelector('input[type=password]'))return;
  var grade=kind==='grades'&&/^\/jwapp\/sys\/cjcx\/(?:\*|%2a)default\/index\.do$/i.test(location.pathname),exam=kind==='exams'&&/^\/jwapp\/sys\/studentWdksapApp\/(?:\*|%2a)default\/index\.do$/i.test(location.pathname);
  if(!grade&&!exam)return;
  var states=window.__lutAcademicQuery=window.__lutAcademicQuery||{};
  if(states[kind]&&states[kind].href===location.href)return;
  var state=states[kind]={href:location.href,pending:true,error:'',data:null};
  function post(path,params){if(window.jQuery&&typeof window.jQuery.ajax==='function')return new Promise(function(resolve,reject){window.jQuery.ajax({url:path,type:'POST',data:params,dataType:'json',timeout:6500}).done(resolve).fail(function(xhr,status){reject(Error(status==='timeout'?'官网查询超时，保留缓存':'官网查询失败（HTTP '+xhr.status+'）'));});});var abort=new AbortController(),timer=setTimeout(function(){abort.abort();},6500);return fetch(path,{method:'POST',credentials:'same-origin',signal:abort.signal,headers:{'Content-Type':'application/x-www-form-urlencoded; charset=UTF-8','Accept':'application/json','X-Requested-With':'XMLHttpRequest'},body:new URLSearchParams(params).toString()}).then(function(response){if(!response.ok)throw Error('官网查询失败（HTTP '+response.status+'）');return response.json();}).finally(function(){clearTimeout(timer);});}
  function block(data,action){var b=data&&data.datas&&data.datas[action];if(!data||String(data.code)!=='0'||!b||!b.extParams||String(b.extParams.code)!=='1'||!Array.isArray(b.rows))throw Error('官网没有确认查询成功');return b;}
  function all(path,action,params,wrapped){var rows=[],expected=null,pages=0;
    function next(){pages++;var p=Object.assign({},params,{pageNumber:String(pages),pageSize:'1000'});return post(path,wrapped?{requestParamStr:JSON.stringify(p)}:p).then(function(data){var b=block(data,action),total=b.totalSize==null?null:Number(b.totalSize);
      if(total!==null&&(!Number.isInteger(total)||total<0||total>2000||expected!==null&&total!==expected))throw Error('查询总数变化或超出范围');
      if(total!==null)expected=total;else if(Number(b.pageSize)!==0&&!wrapped)throw Error('无法确认是否返回全部记录');
      rows=rows.concat(b.rows);if(rows.length>2000||expected!==null&&rows.length>expected)throw Error('返回记录数异常');
      if(expected===null||rows.length===expected){b.rows=rows;b.totalSize=rows.length;state.data=data;return;}
      if(!b.rows.length||pages>=30)throw Error('分页读取不完整');return next();
    });}return next();}
  var promise;
  if(grade){var query=[{name:'SFYX',caption:'是否有效',linkOpt:'AND',builderList:'cbl_m_List',builder:'m_value_equal',value:'1',value_display:'是'},{name:'SHOWMAXCJ',caption:'显示最高成绩',linkOpt:'AND',builderList:'cbl_m_List',builder:'m_value_equal',value:'0',value_display:'否'}];promise=all('/jwapp/sys/cjcx/modules/cjcx/xscjcx.do','xscjcx',{querySetting:JSON.stringify(query),'*order':'-XNXQDM,-KCH,-KXH'},false);}
  else{promise=post('/jwapp/sys/studentWdksapApp/modules/wdksap/dqxnxq.do',{}).then(function(data){var terms=block(data,'dqxnxq').rows;if(terms.length!==1||!/^\d{4}-\d{4}-[123]$/.test(String(terms[0].DM||terms[0].XNXQDM||'')))throw Error('无法确认考试学期');return all('/jwapp/sys/studentWdksapApp/WdksapController/cxxsksap.do','cxxsksap',{XNXQDM:String(terms[0].DM||terms[0].XNXQDM),'*order':'-KSRQ,-KSSJMS'},true);});}
  promise.catch(function(error){state.error=error.name==='AbortError'?'官网查询超时，保留缓存':error.message;}).finally(function(){state.pending=false;});
})(__LUT_KIND__)
