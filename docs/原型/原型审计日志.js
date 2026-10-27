'use strict';
// 离线交互模型：分页固定查询快照，归档保留事件，不调用网络。
let auditSequence=0,auditSnapshot=[],auditVisible=0,auditFilters={user:'',app:'',result:'',event:'',from:'',to:'',storage:'ALL'};
const auditInitialData=initialData;
initialData=function(){auditInitialData();auditSnapshot=[];auditVisible=0;auditSequence=0;auditFilters={user:'',app:'',result:'',event:'',from:'',to:'',storage:'ALL'};};
function prototypeAudit(user,event,allowed,reason){logs.unshift({id:++auditSequence,at:new Date().toISOString(),time:new Date().toLocaleString('zh-CN'),user,app:'',code:'',detail:event,event,allowed,requestId:'demo-audit-'+auditSequence,reason,archived:false});}
function auditMatches(l){const f=auditFilters;return (!f.user||l.user===f.user)&&(!f.app||l.app===f.app)&&(!f.result||String(l.allowed)===f.result)&&(!f.event||l.event===f.event)&&(!f.from||l.at>=new Date(f.from).toISOString())&&(!f.to||l.at<new Date(f.to).toISOString())&&(f.storage==='ALL'||(f.storage==='ARCHIVED')===!!l.archived);}
function auditSearch(){
  if(!canViewLogs())return;
  for(const key of Object.keys(auditFilters))auditFilters[key]=document.getElementById('audit-'+key).value;
  if(auditFilters.from&&auditFilters.to&&auditFilters.from>=auditFilters.to){notify('开始时间必须早于结束时间。');return;}
  auditSnapshot=logs.filter(auditMatches);auditVisible=20;logsView(document.getElementById('view'));
}
function auditArchive(){
  if(!canViewLogs())return;
  const before=document.getElementById('audit-before').value;
  if(!before||new Date(before)>new Date()){notify('请选择不晚于当前时间的归档截止时间。');return;}
  let count=0;for(const l of logs)if(!l.archived&&l.at<new Date(before).toISOString()&&count<1000){l.archived=true;count++;}
  auditSnapshot=logs.filter(auditMatches);auditVisible=20;logsView(document.getElementById('view'));notify('已模拟归档 '+count+' 条；仍可查询，未删除记录。');
}
const previousLocalLog=localLog;
localLog=function(...args){previousLocalLog(...args);Object.assign(logs[0],{id:++auditSequence,at:new Date().toISOString(),event:args[1]?'AUTHORIZATION':'APPLICATION_ENTRY',archived:false});};
logsView=function(view){
  if(!canViewLogs()){view.innerHTML='<p class="error" role="alert">无权查看日志。</p>';return;}
  if(!auditVisible){auditSnapshot=logs.filter(auditMatches);auditVisible=20;}
  const input=(key,label,type='text')=>'<label>'+label+'<input id="audit-'+key+'" type="'+type+'" value="'+esc(auditFilters[key])+'"></label>';
  const select=(key,label,options)=>'<label>'+label+'<select id="audit-'+key+'">'+options.map(([value,text])=>'<option value="'+value+'" '+(value===auditFilters[key]?'selected':'')+'>'+text+'</option>').join('')+'</select></label>';
  const filters=input('user','用户（精确匹配）')+input('app','系统（精确匹配）')+select('result','结果',[['','全部'],['true','成功'],['false','拒绝']])+select('event','事件',[['','全部'],...['AUTHORIZATION','APPLICATION_ENTRY','LOGIN_SUCCESS','LOGIN_FAILED','UNAUTHENTICATED'].map(x=>[x,x])])+input('from','开始（含）','datetime-local')+input('to','结束（不含）','datetime-local')+select('storage','归档状态',[['ALL','全部'],['ACTIVE','未归档'],['ARCHIVED','已归档']]);
  const rows=auditSnapshot.slice(0,auditVisible).map(l=>'<tr><td>'+esc(l.at)+'</td><td>'+esc(l.user||'未识别')+'</td><td>'+esc(l.event)+'<small>'+esc(l.app)+'</small></td><td>'+badge(l.allowed?'成功':'拒绝',l.allowed)+'</td><td>'+esc(l.reason)+'<small>'+esc(l.requestId)+'</small></td><td>'+(l.archived?'已归档':'未归档')+'</td></tr>').join('');
  view.innerHTML='<section class="card panel"><h2>鉴权与认证日志</h2><p class="muted">离线模拟，不连接后台。时间输入为本地时区，记录显示 UTC；查询后新日志在下次查询出现。</p><form id="audit-query" class="audit-grid">'+filters+'<button class="primary">查询</button></form><div class="toolbar">'+button('添加登录失败示例','audit-failure')+button('添加未认证示例','audit-anonymous')+'</div><details><summary>归档历史日志</summary><form id="audit-archive"><label>截止时间（不含）<input id="audit-before" type="datetime-local" required></label><p>确认归档此时间前的全部日志，不受上方筛选影响。每批最多 1,000 条，数据保留。</p><button class="secondary">确认归档本批</button></form></details></section><section class="card table-scroll"><table><thead><tr><th>UTC 时间</th><th>用户</th><th>事件／系统</th><th>结果</th><th>原因／请求号</th><th>归档</th></tr></thead><tbody>'+(rows||'<tr><td colspan="6" class="empty">暂无符合条件的记录，请查询或添加示例。</td></tr>')+'</tbody></table><div class="toolbar">已显示 '+Math.min(auditVisible,auditSnapshot.length)+' 条'+(auditVisible<auditSnapshot.length?button('加载更多','audit-more'):'')+'</div></section>';
};
document.addEventListener('submit',function(e){if(e.target.id==='audit-query'||e.target.id==='audit-archive'){e.preventDefault();e.stopImmediatePropagation();if(e.target.id==='audit-query')auditSearch();else auditArchive();}},true);
document.addEventListener('click',function(e){const b=e.target.closest('[data-action]');if(!b)return;const a=b.dataset.action;if(!a?.startsWith('audit-'))return;e.preventDefault();e.stopImmediatePropagation();if(!canViewLogs())return;if(a==='audit-more'){auditVisible+=20;logsView(document.getElementById('view'));}else{prototypeAudit(a==='audit-failure'?'示例账号':'',a==='audit-failure'?'LOGIN_FAILED':'UNAUTHENTICATED',false,a==='audit-failure'?'LOGIN_FAILED':'AUTHENTICATION_REQUIRED');notify('示例已记录，点击查询查看新日志。');}},true);
const auditStyle=document.createElement('style');auditStyle.textContent='.audit-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:14px;align-items:end;margin:20px 0}.audit-grid label{display:grid;gap:8px}.audit-grid input,.audit-grid select{width:100%}details form{padding:16px 0}';document.head.appendChild(auditStyle);
