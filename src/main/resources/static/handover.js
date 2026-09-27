'use strict';
let handoverUserId=0, handoverFocus;
async function editHandover(event){
  handoverFocus=event.currentTarget;
  handoverUserId=Number(handoverFocus.dataset.id);
  handoverFocus.disabled=true;
  try{
    const data=await api('/users/'+handoverUserId+'/handover');
    const record=data.handover, state=record.state||'EMPLOYED';
    let roles='',systems='',history='';
    for(const r of data.roles)roles+='<span class="badge">'+esc(r.name)+'</span> ';
    for(const s of data.systems)systems+='<li>'+esc(s.name)+'</li>';
    for(const h of data.history)history+='<details><summary>'+esc(h.occurred_at)+' · '+esc(h.action)+' · 操作人 ID '+h.operator_id+'</summary><pre>'+esc(h.detail)+'</pre></details>';
    let body='<p>人员状态：<strong>'+employmentLabel(state)+'</strong>　账号：'+(data.user.status==='ACTIVE'?'启用':'停用')+'</p><p>当前角色：'+(roles||'无角色')+'</p><details><summary>当前可用系统（'+data.systems.length+' 个）</summary><ul>'+systems+'</ul></details>';
    if(state==='EMPLOYED'){
      body+='<p class="muted">开始交接保留现有授权，可继续使用“分配角色”按需回收。接收人不会自动获得原员工权限。</p><label>交接接收人工号<input name="receiverNo" required maxlength="100" placeholder="例如 SG000012"></label><label>交接负责人工号<input name="ownerNo" required maxlength="100" placeholder="例如 SG000011"></label><label>预计完成日期<input name="expectedDate" type="date" required></label><label>离职交接说明<textarea name="reason" required maxlength="1000"></textarea></label><label>OA 单号（可选）<input name="oaReference" maxlength="200"></label>';
    }else{
      body+='<div class="handover-summary"><p>接收人：'+esc(record.receiver_name)+'（'+esc(record.receiver_no)+'）</p><p>负责人：'+esc(record.owner_name)+'（'+esc(record.owner_no)+'）</p><p>预计完成：'+esc(record.expected_date)+'</p><p>交接说明：'+esc(record.reason)+'</p><p>OA 单号：'+esc(record.oa_reference||'未填写')+'</p></div>';
      if(state==='HANDOVER')body+='<p class="error">完成处置将回收全部角色、停用账号并撤销所有登录会话。</p><label>完成交接说明<textarea name="note" required maxlength="1000"></textarea></label><label class="choice"><input name="confirmed" type="checkbox" required><span>确认交接已完成，执行集中撤权和停用账号</span></label>';
      else body+='<p>实际完成：'+esc(record.completed_at)+' UTC</p><p>完成说明：'+esc(record.completion_note)+'</p><p>该账号已停用。用户和历史记录保留，不物理删除。</p>';
    }
    document.body.insertAdjacentHTML('beforeend','<dialog id="handover-dialog"><form id="handover-form"><header><h2>'+esc(data.user.name)+' · 离职交接</h2><button type="button" id="handover-close" class="secondary">关闭</button></header>'+body+'<details><summary>最近变更记录</summary>'+history+'</details><p class="error" id="handover-error" role="alert"></p><footer><small>记录时间采用 UTC</small>'+(state==='DEPARTED'?'':'<button class="primary" id="handover-submit">'+(state==='EMPLOYED'?'开始离职交接':'完成离职处置')+'</button>')+'</footer></form></dialog>');
    const dialog=document.getElementById('handover-dialog');dialog.dataset.state=state;dialog.showModal();dialog.addEventListener('cancel',closeHandover);document.getElementById('handover-close').onclick=closeHandover;document.getElementById('handover-form').onsubmit=saveHandover;
  }catch(error){notify(error.message);}finally{handoverFocus.disabled=false;}
}
function employmentLabel(state){if(state==='HANDOVER')return '离职交接中';if(state==='DEPARTED')return '已离职';return '在职';}
function closeHandover(event){if(event)event.preventDefault();const dialog=document.getElementById('handover-dialog');if(dialog){dialog.close();dialog.remove();}if(handoverFocus?.isConnected)handoverFocus.focus();}
async function saveHandover(event){
  event.preventDefault();const form=event.currentTarget,button=document.getElementById('handover-submit');button.disabled=true;
  const values={};for(const input of form.elements){if(input.name)values[input.name]=input.type==='checkbox'?String(input.checked):input.value;}
  const completing=document.getElementById('handover-dialog').dataset.state==='HANDOVER';
  try{await api('/users/'+handoverUserId+'/handover'+(completing?'/complete':''),'POST',values);closeHandover();notify(completing?'离职处置完成：角色已回收，账号与会话已停用。':'已开始离职交接，现有权限保持不变。');await show('users');}
  catch(error){const box=document.getElementById('handover-error');if(box)box.textContent=error.message;}
  finally{button.disabled=false;}
}
