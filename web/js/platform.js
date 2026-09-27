/* Controller for the server-backed platform. Offline preview is explicitly read-only. */
(() => {
    'use strict';
    const C=window.PlatformCore, $=id=>document.getElementById(id);
    const escape=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const options=(obj,selected)=>Object.entries(obj).map(([v,label])=>`<option value="${escape(v)}" ${v===String(selected)?'selected':''}>${escape(label)}</option>`).join('');
    const money=n=>Number(n||0).toLocaleString('th-TH',{style:'currency',currency:'THB'});
    const time=t=>t?new Date(t).toLocaleString('th-TH'):'—';
    const roles={super_admin:'เจ้าของแพลตฟอร์ม',owner:'เจ้าของบริษัท',admin:'ผู้ดูแล',staff:'พนักงาน'};
    let allState,tenantScope=new URLSearchParams(location.search).get('tenant')||'',state,siteId='',tab='sites',preview=false,busy=false,draft=[],dirty=false,tool='SELECT',floor=1,selected='',undo=[],redo=[],routeIds=[],noticeTimer,modalSubmit;
    let analyticsDays='30',supportFilter='ALL',billingFilter='ALL';
    let filters={from:'',to:'',plate:''};
    // Platform operators explicitly enter one customer's workspace. Customers are
    // already tenant-filtered by the API; this filter only scopes the operator UI.
    function acceptState(next) {
        allState=next;
        if(next.user.role!=='super_admin') { tenantScope=next.user.tenantId; state=next; return; }
        if(!next.tenants.some(t=>t.id===tenantScope)) tenantScope='';
        state={...next,tenants:next.tenants.filter(t=>t.id===tenantScope),
            sites:next.sites.filter(s=>s.tenantId===tenantScope),
            users:next.users.filter(u=>u.tenantId===tenantScope),
            audit:next.audit.filter(a=>a.tenantId===tenantScope)};
    }
    const platformOwner=()=>state?.user.role==='super_admin';
    const current=()=>state.sites.find(s=>s.id===siteId);
    const owner=()=>['owner','super_admin'].includes(state.user.role);
    const manager=()=>state.user.role!=='staff';
    const writeDisabled=()=>preview||busy;
    function notice(message,error=false) { $('notice').textContent=message; $('notice').classList.toggle('error',error); $('notice').style.display='block'; clearTimeout(noticeTimer); noticeTimer=setTimeout(()=>$('notice').style.display='none',7000); }
    async function api(path,data) {
        const response=await fetch(`api/platform/${path}`,{method:data?'POST':'GET',credentials:'same-origin',headers:data?{'Content-Type':'application/json'}:{},body:data?JSON.stringify(data):undefined});
        let result; try { result=await response.json(); } catch { throw new Error('หน้านี้ต้องใช้ Java Backend: รัน run-platform-demo.bat หรือดูตัวอย่างอ่านอย่างเดียว'); }
        if(!response.ok) { if(response.status===401) { $('workspace').hidden=true; $('loginScreen').hidden=false; } throw new Error(result.error||'เชื่อมต่อไม่สำเร็จ'); }
        return result;
    }
    async function command(action,data={}) {
        if(writeDisabled()) throw new Error(preview?'ตัวอย่างนี้อ่านอย่างเดียว กรุณารัน Java Backend เพื่อบันทึก':'กำลังบันทึก กรุณารอ');
        busy=true;
        try { acceptState(await api('command',{action,revision:state.revision,siteId,...data})); return state; }
        finally { busy=false; }
    }
    function checkDirty() { return !dirty||confirm('มีผังที่ยังไม่บันทึก ต้องการออกและทิ้งการแก้ไขหรือไม่?'); }
    function loadDraft() { draft=C.clone(current()?.draft||[]); dirty=false; selected=''; undo=[]; redo=[]; routeIds=[]; }
    function showWorkspace() {
        $('loginScreen').hidden=true; $('workspace').hidden=false;
        if(platformOwner()&&!tenantScope&&!['analytics','customers','support','billing'].includes(tab)) tab='analytics';
        if(!platformOwner()&&['analytics','customers'].includes(tab)) tab='sites';
        if(!owner()&&tab==='billing') tab='sites';
        if(!state.sites.some(s=>s.id===siteId)) siteId=state.sites[0]?.id||'';
        $('accountName').textContent=`${state.user.username} · ${roles[state.user.role]}`;
        $('modeBanner').textContent=preview?'ตัวอย่างอ่านอย่างเดียว • ไม่มีการบันทึกหรือควบคุมอุปกรณ์จริง':current()?.sample?'ลานตัวอย่าง · ทะเบียน ประวัติ และอุปกรณ์เป็นข้อมูลสมมุติ · แยกบริษัทจากข้อมูลจริง':'ข้อมูลจริงบนเซิร์ฟเวอร์ • อุปกรณ์รองรับ LED bench เมื่อเปิด Gateway';
        $('workspaceLabel').textContent=platformOwner()?'เจ้าของแพลตฟอร์ม':'พื้นที่ลูกค้า';
        $('tenantContext').textContent=state.tenants[0]?.name||(platformOwner()?'ศูนย์จัดการลูกค้า':'ยังไม่มีบริษัท');
        $('backToCustomers').hidden=!platformOwner()||!tenantScope;
        if(platformOwner()) $('modeBanner').textContent=tenantScope?`กำลังดูแลลูกค้า: ${state.tenants[0].name} · ดำเนินการด้วยบัญชีเจ้าของแพลตฟอร์ม`:'ศูนย์เจ้าของแพลตฟอร์ม · เลือกลูกค้าเพื่อเข้าพื้นที่ทำงาน';
        document.querySelectorAll('a[href^="dashboard.html"]').forEach(a=>a.href=platformOwner()&&tenantScope?`dashboard.html?tenant=${encodeURIComponent(tenantScope)}`:'dashboard.html');
        $('sitePicker').hidden=platformOwner()&&!tenantScope;
        $('sitePicker').innerHTML=state.sites.length?options(Object.fromEntries(state.sites.map(s=>[s.id,s.name])),siteId):'<option>ยังไม่มีลานจอด</option>';
        const pages={...(owner()?{billing:'฿  ค่าเช่าแพลตฟอร์ม'}:{}),support:'✉  แจ้งปัญหา / ติดต่อผู้ดูแล',...(platformOwner()?{analytics:'▥  Dashboard ลูกค้า',customers:'♙  ลูกค้าแพลตฟอร์ม'}:{}),sites:'▦  ลานจอดทั้งหมด',editor:'▧  ออกแบบผัง',operations:'↔  รถเข้า–ออก',history:'◷  ประวัติ / Excel',members:'◎  สมาชิก / การจอง',devices:'⌁  อุปกรณ์',settings:'⚙  ตั้งค่าลาน',users:'♙  ทีมงานของบริษัท',audit:'≡  บันทึกกิจกรรม'};
        $('nav').innerHTML=Object.entries(pages).filter(([id])=>platformOwner()&&!tenantScope?['analytics','customers','support','billing'].includes(id):owner()||!['editor','settings','users','audit'].includes(id)&& (manager()||!['history','members','devices'].includes(id))).map(([id,label])=>`<button data-nav="${id}" class="${id===tab?'active':''}">${label}</button>`).join('');
        render();
    }
    function heading(title,subtitle,actions='') { return `<div class="page-title"><div><span class="eyebrow">GREENPARK / ${escape(tab.toUpperCase())}</span><h1>${title}</h1><p>${subtitle}</p></div><div class="actions">${actions}</div></div>`; }
    function button(action,label,primary=false,disabled=false,extra='') { return `<button type="button" data-action="${action}" class="${primary?'primary':''}" ${disabled?'disabled':''} ${extra}>${label}</button>`; }
    function empty(text) { return `<div class="empty">${text}</div>`; }
    function metric(label,value) { return `<div class="metric"><span>${label}</span><strong>${value}</strong></div>`; }
    function table(headers,rows) { return `<div class="table-wrap"><table><thead><tr>${headers.map(h=>`<th>${h}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>`<tr>${row.map(c=>`<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`; }
    const billingPlans={STARTER:'Starter · 499 บาท/เดือน',BUSINESS:'Business · 990 บาท/เดือน',MULTI_SITE:'Multi-site · 1,990 บาท/เดือน'};
    const billStatuses={ISSUED:'รอชำระ',REPORTED:'แจ้งโอน / รอตรวจสอบ',PAID:'รับเงินแล้ว',VOID:'ยกเลิก'};
    function billingDashboard() {
        const b=allState.platformBilling;
        if(!b)return heading('ค่าเช่าแพลตฟอร์ม','เฉพาะเจ้าของบริษัทและเจ้าของแพลตฟอร์ม')+empty('กรุณารีเฟรชเพื่อโหลดข้อมูลบิล');
        const invoices=b.invoices.filter(i=>!platformOwner()||!tenantScope||i.tenantId===tenantScope), outstanding=invoices.filter(i=>['ISSUED','REPORTED'].includes(i.status)),paid=invoices.filter(i=>i.status==='PAID');
        const overdue=i=>['ISSUED','REPORTED'].includes(i.status)&&i.dueDate<b.today;
        const sum=rows=>money(rows.reduce((n,i)=>n+i.amount,0));
        let html=heading('ค่าเช่าแพลตฟอร์ม','บิลรายเดือนและประวัติรับเงินค่าใช้บริการ GreenPark',platformOwner()?button('billingSettings','ตั้งค่ารับโอน',false,writeDisabled())+button('issueInvoice','＋ ออกบิลรายเดือน',true,writeDisabled()):'');
        html+=`<div class="metrics">${metric(platformOwner()?'รับเงินแล้วสะสม':'ชำระแล้วสะสม',sum(paid))}${metric('ยอดรอชำระ / รอตรวจสอบ',sum(outstanding))}${metric('เกินกำหนดชำระ',sum(invoices.filter(overdue)))}${metric('บิลแจ้งโอนรอตรวจ',invoices.filter(i=>i.status==='REPORTED').length)}</div>`;
        html+=`<section class="card"><h2>ช่องทางชำระเงิน</h2><p style="white-space:pre-wrap;overflow-wrap:anywhere">${escape(b.paymentInstructions||'ยังไม่ได้ตั้งค่าบัญชีรับโอน กรุณาติดต่อเจ้าของแพลตฟอร์ม')}</p><p>โอนตามยอดในบิล แล้วแจ้งเลขอ้างอิง ผู้ดูแลตรวจยอดเงินเข้าก่อนยืนยันรับเงิน</p><label>สถานะบิล<select id="billingFilter">${options({ALL:'ทั้งหมด',OVERDUE:'เกินกำหนด',...billStatuses},billingFilter)}</select></label></section>`;
        const shown=invoices.filter(i=>billingFilter==='ALL'||(billingFilter==='OVERDUE'?overdue(i):i.status===billingFilter)).slice().sort((a,b)=>b.createdAt.localeCompare(a.createdAt));
        html+=shown.length?shown.map(i=>`<article class="card section-gap"><div class="card-top"><span class="tag">${billStatuses[i.status]}${overdue(i)?' · เกินกำหนด':''}</span><strong>${escape(i.number)}</strong></div><h2>${money(i.amount)}</h2><p>${escape(i.tenantName)} · ${escape(billingPlans[i.plan])}</p><p>รอบบริการ ${i.periodStart} ถึง ${new Date(Date.parse(i.periodEnd+'T00:00:00Z')-86400000).toISOString().slice(0,10)} · ครบกำหนด ${i.dueDate}</p><details><summary>รายละเอียดบิล / ประวัติรับเงิน</summary><p style="white-space:pre-wrap;overflow-wrap:anywhere">ข้อมูลรับโอน ณ วันออกบิล: ${escape(i.paymentInstructions)}</p><p>แจ้งโอน: ${escape(i.transferReference||'—')} · ${time(i.reportedAt)}</p><p>รับเงิน: ${escape(i.paymentReference||'—')} · ${time(i.paidAt)}</p>${i.voidReason?`<p>เหตุผลยกเลิก: ${escape(i.voidReason)}</p>`:''}<p>เอกสารเรียกเก็บภายในระบบ ไม่ใช่ใบกำกับภาษี</p></details><div class="actions">${['ISSUED','REPORTED'].includes(i.status)?platformOwner()?button('confirmInvoice','ตรวจแล้ว / ยืนยันรับเงิน',true,writeDisabled(),`data-id="${escape(i.id)}"`)+button('voidInvoice','ยกเลิกบิล',false,writeDisabled(),`data-id="${escape(i.id)}"`):button('reportTransfer',i.status==='REPORTED'?'แก้ไขข้อมูลแจ้งโอน':'แจ้งโอนเงิน',true,writeDisabled(),`data-id="${escape(i.id)}"`):''}</div></article>`).join(''):empty('ยังไม่มีบิลในสถานะนี้');
        html+=`<section class="card section-gap"><h3>ขอบเขตระบบเรียกเก็บรุ่นแรก</h3><p>ออกบิลครั้งละ 1 เดือนโดยผู้ดูแล ไม่ตัดเงินหรือออกบิลซ้ำอัตโนมัติ ไม่ระงับลานจอดอัตโนมัติเมื่อค้างชำระ และยังไม่บังคับโควตาลานหรือบัญชีตามแพ็กเกจ ยอดนี้แยกจากค่าจอดรถของบริษัทลูกค้า</p></section>`;
        return html;
    }
    const supportStatuses={OPEN:'รอรับเรื่อง',IN_PROGRESS:'กำลังดำเนินการ',RESOLVED:'แก้ไขแล้ว'};
    function supportDashboard() {
        const issues=allState.supportTickets||[],visible=issues.filter(t=>(!platformOwner()||!tenantScope||t.tenantId===tenantScope)&&(supportFilter==='ALL'||t.status===supportFilter)).slice().sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt));
        let html=heading(platformOwner()?'ศูนย์รับแจ้งปัญหาลูกค้า':'แจ้งปัญหา / ติดต่อเจ้าของแพลตฟอร์ม','ข้อความภายในระบบ · กดรีเฟรชเพื่อดูคำตอบล่าสุด · อย่าส่งรหัสผ่านหรือคีย์ลับ',platformOwner()?'':button('createSupport','＋ แจ้งปัญหา',true,writeDisabled()));
        html+=`<div class="card"><label>สถานะ<select id="supportFilter">${options({ALL:'ทั้งหมด',...supportStatuses},supportFilter)}</select></label></div>`;
        html+=visible.length?visible.map(t=>`<article class="card section-gap"><div class="card-top"><span class="tag">${supportStatuses[t.status]}</span><small>${time(t.updatedAt)}</small></div><h2>${escape(t.subject)}</h2><p>${escape(allState.tenants.find(c=>c.id===t.tenantId)?.name||'บริษัทของคุณ')} · เลขอ้างอิง ${escape(t.id.slice(0,8))}</p><details><summary>อ่านรายละเอียดและการตอบกลับ (${t.messages.length})</summary>${t.messages.map(m=>`<div class="support-message"><strong>${m.operator?'เจ้าของแพลตฟอร์ม':escape(m.author)}</strong><small> · ${time(m.at)}</small><p style="white-space:pre-wrap;overflow-wrap:anywhere">${escape(m.text)}</p></div>`).join('')}</details><div class="actions">${button('replySupport','ตอบกลับ',false,writeDisabled(),`data-id="${escape(t.id)}"`)}${platformOwner()?button('supportStatus','เปลี่ยนสถานะ',false,writeDisabled(),`data-id="${escape(t.id)}"`):''}</div></article>`).join(''):empty('ยังไม่มีรายการแจ้งปัญหาในสถานะนี้');
        return html;
    }
    function analyticsDashboard() {
        const report=allState.platformAnalytics, data=report?.windows?.[analyticsDays];
        let html=heading('Dashboard ลูกค้าแพลตฟอร์ม','ภาพรวมการเติบโตและการใช้งานจริงของบริษัทลูกค้า',`<label>ช่วงรายงาน<select id="analyticsDays">${options({'7':'7 วัน','30':'30 วัน','90':'90 วัน'},analyticsDays)}</select></label>`);
        if(!data) return html+empty('ยังไม่มีรายงานจากเซิร์ฟเวอร์ · กรุณารีเฟรชหลังอัปเดตระบบ');
        const customers=data.customers, logins=customers.reduce((n,c)=>n+c.logins,0), actions=customers.reduce((n,c)=>n+c.actions,0);
        html+=`<div class="metrics">${metric('ลูกค้าทั้งหมด',customers.length)}${metric('ลูกค้าใหม่ในช่วงนี้',data.newCustomers)}${metric('ลูกค้าที่ใช้งาน',data.activeCustomers)}${metric('ลูกค้าเดิมที่ใช้งาน',data.returningCustomers)}${metric('ไม่พบการใช้งานในช่วงนี้',data.inactiveCustomers)}${metric('เข้าสู่ระบบสำเร็จ',logins)}${metric('รายการที่บันทึกสำเร็จ',actions)}</div>`;
        html+=`<section class="card"><h2>แนวโน้มการใช้งานรายวัน</h2><p>จำนวนบริษัทที่เข้าสู่ระบบหรือบันทึกรายการ · เวลาไทย</p><div class="usage-chart" role="img" aria-label="แนวโน้มลูกค้าที่ใช้งานรายวัน รายละเอียดอยู่ในตารางด้านล่าง">${data.trend.map(d=>`<div class="usage-column" title="${d.day}: ${d.active} บริษัท"><span style="height:${Math.max(2,110*d.active/Math.max(1,...data.trend.map(x=>x.active)))}px"></span><small>${d.day.slice(8)}</small></div>`).join('')}</div><details><summary>ดูตัวเลขรายวัน</summary>${table(['วันที่','ลูกค้าใหม่','บริษัทที่ใช้งาน','เข้าสู่ระบบ','บันทึกรายการ'],data.trend.map(d=>[d.day,d.newCustomers,d.active,d.logins,d.actions]))}</details></section>`;
        html+=`<section class="card section-gap"><h2>รายงานแยกตามลูกค้า</h2><p>เรียงตามจำนวนการใช้งานมากที่สุด</p>${customers.length?table(['บริษัท','กลุ่มลูกค้า','เริ่มเป็นลูกค้า','ลานจริง','เข้าสู่ระบบ','บันทึกรายการ','วันที่ใช้งาน','ใช้งานล่าสุด',''],customers.map(c=>[escape(c.name),({new:'ใหม่',existing:'เดิม',unknown:'ไม่ทราบวันเริ่ม'})[c.cohort],c.createdAt?time(c.createdAt):'ไม่ทราบ',c.sites,c.logins,c.actions,c.activeDays,time(c.lastActiveAt),button('enterTenant','ดูแลลูกค้า →',false,false,`data-id="${escape(c.id)}"`)])):empty('ยังไม่มีลูกค้าจริง · เพิ่มลูกค้าได้ที่เมนูลูกค้าแพลตฟอร์ม')}</section>`;
        html+=`<section class="card section-gap"><h3>วิธีอ่านรายงาน</h3><p>ลูกค้าใหม่ = บริษัทที่สร้างในช่วงที่เลือก · ลูกค้าเดิม = บริษัทที่สร้างก่อนช่วงนั้น · การใช้งาน = เข้าสู่ระบบสำเร็จหรือบันทึกคำสั่งสำเร็จ ไม่ใช่เวลาที่เปิดหน้าเว็บ</p><p>เริ่มเก็บสถิติ ${time(report.startedAt)} เก็บยอดรายวัน 90 วัน ไม่นับการทำงานของเจ้าของแพลตฟอร์ม บริษัททดลอง และรายการในลานตัวอย่าง ข้อมูลก่อนเริ่มเก็บไม่ได้เติมย้อนหลัง</p><p>ลูกค้าไม่ทราบวันเริ่ม ${data.unknownCreated} บริษัท แสดงแยกจากลูกค้าใหม่/เดิม · “ไม่พบการใช้งาน” ไม่ได้หมายถึงยกเลิกบริการ · อัปเดต ${time(report.generatedAt)}</p></section>`;
        return html;
    }
    function render() {
        let content=''; const s=current();
        if(tab!=='billing'&&tab!=='support'&&tab!=='analytics'&&tab!=='customers'&&tab!=='sites'&&tab!=='users'&&tab!=='audit'&&!s) {
            $('main').innerHTML=heading('ยังไม่มีลานจอด','สร้างลานก่อนใช้เครื่องมือออกแบบและรับรถ')+`<div class="card onboarding"><h2>เริ่มออกแบบลานจอด</h2><p>ลานตัวอย่างมีถนน 2 ชั้น ช่องจอด กล้องและไม้กั้นจำลอง พร้อมประวัติรถ 3 เดือน ข้อมูลทั้งหมดระบุว่าเป็นตัวอย่างและอยู่ในบริษัททดลองแยกต่างหาก</p><div class="actions">${state.user.role==='super_admin'&&!allState.tenants.some(t=>t.sample)?button('sampleWorkspace','＋ สร้างพื้นที่ทดลอง',true,writeDisabled()):''}${state.user.role==='super_admin'?button('tenant','สร้างบริษัทจริง',false,writeDisabled()):''}${owner()&&state.tenants.length?button('createSite','สร้างลานจอดจริง',false,writeDisabled()):''}</div></div>`;
            return;
        }
        if(tab==='billing'&&owner()) {
            content=billingDashboard();
        } else if(tab==='support') {
            content=supportDashboard();
        } else if(tab==='analytics'&&platformOwner()) {
            content=analyticsDashboard();
        } else if(tab==='customers'&&platformOwner()) {
            content=heading('ศูนย์เจ้าของแพลตฟอร์ม','สร้างบัญชีลูกค้าและเลือกบริษัทที่ต้องการดูแล',button('tenant','＋ เพิ่มลูกค้า / เจ้าของบริษัท',true,writeDisabled()));
            content+=`<div class="metrics">${metric('บริษัทลูกค้า',allState.tenants.filter(t=>!t.sample).length)}${metric('ลานจอด',allState.sites.length)}${metric('บัญชีลูกค้า',allState.users.filter(u=>u.role!=='super_admin').length)}</div>`;
            content+=`<div class="card"><h2>บัญชีลูกค้าแยกบริษัท</h2><p>ลูกค้าเข้าสู่ระบบด้วยบัญชีของตนเอง เห็นเฉพาะลาน ประวัติ รายได้ และทีมงานในบริษัทของตน เจ้าของแพลตฟอร์มเลือกดูแลลูกค้าได้ทีละบริษัท</p><a href="platform.html">ลิงก์เข้าสู่ระบบสำหรับลูกค้า</a></div>`;
            content+=table(['บริษัท','บัญชีเจ้าของ','ลาน','ประเภท','สถานะ','จัดการ'],allState.tenants.map(t=>[escape(t.name),allState.users.filter(u=>u.tenantId===t.id&&u.role==='owner').map(u=>escape(u.username)+(u.active===false?' (ปิดใช้งาน)':'')).join(', ')||'ยังไม่มีบัญชีเจ้าของ',allState.sites.filter(s=>s.tenantId===t.id).length,t.sample?'ข้อมูลตัวอย่าง':'ลูกค้า',t.active===false?'ระงับการใช้งาน':'ใช้งานปกติ',button('enterTenant','เข้าพื้นที่ลูกค้า →',true,false,`data-id="${escape(t.id)}"`)+button('toggleTenant',t.active===false?'เปิดใช้งานกลับ':'ระงับการใช้งาน',false,writeDisabled(),`data-id="${escape(t.id)}"`)+button('deleteTenant','ลบบริษัท',false,writeDisabled()||t.active!==false,`data-id="${escape(t.id)}"`)]));
            if(!allState.tenants.some(t=>t.sample)) content+=button('sampleWorkspace','＋ สร้างพื้นที่ทดลอง',false,writeDisabled());
        } else if(tab==='sites') {
            const slots=state.sites.flatMap(s=>s.published).filter(c=>c.type==='SLOT').length, active=state.sites.flatMap(s=>s.tickets).filter(t=>t.status==='ACTIVE').length;
            content=heading('ลานจอดของบริษัท',escape(state.tenants[0]?.name||'พื้นที่ลูกค้า')+' · ข้อมูลเฉพาะบริษัทนี้',owner()&&state.tenants.length?button('createSite','＋ สร้างลานจอด',true,writeDisabled())+button('presentation','▶ ลานพรีเซนต์',false,writeDisabled()):'');
            content+=`<div class="metrics">${metric('ลานจอดทั้งหมด',state.sites.length)}${metric('ช่องที่เผยแพร่',slots)}${metric('รถในลาน',active)}${metric('บริษัทที่เข้าถึงได้',state.tenants.length)}</div>`;
            content+=`<div class="cards">${state.sites.map(s=>`<article class="card"><div class="card-top"><span class="site-icon">▦</span><span class="tag">${s.sample?'ข้อมูลสมมุติ · ':''}${escape(C.templates[s.businessType]?.[0]||s.businessType)}</span></div><h2>${escape(s.name)}</h2><p>${escape(C.templates[s.businessType]?.[1]||'')}</p><div class="site-stats"><span>${s.published.filter(c=>c.type==='SLOT').length} ช่องใช้งาน</span><span>${s.versions.length?'เผยแพร่แล้ว':'แบบร่าง'}</span></div><p>ราคา ${money(s.rate)}/ชม. · ฟรี ${s.freeMinutes} นาทีแรก</p><div class="actions">${button('openSite','เปิดลาน →',true,false,`data-id="${escape(s.id)}"`)}${owner()?button('editSite','ออกแบบผัง',false,false,`data-id="${escape(s.id)}"`):''}</div></article>`).join('')}</div>`;
            if(!state.sites.length) content+=`<div class="card onboarding"><h2>เริ่มสร้างลานจอดของบริษัท</h2><p>สร้างลานคอนโด ห้าง และโรงแรมที่มีผังพร้อมทดลอง พร้อมข้อมูลประวัติสมมุติ 3 เดือนในบริษัททดลองแยกต่างหาก</p><div class="actions">${state.user.role==='super_admin'&&!allState.tenants.some(t=>t.sample)?button('sampleWorkspace','＋ สร้างพื้นที่ทดลอง',true,writeDisabled()):''}${state.user.role==='super_admin'?button('tenant','สร้างบริษัทจริง',false,writeDisabled()):''}</div></div>`;
        } else if(tab==='editor') {
            content=heading('ออกแบบพื้นที่ของคุณ',`${escape(s.name)} · Grid 24 × 16 ต่อชั้น · ช่องละหนึ่งหน่วยเชิงตรรกะ`,button('sampleLayout','ใส่ผังตัวอย่าง',false,writeDisabled())+button('validate','ตรวจผัง')+button('saveLayout','บันทึกแบบร่าง',false,writeDisabled())+button('publish','เผยแพร่ผัง',true,writeDisabled()));
            content+=`<div class="editor"><div class="tools">${Object.entries(C.types).map(([type,label])=>`<button data-tool="${type}" draggable="${!['SELECT','ERASE'].includes(type)}" class="${tool===type?'selected':''}">${label}</button>`).join('')}<p class="help">เลือกเครื่องมือแล้วแตะตาราง หรือลากลงพื้นที่<br>เลือก/ย้าย: ลากชิ้นส่วนเดิม<br>ถนนต่อมุมกันได้ด้วยช่องติดกัน</p></div><div class="canvas-shell"><div class="canvas-toolbar"><select id="floorPicker" aria-label="ชั้น">${options(Object.fromEntries(Array.from({length:8},(_,i)=>[i+1,`ชั้น ${i+1}`])),floor)}</select>${button('undo','↶ ย้อน',false,!undo.length)}${button('redo','↷ ทำซ้ำ',false,!redo.length)}<span class="help">${dirty?'● ยังไม่บันทึก':'บันทึกแล้ว'} · ${draft.filter(c=>c.type==='SLOT').length} ช่อง</span></div><div class="canvas-scroll">${grid(draft)}</div><div class="legend"><span><b>สีเขียว</b> ช่องจอด</span><span>สีเทา ถนน</span><span>ทางเดียว → ↓ ← ↑</span></div>${inspector()}</div></div>`;
            content+=`<div class="card section-gap"><h3>เวอร์ชันที่เผยแพร่ (ล่าสุดไม่เกิน 20)</h3><p>การเรียกคืนจะสร้างแบบร่าง ต้องตรวจและเผยแพร่อีกครั้ง ผังนี้ไม่รับรองความกว้างถนน รัศมีเลี้ยว หรือมาตรฐานก่อสร้าง</p>${s.versions.slice().reverse().map((v,i)=>`<div class="version-row"><span>${time(v.at)} · ${v.cells.length} องค์ประกอบ</span>${button('restore','เรียกเป็นแบบร่าง',false,writeDisabled(),`data-id="${escape(v.id)}"`)}</div>`).join('')||'<p>ยังไม่เคยเผยแพร่</p>'}</div>`;
        } else if(tab==='operations') {
            const active=s.tickets.filter(t=>t.status==='ACTIVE');
            content=heading('รถเข้า–ออก',`${escape(s.name)} · ใช้ผังที่เผยแพร่เท่านั้น`,button('checkin','＋ รับรถเข้าลาน',true,writeDisabled()||!s.active||!s.published.length));
            content+=`<div class="metrics">${metric('ช่องจอด',s.published.filter(c=>c.type==='SLOT').length)}${metric('รถในลาน',active.length)}${metric('ค่าจอด / ชั่วโมง',money(s.rate))}${metric('ฟรีช่วงแรก',s.freeMinutes+' นาที')}</div>`;
            content+=`<div class="canvas-shell"><div class="canvas-toolbar"><select id="floorPicker" aria-label="ชั้น">${options(Object.fromEntries(Array.from({length:8},(_,i)=>[i+1,`ชั้น ${i+1}`])),floor)}</select><span class="help">แตะช่องจอดเพื่อแสดงเส้นทางแนะนำ</span></div><div class="canvas-scroll">${grid(s.published,active)}</div></div>`;
            content+=`<h3 class="section-gap">รถที่กำลังจอด</h3>`+table(['ทะเบียน','ช่อง','เวลาเข้า','ค่าจอด ณ ตอนนี้',''],active.map(t=>[escape(t.licensePlate),escape(t.slotNumber),time(t.entryTime),money(fee(t)),button('checkout',t.paidAt?'ชำระแล้ว / รถออก':'รับเงินสด / รถออก',false,writeDisabled(),`data-id="${escape(t.ticketId)}"`)]));
        } else if(tab==='history') {
            const rows=historyRows();
            content=heading('ประวัติย้อนหลัง / Excel','แยกตามลาน · เวลาบนหน้าจอเป็นเวลาท้องถิ่น · Excel ใช้ ISO UTC');
            content+=`<div class="filter-row"><label>ตั้งแต่<input id="filterFrom" type="date" value="${filters.from}"></label><label>ถึง<input id="filterTo" type="date" value="${filters.to}"></label><label>ทะเบียน<input id="filterPlate" maxlength="160" value="${escape(filters.plate)}"></label>${button('filter','ค้นหา',true)}${button('export','ส่งออก .xlsx',false,!rows.length)}</div><p class="help">${rows.length} รายการ · รถออก ${rows.filter(t=>t.status==='EXITED').length} · ยอดรวม ${money(rows.reduce((n,t)=>n+Number(t.fee||0),0))} · รายการ SAMPLE เป็นข้อมูลสมมุติ ไม่ใช่รายได้จริง</p>`;
            content+=table(['ทะเบียน','ช่อง','เวลาเข้า','เวลาออก','สถานะ','ค่าจอด'],rows.map(t=>[escape(t.licensePlate)+(t.sample?' <span class="tag">SAMPLE</span>':''),escape(t.slotNumber),time(t.entryTime),time(t.exitTime),t.status==='ACTIVE'?'ยังอยู่':'ออกแล้ว',money(t.fee)]));
        } else if(tab==='members') {
            content=heading('สมาชิกและการจอง',`${escape(s.name)} · แม่แบบ ${escape(C.templates[s.businessType][0])}`,button('member','＋ สมาชิก',false,writeDisabled()||!s.features.membership)+button('reserve','＋ จองช่อง',true,writeDisabled()||!s.features.reservation));
            content+=`<p class="help">ส่วนลดสมาชิก ${s.policy?.memberDiscountPercent||0}% · โควตาต่อห้อง ${s.policy?.roomQuota||'ไม่จำกัด'} คัน · ตรวจสิทธิ์และเก็บราคา ณ เวลาเข้าลาน</p><h3>สมาชิก / ผู้เข้าพัก</h3>`+table(['ชื่อ','ทะเบียน','ห้อง / หน่วยงาน','เริ่ม','หมดอายุ'],s.memberships.map(m=>[escape(m.name),escape(m.plate),escape(m.room),escape(m.starts||'—'),escape(m.expires)]));
            content+=button('visitor','＋ ขออนุมัติผู้มาติดต่อ',false,writeDisabled())+table(['ทะเบียน','ห้อง','หมดอายุ','สถานะ',''],(s.visitors||[]).map(v=>[escape(v.plate),escape(v.room),time(v.expires),escape(v.status),v.status==='PENDING'?button('approveVisitor','อนุมัติ',false,writeDisabled(),`data-id="${escape(v.id)}"`):'']));
            content+=`<h3 class="section-gap">การจอง</h3>`+table(['ทะเบียน','ช่อง','เริ่ม','สิ้นสุด','สถานะ',''],s.reservations.map(r=>[escape(r.plate),escape(s.published.find(c=>c.id===r.slotId)?.label||r.slotId),time(r.from),time(r.to),escape(r.status),r.status==='BOOKED'?button('cancelReservation','ยกเลิก',false,writeDisabled(),`data-id="${escape(r.id)}"`):'']));
        } else if(tab==='devices') {
            content=heading('อุปกรณ์ / Bench test','Heartbeat จริงเมื่อเปิด Gateway • ทดสอบ LED เท่านั้น ไม่สั่งไม้กั้น',button('device','＋ ลงทะเบียนอุปกรณ์',true,writeDisabled()));
            content+=table(['ชื่อ','ชนิด','สถานะ','ล่าสุด','จัดการ'],s.devices.map(d=>[escape(d.name),escape(d.type),escape(d.status),time(d.lastSeen),owner()?button('provision','สร้าง/หมุนคีย์',false,writeDisabled(),`data-id="${escape(d.id)}"`)+button('disableDevice','ปิดการเชื่อมต่อ',false,writeDisabled(),`data-id="${escape(d.id)}"`)+(d.type==='ESP32'?button('queueLed','ทดสอบ LED',false,writeDisabled(),`data-id="${escape(d.id)}"`):''):'']));
            content+=`<div class="card section-gap"><h3>ก่อนเชื่อมอุปกรณ์จริง</h3><p>Gateway รับ heartbeat และทะเบียนจากอุปกรณ์ที่มีคีย์ รองรับคำสั่ง LED bench เท่านั้น ต้องเปิด HTTPS และกำหนดค่า Gateway ก่อนใช้งาน ยังไม่อ่านภาพ ANPR หรือสั่งไม้กั้นจริง</p></div>`;
        } else if(tab==='settings') {
            content=heading('ตั้งค่าลาน',escape(s.name),button('pricing','ราคา / สมาชิก / ผู้มาติดต่อ',true,writeDisabled()));
            content+=`<form id="settingsForm" class="card"><div class="form-grid"><label>ชื่อลาน<input name="name" value="${escape(s.name)}" maxlength="160" required></label><label>ที่อยู่<input name="address" value="${escape(s.address)}" maxlength="160" required></label><label>ราคา / ชั่วโมง (บาท)<input name="rate" type="number" min="0" max="10000" step="1" value="${s.rate}" required></label><label>ฟรีนาทีแรก<input name="freeMinutes" type="number" min="0" max="1440" value="${s.freeMinutes}" required></label></div><label class="check-label"><input name="active" type="checkbox" ${s.active?'checked':''}>เปิดรับรถใหม่</label><label class="check-label"><input name="membership" type="checkbox" ${s.features.membership?'checked':''}>เปิดสมาชิก</label><label class="check-label"><input name="reservation" type="checkbox" ${s.features.reservation?'checked':''}>เปิดการจอง</label><p class="help">คิดเฉพาะเวลาที่เกินช่วงฟรี ปัดขึ้นเป็นชั่วโมง เก็บอัตราขณะรถเข้ากับตั๋วเดิม การเปลี่ยนราคาจึงไม่เปลี่ยนตั๋วที่กำลังจอด</p><button class="primary" ${writeDisabled()?'disabled':''}>บันทึกการตั้งค่า</button></form><div class="card section-gap"><h3>ส่วนขยาย ${escape(C.templates[s.businessType][0])}</h3><p>ตั้งกฎราคาและสมาชิกได้จากปุ่มด้านบน มีคูปองแบบเจ้าหน้าที่ตรวจและ Valet ตามประเภทลาน ส่วน PMS, POS และการชำระเงินออนไลน์ยังไม่เชื่อมผู้ให้บริการ</p></div>`;
        } else if(tab==='users') {
            content=heading('ทีมงานของบริษัท','บัญชีหลักของลูกค้าเป็นเจ้าของบริษัท · สร้างบัญชีย่อยให้พนักงานล็อกอินด้วยชื่อและรหัสของตนเอง',button('user','＋ พนักงาน / ผู้ดูแล',false,writeDisabled()||!state.sites.length));
            content+=table(['บริษัท','แพ็กเกจ'],state.tenants.map(t=>[escape(t.name),escape(t.plan)+' · ยังไม่มีเรียกเก็บเงิน']));
            content+=`<h3 class="section-gap">บัญชีผู้ใช้งาน</h3>`+table(['ชื่อบัญชี','สิทธิ์','บริษัท','ลานที่เข้าถึง','สถานะ','จัดการ'],state.users.map(u=>[escape(u.username),roles[u.role],escape(state.tenants.find(t=>t.id===u.tenantId)?.name||'แพลตฟอร์ม'),['owner','super_admin'].includes(u.role)?'ทุกลานในสิทธิ์':escape(state.sites.filter(s=>(u.siteIds||[]).includes(s.id)).map(s=>s.name).join(', ')),u.active===false?'ปิดใช้งาน':'ใช้งาน',u.id!==state.user.id&&u.role!=='super_admin'&&(state.user.role==='super_admin'||['admin','staff'].includes(u.role))?(['admin','staff'].includes(u.role)?button('editTeamUser','แก้สิทธิ์ / ลาน',false,writeDisabled(),`data-id="${escape(u.id)}"`)+button('resetTeamPassword','ตั้งรหัสใหม่',false,writeDisabled(),`data-id="${escape(u.id)}"`):'')+button('toggleUser',u.active===false?'เปิดบัญชี':'ปิดบัญชี',false,writeDisabled(),`data-id="${escape(u.id)}"`)+button('revokeUser','ออกจากระบบทุกเครื่อง',false,writeDisabled(),`data-id="${escape(u.id)}"`):'—']));
        } else if(tab==='audit') {
            content=heading('บันทึกกิจกรรม','ล่าสุด 5,000 เหตุการณ์ · ไม่บันทึกรหัสผ่าน');
            content+=table(['เวลา','บัญชี','คำสั่ง','ลาน'],state.audit.slice().reverse().map(a=>[time(a.at),escape(a.actor),escape(a.action),escape(state.sites.find(s=>s.id===a.siteId)?.name||'—')]));
        }
        if(tab==='operations') {
            content+=button('visitor','ขออนุมัติผู้มาติดต่อ',false,writeDisabled());
            if(s.businessType==='MALL'&&manager()) content+='<h3>คูปองที่ตรวจโดยเจ้าหน้าที่</h3>'+table(['ทะเบียน','ส่วนลด',''],s.tickets.filter(t=>t.status==='ACTIVE').map(t=>[escape(t.licensePlate),`${t.couponDiscountPercent||0}%`,button('coupon','บันทึกคูปอง',false,writeDisabled()||!!t.couponCode,`data-id="${escape(t.ticketId)}"`)]));
            if(s.businessType==='HOTEL') content+='<h3>Valet / ผู้รับผิดชอบรถ</h3>'+table(['ทะเบียน','สถานะ','ผู้รับผิดชอบ',''],s.tickets.filter(t=>t.status==='ACTIVE').map(t=>{const stage=({'':'RECEIVED',RECEIVED:'PARKED',PARKED:'RETURNED'})[t.valetStage||''];return [escape(t.licensePlate),escape(t.valetStage||'ยังไม่รับฝาก'),escape(t.valetStaff||'—'),stage?button('valet',({RECEIVED:'รับฝากรถ',PARKED:'จอดแล้ว',RETURNED:'ส่งคืนแล้ว'})[stage],false,writeDisabled(),`data-id="${escape(t.ticketId)}" data-stage="${stage}"`):'ส่งคืนแล้ว'];}));
        }
        if(tab==='editor') {
            const curves=(s.roadCurves||[]).filter(c=>c.floor===floor);
            content+=`<section class="card section-gap"><h3>แบบร่างถนนโค้ง • ชั้น ${floor}</h3><p>พื้นที่ 100 × 100 เมตร เป็นแบบร่างแยกจาก Grid ที่ใช้รับรถ ยังไม่ตรวจรัศมีเลี้ยวหรือเชื่อมเส้นทางอัตโนมัติ</p>${button('roadCurve','เพิ่มถนนโค้ง',false,writeDisabled())}<svg viewBox="-6 -6 112 112" role="img" aria-label="แบบร่างถนนโค้ง" style="width:100%;max-width:540px;background:#263b35">${curves.map(c=>`<path d="M ${c.x1} ${c.y1} Q ${c.cx} ${c.cy} ${c.x2} ${c.y2}" fill="none" stroke="#8ca69e" stroke-width="${c.width}"/><path d="M ${c.x1} ${c.y1} Q ${c.cx} ${c.cy} ${c.x2} ${c.y2}" fill="none" stroke="white" stroke-width="0.3" stroke-dasharray="2 2"/>`).join('')}</svg>${table(['ชื่อ','กว้าง (ม.)',''],curves.map(c=>[escape(c.label),c.width,button('removeCurve','ลบ',false,writeDisabled(),`data-id="${escape(c.id)}"`)]))}</section>`;
        }
        $('main').innerHTML=content;
        $('billingFilter')?.addEventListener('change',e=>{billingFilter=e.target.value;render();});
        $('supportFilter')?.addEventListener('change',e=>{supportFilter=e.target.value;render();});
        $('analyticsDays')?.addEventListener('change',e=>{analyticsDays=e.target.value;render();});
        if(tab==='settings') $('settingsForm').onsubmit=async e=>{e.preventDefault(); const f=new FormData(e.target); await safely(async()=>{await command('configure',{name:f.get('name'),address:f.get('address'),rate:Number(f.get('rate')),freeMinutes:Number(f.get('freeMinutes')),active:f.has('active'),features:{membership:f.has('membership'),reservation:f.has('reservation')}}); showWorkspace(); notice('บันทึกการตั้งค่าแล้ว');});};
        if(tab==='editor'&&$('cellForm')) $('cellForm').onsubmit=e=>{e.preventDefault(); const f=new FormData(e.target); mutate(()=>{const c=draft.find(c=>c.id===selected); c.label=f.get('label').trim(); c.slotType=f.get('slotType'); c.rotation=Number(f.get('rotation')); c.oneWay=f.has('oneWay');});};
        $('floorPicker')?.addEventListener('change',e=>{floor=Number(e.target.value); selected='';routeIds=[];render();});
    }
    function grid(cells,active=[]) {
        const index=new Map(cells.filter(c=>c.floor===floor).map(c=>[`${c.x}:${c.y}`,c])), occupied=new Set(active.map(t=>t.slotId));
        return `<div class="grid" aria-label="ผังลานชั้น ${floor}">${Array.from({length:384},(_,i)=>{const x=i%24,y=Math.floor(i/24),c=index.get(`${x}:${y}`); const symbol=c?c.type==='SLOT'?c.label:c.type==='ROAD'?(c.oneWay?['→','↓','←','↑'][c.rotation]:'↔'):({ENTRY:'IN',EXIT:'OUT',CROSSING:'▤',BUILDING:'▦',CAMERA:'CAM',SENSOR:'S',BARRIER:'G'})[c.type]:'';return `<button type="button" class="cell ${c?c.type:''} ${c&&occupied.has(c.id)?'occupied':''} ${c&&routeIds.includes(c.id)?'route':''} ${c?.id===selected?'picked':''}" data-x="${x}" data-y="${y}" ${c?`data-cell="${escape(c.id)}" draggable="${tab==='editor'&&tool==='SELECT'}"`:''} aria-label="${escape(c?c.label+' '+C.types[c.type]:`ช่องตาราง ${x+1},${y+1}`)}">${escape(symbol)}</button>`;}).join('')}</div>`;
    }
    function inspector() {
        const c=draft.find(c=>c.id===selected);
        if(!c) return '<div class="inspector help">เลือกชิ้นส่วนเพื่อเปลี่ยนชื่อ ประเภทช่อง และทิศทางถนน</div>';
        return `<div class="inspector"><h3>${escape(C.types[c.type])} · (${c.x+1}, ${c.y+1})</h3><form id="cellForm"><div class="form-grid"><label>ชื่อ / หมายเลข<input name="label" value="${escape(c.label)}" maxlength="160" required></label><label>ประเภทช่อง<select name="slotType">${options(C.slotTypes,c.slotType)}</select></label><label>ทิศทาง<select name="rotation">${options({0:'ขวา →',1:'ลง ↓',2:'ซ้าย ←',3:'ขึ้น ↑'},c.rotation)}</select></label><label class="check-label"><input name="oneWay" type="checkbox" ${c.oneWay?'checked':''}>ถนนทางเดียว</label></div><div class="actions"><button type="submit">ใช้ค่ากับชิ้นส่วน</button>${button('deleteCell','ลบชิ้นส่วน')}</div></form></div>`;
    }
    function mutate(fn) { if(preview) return notice('ตัวอย่างอ่านอย่างเดียว',true); undo.push(C.clone(draft)); if(undo.length>40) undo.shift(); redo=[]; fn(); dirty=true; routeIds=[];render(); }
    function putCell(x,y,type) {
        const existing=draft.find(c=>c.floor===floor&&c.x===x&&c.y===y);
        if(type==='SELECT') { selected=existing?.id||''; render(); return; }
        if(type==='ERASE') { if(existing) mutate(()=>{draft=draft.filter(c=>c.id!==existing.id);selected='';}); return; }
        if(existing) return notice('ตำแหน่งนี้มีชิ้นส่วนแล้ว กรุณาย้ายหรือลบก่อน',true);
        mutate(()=>{const id=C.createId();draft.push({id,type,x,y,floor,label:type==='SLOT'?`F${floor}-${x+1}-${y+1}`:C.types[type],slotType:'STANDARD',rotation:0,oneWay:false});selected=id;});
    }
    function fee(t) {
        if(t.paidAt)return Number(t.fee||0);
        const seconds=Math.max(0,Math.floor((Date.now()+(current()?.sample?Number(current().simulationMinutes||0)*60000:0)-new Date(t.entryTime).getTime())/1000));
        const hours=Math.ceil(Math.max(0,seconds-t.freeMinutes*60)/3600),cap=Number(t.dailyCap||0),rate=Number(t.rate);
        const gross=cap>0?Math.floor(hours/24)*Math.min(24*rate,cap)+Math.min((hours%24)*rate,cap):hours*rate;
        return Math.ceil(gross*(100-Math.max(t.memberDiscountPercent||0,t.couponDiscountPercent||0))/100)+Number(t.lostTicketPenalty||0);
    }
    function historyRows() { return current().tickets.filter(t=>{const date=new Date(t.entryTime), local=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;return (!filters.from||local>=filters.from)&&(!filters.to||local<=filters.to)&&t.licensePlate.toLowerCase().includes(filters.plate.toLowerCase());}).slice().sort((a,b)=>b.entryTime.localeCompare(a.entryTime)); }
    function field(name,label,type='text',value='',extra='') { return `<label>${label}<input name="${name}" type="${type}" value="${escape(value)}" required maxlength="160" ${extra}></label>`; }
    function select(name,label,values,selected='') { return `<label>${label}<select name="${name}">${options(values,selected)}</select></label>`; }
    function modal(title,html,fn) { $('modalContent').innerHTML=`<h2>${title}</h2>${html}`; modalSubmit=fn; $('modal').showModal(); }
    async function safely(fn) { try {await fn();} catch(e) { notice(e.message,true); } }
    function siteOptions(vehicleType) {
        const s=current();
        const taken=new Set((s?.tickets||[]).filter(t=>t.status==='ACTIVE').map(t=>t.slotId));
        return Object.fromEntries((s?.published||[]).filter(c=>c.type==='SLOT'&&!taken.has(c.id)&&(!vehicleType||C.compatible(vehicleType,c.slotType))).map(c=>[c.id,`${c.label} • ${C.slotTypes[c.slotType]}`]));
    }
    async function action(name,element) {
        const s=current();
        if(name==='enterTenant') {
            if(!checkDirty())return;
            tenantScope=element.dataset.id; acceptState(allState); siteId='';tab='sites';filters={from:'',to:'',plate:''};floor=1;loadDraft();showWorkspace();return;
        }
        if(name==='sampleWorkspace') {
            if(!confirm('สร้างบริษัททดลองพร้อมลานคอนโด ห้าง โรงแรม และประวัติสมมุติ 3 เดือน? ข้อมูลนี้แยกจากบริษัทจริง'))return;
            const before=new Set(state.sites.map(item=>item.id));
            await command('createSampleWorkspace');
            tenantScope=allState.tenants.find(t=>t.sample).id;acceptState(allState);
            siteId=state.sites.find(item=>!before.has(item.id))?.id||'';
            tab='editor';floor=1;loadDraft();showWorkspace();notice('สร้างพื้นที่ทดลองแล้ว เลือกเครื่องมือทางซ้ายเพื่อแก้ผัง หรือสลับชั้นด้านบน');return;
        }
        if(name==='sampleLayout') {
            if(draft.length&&!confirm('แทนที่แบบร่างปัจจุบันด้วยผังตัวอย่าง 2 ชั้น? สามารถกดย้อนกลับได้ก่อนบันทึก'))return;
            mutate(()=>{draft=C.sampleLayout(s.businessType);selected='';floor=1;});
            notice('ใส่ผังตัวอย่างในแบบร่างแล้ว ตรวจผังและบันทึกก่อนเผยแพร่');return;
        }
        if(name==='roadCurve') modal('ถนนโค้งแบบร่าง (เมตร)',field('label','ชื่อถนน')+['x1','y1','cx','cy','x2','y2'].map((k,i)=>field(k,({x1:'เริ่ม X',y1:'เริ่ม Y',cx:'จุดควบคุม X',cy:'จุดควบคุม Y',x2:'จบ X',y2:'จบ Y'})[k],'number',[10,10,80,10,80,80][i],'min="0" max="100"')).join('')+field('width','ความกว้างถนน (เมตร)','number',6,'min="2" max="12"'),f=>command('addRoadCurve',{label:f.get('label'),floor,...Object.fromEntries(['x1','y1','cx','cy','x2','y2','width'].map(k=>[k,Number(f.get(k))]))}));
        if(name==='removeCurve') {if(!confirm('ลบถนนแบบร่างนี้?'))return;await command('removeRoadCurve',{curveId:element.dataset.id});showWorkspace();}
        if(name==='provision') {
            if(!confirm('สร้างคีย์ใหม่จะยกเลิกคีย์เดิม ต้องตั้งค่าอุปกรณ์ใหม่ ต้องการดำเนินการ?')) return;
            await command('provisionDevice',{deviceId:element.dataset.id});
            const token=state.deviceSecret;delete state.deviceSecret;showWorkspace();
            modal('คีย์อุปกรณ์ — แสดงครั้งเดียว',`<p>เก็บคีย์นี้ในอุปกรณ์ ผ่าน HTTPS เท่านั้น ห้ามแชร์</p><label>คีย์<input readonly value="${escape(token)}"></label><p>Site ID: ${escape(siteId)}<br>Device ID: ${escape(element.dataset.id)}</p>`,async()=>{});
        }
        if(name==='queueLed'||name==='disableDevice') {await command(name,{deviceId:element.dataset.id});showWorkspace();}
        if(name==='pricing') {
            const p=s.policy||{},fields={carRate:'รถยนต์',evRate:'EV',motorcycleRate:'มอเตอร์ไซค์',truckRate:'รถใหญ่',weekendRate:'เสาร์–อาทิตย์',holidayRate:'วันหยุด'};
            modal('กฎราคาและสิทธิ์', '<p>ราคาเป็นบาท/ชั่วโมง ใส่ -1 เพื่อใช้ราคาเดิม วันหยุดมีลำดับสูงสุด ตามวันที่รถเข้า (เวลาไทย)</p>'+Object.entries(fields).map(([key,label])=>field(key,label,'number',p[key]??-1,'min="-1" max="10000"')).join('')+field('dailyCap','เพดานต่อ 24 ชั่วโมงที่คิดเงิน (0 = ไม่จำกัด)','number',p.dailyCap||0,'min="0" max="100000"')+field('memberDiscountPercent','ส่วนลดสมาชิก (%)','number',p.memberDiscountPercent||0,'min="0" max="100"')+field('roomQuota','โควตารถต่อห้อง (0 = ไม่จำกัด)','number',p.roomQuota||0,'min="0" max="100"')+`<label>วันหยุด YYYY-MM-DD คั่นด้วยจุลภาค<input name="holidays" value="${escape((p.holidays||[]).join(','))}"></label><label class="check-label"><input name="requireVisitorApproval" type="checkbox" ${p.requireVisitorApproval?'checked':''}>รถที่ไม่ใช่สมาชิกต้องอนุมัติก่อนเข้า</label>`, f=>{
                const policy=Object.fromEntries([...Object.keys(fields),'dailyCap','memberDiscountPercent','roomQuota'].map(k=>[k,Number(f.get(k))]));
                policy.holidays=String(f.get('holidays')).split(',').map(v=>v.trim()).filter(Boolean); policy.requireVisitorApproval=f.has('requireVisitorApproval');
                return command('configurePolicy',{policy});
            });
        }
        if(name==='visitor') modal('ขออนุมัติผู้มาติดต่อ',field('plate','ทะเบียน')+field('room','ห้อง / ผู้ติดต่อ')+field('expires','หมดอายุ (ไม่เกิน 7 วัน)','datetime-local'),f=>command('requestVisitor',{plate:f.get('plate'),room:f.get('room'),expires:new Date(f.get('expires')).toISOString()}));
        if(name==='approveVisitor') { await command('approveVisitor',{visitorId:element.dataset.id,approved:true}); showWorkspace(); }
        if(name==='coupon') modal('คูปองที่เจ้าหน้าที่ตรวจแล้ว',field('code','เลขคูปอง / หลักฐาน (ห้ามซ้ำ)')+field('percent','ส่วนลด (%)','number',10,'min="1" max="100"'),f=>command('applyCoupon',{ticketId:element.dataset.id,code:f.get('code'),percent:Number(f.get('percent'))}));
        if(name==='valet') { await command('valet',{ticketId:element.dataset.id,stage:element.dataset.stage});showWorkspace(); }
        if(name==='toggleUser'||name==='revokeUser') {
            const u=state.users.find(u=>u.id===element.dataset.id);
            if(!u||!confirm(`ยืนยัน ${name==='revokeUser'?'ออกจากระบบทุกเครื่อง':u.active===false?'เปิดบัญชี':'ปิดบัญชี'}: ${u.username}?`)) return;
            await command(name==='toggleUser'?'setUserActive':'revokeSessions',{userId:u.id,active:u.active===false}); showWorkspace();
        }
        if(name==='presentation') {
            await command('createPresentation',{tenantId:state.tenants[0].id});
            const demo=state.sites.find(s=>s.presentation);location.href='dashboard.html?site='+encodeURIComponent(demo.id)+(platformOwner()?'&tenant='+encodeURIComponent(tenantScope):'');return;
        }
        if(name==='openSite'||name==='editSite') { if(!checkDirty())return; siteId=element.dataset.id; tab=name==='editSite'?'editor':'operations';loadDraft();showWorkspace(); }
        if(name==='createSite') modal('สร้างลานจอด',field('name','ชื่อลาน')+(state.user.role==='super_admin'?select('tenantId','บริษัท',Object.fromEntries(state.tenants.map(t=>[t.id,t.name]))):'')+select('businessType','แม่แบบ',Object.fromEntries(Object.entries(C.templates).map(([k,v])=>[k,`${v[0]} — ${v[1]}`])))+'<p class="help">แม่แบบเป็นจุดเริ่มต้น ไม่ล็อกการแก้ไข แม่แบบใช้ผังเริ่มต้นร่วมกัน ปรับให้ตรงพื้นที่จริงก่อนเผยแพร่</p>',async f=>{const before=new Set(state.sites.map(s=>s.id));await command('createSite',Object.fromEntries(f));siteId=state.sites.find(s=>!before.has(s.id))?.id||siteId;tab='editor';loadDraft();});
        if(name==='billingSettings') modal('ตั้งค่าช่องทางรับโอน',`<label>ธนาคาร / เลขบัญชี / ชื่อบัญชี หรือข้อมูลพร้อมเพย์<textarea name="paymentInstructions" required maxlength="2000" rows="5">${escape(allState.platformBilling.paymentInstructions)}</textarea></label><p>ข้อมูลนี้จะแสดงให้เจ้าของบริษัทลูกค้าเห็น บิลที่ออกแล้วเก็บข้อมูลรับโอนเดิมไว้</p>`,f=>command('configureBilling',Object.fromEntries(f)));
        if(name==='issueInvoice') {
            const companies=allState.tenants.filter(t=>!t.sample);
            if(!companies.length){notice('เพิ่มบริษัทลูกค้าจริงก่อนออกบิล',true);return;}
            modal('ออกบิลค่าแพลตฟอร์ม 1 เดือน',select('tenantId','บริษัท',Object.fromEntries(companies.map(t=>[t.id,t.name])),tenantScope)+select('plan','แพ็กเกจ',billingPlans,'STARTER')+field('periodStart','วันเริ่มรอบบริการ','date',allState.platformBilling.today)+field('dueDate','วันครบกำหนดชำระ','date',allState.platformBilling.today)+'<p>ราคาคำนวณจากแพ็กเกจที่เซิร์ฟเวอร์ ไม่รวมฮาร์ดแวร์และงานติดตั้ง บิลที่มีรอบทับซ้อนจะถูกปฏิเสธ</p>',f=>command('issuePlatformInvoice',Object.fromEntries(f)));
        }
        if(name==='reportTransfer') modal('แจ้งโอนค่าแพลตฟอร์ม',field('reference','เลขอ้างอิงธุรกรรม / วันเวลาโอน')+'<p>การแจ้งโอนยังไม่ถือว่าชำระสำเร็จ ต้องรอเจ้าของแพลตฟอร์มตรวจยอด</p>',f=>command('reportPlatformTransfer',{invoiceId:element.dataset.id,reference:f.get('reference')}));
        if(name==='confirmInvoice') {
            const invoice=allState.platformBilling.invoices.find(i=>i.id===element.dataset.id);
            modal('ยืนยันรับเงินจริง',`<p>${escape(invoice.number)} · ${escape(invoice.tenantName)} · ${money(invoice.amount)}</p>`+field('reference','เลขอ้างอิงจากรายการเงินเข้าบัญชี')+'<label class="check-label"><input name="confirmed" type="checkbox" required>ตรวจแล้วว่าเงินเข้าบัญชีครบตามยอดบิล</label>',f=>command('confirmPlatformPayment',{invoiceId:invoice.id,reference:f.get('reference'),confirmed:f.has('confirmed')}));
        }
        if(name==='voidInvoice') modal('ยกเลิกบิลที่ยังไม่รับเงิน',field('reason','เหตุผลยกเลิก'),f=>command('voidPlatformInvoice',{invoiceId:element.dataset.id,reason:f.get('reason')}));
        if(name==='createSupport') modal('แจ้งปัญหาที่พบ',field('subject','หัวข้อปัญหา')+'<label>รายละเอียด / ขั้นตอนที่ทำให้เกิดปัญหา<textarea name="message" required maxlength="4000" rows="6"></textarea></label>',f=>command('createSupportTicket',Object.fromEntries(f)));
        if(name==='replySupport') modal('ตอบกลับรายการแจ้งปัญหา','<label>ข้อความ<textarea name="message" required maxlength="4000" rows="6"></textarea></label>',f=>command('replySupportTicket',{ticketId:element.dataset.id,message:f.get('message')}));
        if(name==='supportStatus') {
            const issue=allState.supportTickets.find(t=>t.id===element.dataset.id);
            modal('อัปเดตสถานะปัญหา',select('status','สถานะ',supportStatuses,issue.status),f=>command('setSupportStatus',{ticketId:issue.id,status:f.get('status')}));
        }
        if(name==='toggleTenant') {
            const target=allState.tenants.find(t=>t.id===element.dataset.id); if(!target)return;
            const active=target.active===false;
            if(!confirm(`${active?'เปิดใช้งานกลับ':'ระงับการใช้งาน'}บริษัท ${target.name}? ${active?'ลูกค้าต้องเข้าสู่ระบบใหม่':'บัญชีทุกคนในบริษัทและ API อุปกรณ์จะหยุดเข้าถึงระบบ ข้อมูลยังอยู่ครบ'}`))return;
            await command('setTenantActive',{tenantId:target.id,active});showWorkspace();notice(active?'เปิดใช้งานบริษัทแล้ว':'ระงับบริษัทแล้ว');
        }
        if(name==='deleteTenant') {
            const target=allState.tenants.find(t=>t.id===element.dataset.id);if(!target)return;
            modal('ลบบริษัทถาวร',`<p>ลบ <strong>${escape(target.name)}</strong> พร้อมบัญชีผู้ใช้ ลานจอด ประวัติรถ การชำระเงิน และสถิติออกจากระบบปัจจุบัน ไม่สามารถกู้คืนด้วยปุ่มเปิดใช้งานกลับได้ สำเนาสำรองเดิมอาจยังมีข้อมูลอยู่</p>`+field('confirmationName','พิมพ์ชื่อบริษัทให้ตรงกัน')+field('currentPassword','รหัสผ่านเจ้าของแพลตฟอร์ม','password'),async f=>{
                await command('deleteTenant',{tenantId:target.id,confirmationName:f.get('confirmationName'),currentPassword:f.get('currentPassword')});tenantScope='';tab='customers';siteId='';acceptState(allState);
            });
        }
        if(name==='tenant') modal('สร้างบริษัทพร้อมเจ้าของ',field('name','ชื่อบริษัท')+field('username','บัญชีเจ้าของ','text','','pattern="[a-z0-9._-]{3,40}"')+field('password','รหัสผ่าน (12 ตัวขึ้นไป)','password','','minlength="12" maxlength="128"'),async f=>{await command('createTenant',Object.fromEntries(f));tab='customers';tenantScope='';acceptState(allState);});
        if(name==='user'||name==='editTeamUser') {
            const target=name==='editTeamUser'?state.users.find(u=>u.id===element.dataset.id):null;
            const sites=state.sites.filter(s=>!target||s.tenantId===target.tenantId);
            modal(target?'แก้ไขบัญชีย่อย':'สร้างบัญชีย่อยให้พนักงาน',
                (target?`<p>${escape(target.username)}</p>`:field('username','ชื่อบัญชีสำหรับล็อกอิน','text','','pattern="[a-z0-9._-]{3,40}"')+field('password','รหัสผ่าน 12–128 ตัวอักษร','password','','minlength="12" maxlength="128"'))+
                select('role','สิทธิ์',{staff:'พนักงาน — รับรถ / ชำระเงิน / รถออก',admin:'ผู้ดูแล — จัดการงานประจำลาน'},target?.role||'staff')+
                '<p>เลือกลานที่อนุญาตอย่างน้อยหนึ่งแห่ง</p>'+sites.map(s=>`<label class="check-label"><input type="checkbox" name="siteIds" value="${escape(s.id)}" ${(target?.siteIds||[siteId]).includes(s.id)?'checked':''}>${escape(s.name)}</label>`).join('')+
                '<p class="help">ใช้บัญชีนี้เข้าสู่หน้า dashboard.html หรือ platform.html ได้ โดยไม่ต้องให้รหัสบัญชีหลักแก่พนักงาน</p>',async f=>{
                    const assigned=f.getAll('siteIds'); if(!assigned.length)throw new Error('กรุณาเลือกลานอย่างน้อยหนึ่งแห่ง');
                    if(target) await command('updateTeamUser',{userId:target.id,role:f.get('role'),siteIds:assigned});
                    else await command('createUser',{username:f.get('username'),password:f.get('password'),role:f.get('role'),tenantId:sites.find(s=>s.id===assigned[0]).tenantId,siteIds:assigned});
                });
        }
        if(name==='resetTeamPassword') {
            const target=state.users.find(u=>u.id===element.dataset.id);
            modal('ตั้งรหัสผ่านบัญชีย่อย',`<p>${escape(target.username)} · บัญชีนี้จะออกจากระบบทุกเครื่อง</p>`+field('newPassword','รหัสผ่านใหม่ 12–128 ตัวอักษร','password','','minlength="12" maxlength="128"')+field('confirmPassword','ยืนยันรหัสผ่าน','password'),async f=>{
                if(f.get('newPassword')!==f.get('confirmPassword'))throw new Error('รหัสผ่านไม่ตรงกัน');
                await command('resetTeamPassword',{userId:target.id,newPassword:f.get('newPassword')});
            });
        }
        if(name==='validate') {const errors=C.validate(draft);notice(errors.length?errors.slice(0,6).join(' • '):'ผังเชื่อมทางเข้า–ช่องจอด–ทางออกครบ (ไม่ใช่การรับรองพื้นที่จริง)',!!errors.length);}
        if(name==='saveLayout'||name==='publish') {
            if(name==='publish') {const errors=C.validate(draft);if(errors.length)throw new Error(errors.slice(0,6).join(' • '));}
            await command('saveLayout',{cells:draft}); dirty=false;
            if(name==='publish')await command('publish');loadDraft();showWorkspace();notice(name==='publish'?'เผยแพร่ผังแล้ว ช่องพร้อมรับรถ':'บันทึกแบบร่างแล้ว');
        }
        if(name==='restore') {if(!checkDirty())return;await command('restore',{versionId:element.dataset.id});loadDraft();showWorkspace();notice('เรียกเวอร์ชันเป็นแบบร่างแล้ว');}
        if(name==='undo'&&undo.length&&!preview) {redo.push(C.clone(draft));draft=undo.pop();dirty=true;selected='';render();}
        if(name==='redo'&&redo.length&&!preview) {undo.push(C.clone(draft));draft=redo.pop();dirty=true;selected='';render();}
        if(name==='deleteCell') mutate(()=>{draft=draft.filter(c=>c.id!==selected);selected='';});
        if(name==='checkin') {
            modal('รับรถเข้าลาน',field('plate','ทะเบียน')+select('vehicleType','ประเภทรถ',{CAR:'รถยนต์',ELECTRIC_VEHICLE:'EV',MOTORCYCLE:'มอเตอร์ไซค์',TRUCK:'รถใหญ่'})+select('slotId','ช่องว่างที่เหมาะกับรถ',siteOptions('CAR'))+'<p class="help">แสดงเฉพาะช่องว่างตามประเภทรถ เซิร์ฟเวอร์ตรวจการจองอีกครั้งก่อนรับรถ</p>',f=>command('checkin',Object.fromEntries(f)));
            const vehicle=$('modal').querySelector('[name=vehicleType]'), slot=$('modal').querySelector('[name=slotId]');
            const update=()=>{const available=siteOptions(vehicle.value);slot.innerHTML=Object.keys(available).length?options(available):'<option value="">ไม่มีช่องว่างสำหรับรถประเภทนี้</option>';slot.required=true;};
            vehicle.onchange=update;update();
        }
        if(name==='checkout') {const t=s.tickets.find(t=>t.ticketId===element.dataset.id);modal(t.paidAt?'ยืนยันนำรถที่ชำระแล้วออก':'ยืนยันรับเงินสดและนำรถออก',`<p>ทะเบียน ${escape(t.licensePlate)} · ช่อง ${escape(t.slotNumber)}</p><h2>${money(fee(t))}</h2><p>ยอดประมาณการ ณ ตอนนี้ เซิร์ฟเวอร์คำนวณอีกครั้งเมื่อยืนยัน ยังไม่มี Payment Gateway หรือคำสั่งเปิดไม้กั้น</p><label class="check-label"><input type="checkbox" required>${t.paidAt?'ตรวจแล้วว่าตั๋วชำระเรียบร้อย ยืนยันนำรถออก':'รับเงินสดเรียบร้อยแล้ว / ไม่มีค่าบริการ'}</label>`,()=>command('checkout',{ticketId:t.ticketId}));}
        if(name==='member')modal('เพิ่มสมาชิก / ผู้เข้าพัก',field('name','ชื่อสมาชิก')+field('plate','ทะเบียน')+field('room','เลขห้อง / หน่วยงาน')+field('starts','วันเริ่มสิทธิ์ / เข้าพัก','date')+field('expires','วันหมดอายุ / ออก','date'),f=>command('addMember',Object.fromEntries(f)));
        if(name==='reserve')modal('จองช่องล่วงหน้า',field('plate','ทะเบียน')+select('slotId','ช่องจอด',siteOptions())+field('from','เริ่ม','datetime-local')+field('to','สิ้นสุด','datetime-local'),f=>command('reserve',{plate:f.get('plate'),slotId:f.get('slotId'),from:new Date(f.get('from')).toISOString(),to:new Date(f.get('to')).toISOString()}));
        if(name==='cancelReservation') {if(!confirm('ยกเลิกการจองนี้?'))return;await command('cancelReservation',{reservationId:element.dataset.id});showWorkspace();}
        if(name==='device')modal('ทะเบียนอุปกรณ์ (ยังไม่เชื่อมต่อ)',field('name','ชื่อ / ตำแหน่ง')+select('type','ชนิด',{CAMERA:'กล้อง',ESP32:'ESP32',SENSOR:'เซนเซอร์',BARRIER:'ไม้กั้น'}),f=>command('addDevice',Object.fromEntries(f)));
        if(name==='filter') {const next={from:$('filterFrom').value,to:$('filterTo').value,plate:$('filterPlate').value.trim()};if(next.from&&next.to&&next.from>next.to)throw new Error('วันที่เริ่มต้องไม่เกินวันที่สิ้นสุด');filters=next;render();}
        if(name==='export') {for(const [id,value]of [['historyFrom',filters.from],['historyTo',filters.to],['historyPlate',filters.plate]])$(id).value=value;window.setHistoryExportRecords(historyRows());window.exportParkingHistory();notice($('historyExportMessage').textContent);}
    }
    $('main').addEventListener('click',e=>{const target=e.target.closest('button');if(!target)return;if(target.dataset.action)safely(()=>action(target.dataset.action,target));if(target.dataset.tool){tool=target.dataset.tool;render();}if(target.dataset.x!==undefined){if(tab==='editor')putCell(Number(target.dataset.x),Number(target.dataset.y),tool);else if(tab==='operations'&&target.dataset.cell){routeIds=C.route(current().published,target.dataset.cell);render();}}});
    $('main').addEventListener('dragstart',e=>{const target=e.target.closest('[draggable="true"]');if(!target)return;e.dataTransfer.setData('text/plain',JSON.stringify(target.dataset.tool?{tool:target.dataset.tool}:{id:target.dataset.cell}));});
    $('main').addEventListener('input',e=>{if(['filterFrom','filterTo','filterPlate'].includes(e.target.id)){const exportButton=$('main').querySelector('[data-action="export"]');if(exportButton)exportButton.disabled=true;}});
    $('main').addEventListener('dragover',e=>{if(tab==='editor'&&e.target.closest('.cell'))e.preventDefault();});
    $('main').addEventListener('drop',e=>{const cell=e.target.closest('.cell');if(!cell||tab!=='editor')return;e.preventDefault();safely(()=>{const data=JSON.parse(e.dataTransfer.getData('text/plain')),x=Number(cell.dataset.x),y=Number(cell.dataset.y);if(data.tool&&C.types[data.tool])putCell(x,y,data.tool);else if(data.id){if(draft.some(c=>c.floor===floor&&c.x===x&&c.y===y))throw new Error('ตำแหน่งนี้มีชิ้นส่วนแล้ว');if(!draft.some(c=>c.id===data.id))return;mutate(()=>{const c=draft.find(c=>c.id===data.id);c.x=x;c.y=y;c.floor=floor;selected=c.id;});}});});
    $('nav').onclick=e=>{const b=e.target.closest('[data-nav]');if(!b||!checkDirty())return;tab=b.dataset.nav;if(['customers','analytics'].includes(tab)){tenantScope='';acceptState(allState);}loadDraft();showWorkspace();};
    $('backToCustomers').onclick=()=>{if(!checkDirty())return;tenantScope='';tab='customers';siteId='';acceptState(allState);loadDraft();showWorkspace();};
    $('sitePicker').onchange=e=>{if(!checkDirty()){e.target.value=siteId;return;}siteId=e.target.value;loadDraft();showWorkspace();};
    $('refreshButton').onclick=()=>safely(async()=>{if(!checkDirty())return;if(!preview)acceptState(await api('state'));loadDraft();showWorkspace();notice('อัปเดตแล้ว');});
    $('logoutButton').onclick=()=>safely(async()=>{if(!checkDirty())return;if(!preview)await api('logout',{});state=null;allState=null;tenantScope='';preview=false;tab='sites';siteId='';filters={from:'',to:'',plate:''};dirty=false;$('workspace').hidden=true;$('loginScreen').hidden=false;});
    const passwordButton=document.createElement('button');
    passwordButton.textContent='เปลี่ยนรหัสผ่าน';
    $('logoutButton').before(passwordButton);
    passwordButton.onclick=()=>{
        if(preview) return notice('ตัวอย่างอ่านอย่างเดียว',true);
        modal('เปลี่ยนรหัสผ่าน',field('currentPassword','รหัสผ่านเดิม','password')+field('newPassword','รหัสผ่านใหม่ 12–128 ตัวอักษร','password','','minlength="12"')+field('confirmPassword','ยืนยันรหัสผ่านใหม่','password'),async f=>{
            if(f.get('newPassword')!==f.get('confirmPassword')) throw new Error('รหัสผ่านใหม่ไม่ตรงกัน');
            await command('changePassword',{currentPassword:f.get('currentPassword'),newPassword:f.get('newPassword')});
            await api('logout',{}).catch(()=>{});
            location.reload();
        });
    };
    $('cancelModal').onclick=()=>$('modal').close();
    $('modalForm').onsubmit=e=>{e.preventDefault();if(busy)return;safely(async()=>{await modalSubmit(new FormData(e.target));$('modal').close();showWorkspace();notice('บันทึกแล้ว');});};
    $('loginForm').onsubmit=e=>{e.preventDefault();safely(async()=>{tenantScope='';acceptState(await api('login',Object.fromEntries(new FormData(e.target))));preview=false;siteId='';tab='sites';e.target.reset();loadDraft();showWorkspace();});};
    $('previewButton').onclick=()=>{acceptState(C.preview());preview=true;siteId=state.sites[0].id;tab='sites';loadDraft();showWorkspace();};
    window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
    if(location.hostname.endsWith('github.io'))$('connectionHint').textContent='GitHub Pages ไม่มี Java Backend หน้านี้จึงดูตัวอย่างได้เท่านั้น การบันทึกต้องรันเซิร์ฟเวอร์';
    else api('state').then(data=>{acceptState(data);showWorkspace();loadDraft();}).catch(()=>{});
})();
