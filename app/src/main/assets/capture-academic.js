(function(){
  'use strict';
  if(location.hostname!=='jwxt.lut.edu.cn'||window.__lutCapture)return;
  var state=window.__lutCapture={responses:[]};
  function remember(url,body){try{var u=new URL(url,location.href);if(u.origin!==location.origin||typeof body!=='string'||body.length>1500000)return;var data=JSON.parse(body);if(!data||!data.datas)return;state.responses.push({url:u.pathname,data:data});if(state.responses.length>20)state.responses.shift();}catch(_){} }
  if(window.fetch){var original=window.fetch;window.fetch=function(){var args=arguments;return original.apply(this,args).then(function(response){try{response.clone().text().then(function(body){remember(response.url,body);}).catch(function(){});}catch(_){}return response;});};}
  var open=XMLHttpRequest.prototype.open,send=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open=function(method,url){this.__lutObservedUrl=String(url);return open.apply(this,arguments);};
  XMLHttpRequest.prototype.send=function(){this.addEventListener('load',function(){try{if(this.responseType===''||this.responseType==='text')remember(this.responseURL||this.__lutObservedUrl,this.responseText);}catch(_){} });return send.apply(this,arguments);};
})()
