/* Pure domain helpers shared by the editor and offline unit tests. */
(function (root) {
    'use strict';
    const templates = {
        CONDO: ['คอนโด', 'ลูกบ้าน เลขห้อง และผู้มาติดต่อ', 'อนุมัติผู้มาติดต่อ / โควตาต่อห้อง'],
        MALL: ['ห้างสรรพสินค้า', 'จอดรายชั่วโมง การจอง และหลายโซน', 'คูปองร้านค้า / ส่วนลดตามยอดซื้อ'],
        HOTEL: ['โรงแรม', 'ทะเบียนรถ ห้องพัก และวันหมดอายุสมาชิก', 'เชื่อม PMS / Valet parking'],
        OFFICE: ['สำนักงาน', 'พนักงานและที่จอดประจำ', 'เชื่อมระบบบุคลากร'],
        HOSPITAL: ['โรงพยาบาล', 'ช่องผู้พิการและจุดรับส่ง', 'สิทธิ์ผู้ป่วย / รถฉุกเฉิน'],
        SCHOOL: ['สถานศึกษา', 'บุคลากรและผู้มาติดต่อ', 'ตารางรถรับส่ง'],
        PUBLIC: ['ลานสาธารณะ', 'รถเข้าออกและค่าจอดรายชั่วโมง', 'สมาชิกหลายแพ็กเกจ'],
        EVENT: ['สถานที่จัดงาน', 'จองล่วงหน้าและแบ่งโซน', 'เชื่อมบัตรเข้างาน'],
        CUSTOM: ['กำหนดเอง', 'เริ่มจากพื้นที่ว่างและออกแบบเอง', 'กฎขั้นสูงเพิ่มเติม']
    };
    const types = {SELECT:'เลือก / ย้าย', ROAD:'ถนน', ENTRY:'ทางเข้า', EXIT:'ทางออก', SLOT:'ช่องจอด', CROSSING:'ทางม้าลาย', BUILDING:'อาคาร / สิ่งกีดขวาง', CAMERA:'กล้อง', BARRIER:'ไม้กั้น', SENSOR:'เซนเซอร์', ERASE:'ลบ'};
    const slotTypes = {STANDARD:'รถยนต์', EV_CHARGING:'EV', MOTORCYCLE:'มอเตอร์ไซค์', VIP:'VIP', ACCESSIBLE:'ผู้พิการ', LARGE:'รถใหญ่'};
    // randomUUID requires HTTPS; getRandomValues also works on HTTP EC2 hosts.
    function createId() {
        if (typeof root.crypto.randomUUID === 'function') return root.crypto.randomUUID();
        const bytes = root.crypto.getRandomValues(new Uint8Array(16));
        bytes[6] = (bytes[6] & 15) | 64;
        bytes[8] = (bytes[8] & 63) | 128;
        const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
        return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
    }
    const clone = value => JSON.parse(JSON.stringify(value));
    const near = (a,b) => a.floor === b.floor && Math.abs(a.x-b.x)+Math.abs(a.y-b.y)===1;
    const road = c => ['ROAD','ENTRY','EXIT','CROSSING','BARRIER'].includes(c.type);
    function edge(a,b) {
        if (!near(a,b)) return false;
        const [x,y] = [[1,0],[0,1],[-1,0],[0,-1]][a.rotation];
        return !a.oneWay || b.x-a.x===x && b.y-a.y===y;
    }
    function route(cells, slotId) {
        const slot=cells.find(c=>c.id===slotId&&c.type==='SLOT');
        if (!slot) return [];
        const roads=cells.filter(road), queue=roads.filter(c=>c.type==='ENTRY'), parent=new Map(queue.map(c=>[c.id,null]));
        for (let i=0; i<queue.length; i++) {
            const c=queue[i];
            if(near(c,slot)) { const result=[slotId]; for(let p=c.id;p!==null;p=parent.get(p)) result.push(p); return result.reverse(); }
            for(const b of roads) if(edge(c,b)&&!parent.has(b.id)) { parent.set(b.id,c.id); queue.push(b); }
        }
        return [];
    }
    function validate(cells) {
        const errors=[], ids=new Set(), places=new Set(), labels=new Set();
        for(const c of cells) {
            const p=`${c.floor}:${c.x}:${c.y}`;
            if(ids.has(c.id)||places.has(p)) errors.push('ID หรือพิกัดซ้ำ'); ids.add(c.id); places.add(p);
            if(c.type==='SLOT') { if(labels.has(c.label)) errors.push(`หมายเลข ${c.label} ซ้ำ`); labels.add(c.label); }
        }
        if(!labels.size) errors.push('ต้องมีช่องจอดอย่างน้อย 1 ช่อง');
        const roads=cells.filter(road), queue=roads.filter(c=>c.type==='EXIT'), exit=new Set(queue.map(c=>c.id));
        for(let i=0;i<queue.length;i++) for(const b of roads) if(edge(b,queue[i])&&!exit.has(b.id)) { exit.add(b.id); queue.push(b); }
        for(const c of cells.filter(c=>c.type==='SLOT')) {
            if(!route(cells,c.id).length) errors.push(`${c.label}: ไม่มีเส้นทางจากทางเข้า`);
            if(!roads.some(r=>near(r,c)&&exit.has(r.id))) errors.push(`${c.label}: ไปทางออกไม่ได้`);
        }
        return errors;
    }
    function template(type) {
        if(type==='CUSTOM') return [];
        const cells=[], add=(id,type,x,y,label,slotType='STANDARD')=>cells.push({id,type,x,y,label,slotType,floor:1,rotation:0,oneWay:false});
        for(let x=0;x<24;x++) add(`R${x}`,x===0?'ENTRY':x===23?'EXIT':'ROAD',x,7,'ทางรถ');
        for(let x=2;x<22;x+=2) { add(`A${x}`,'SLOT',x,6,`A-${x}`,x===2?'EV_CHARGING':'STANDARD'); add(`B${x}`,'SLOT',x,8,`B-${x}`,x===2?'ACCESSIBLE':x===4?'MOTORCYCLE':'STANDARD'); }
        return cells;
    }
    function sampleLayout(type) {
        const cells=template(type==='CUSTOM'?'PUBLIC':type);
        for(const [id,cellType,label] of [['R1','BARRIER','ไม้กั้นเข้า'],['R3','CROSSING','ทางม้าลาย'],['R22','BARRIER','ไม้กั้นออก']]) {
            const cell=cells.find(c=>c.id===id);cell.type=cellType;cell.label=label;
        }
        const add=(id,cellType,x,y,label,slotType='STANDARD',floor=1)=>cells.push({id,type:cellType,x,y,label,slotType,floor,rotation:0,oneWay:false});
        add('C1','CAMERA',1,5,'กล้องจำลอง ทางเข้า');
        add('S1','SENSOR',11,5,'เซนเซอร์จำลอง');
        add('D1','BUILDING',13,5,'อาคารตัวอย่าง');
        for(let x=0;x<24;x++)add(`F2R${x}`,x===0?'ENTRY':x===23?'EXIT':'ROAD',x,7,'ถนนชั้น 2','STANDARD',2);
        for(let x=2;x<22;x+=2) {
            add(`F2A${x}`,'SLOT',x,6,`F2-A-${x}`,x===2?'EV_CHARGING':'STANDARD',2);
            add(`F2B${x}`,'SLOT',x,8,`F2-B-${x}`,x===4?'MOTORCYCLE':'STANDARD',2);
        }
        return cells;
    }
    function preview() {
        const sites=['CONDO','MALL','HOTEL'].map((type,i)=>({id:`preview-${i}`,tenantId:'preview',name:['Green Residence','Green Avenue Mall','Grey Garden Hotel'][i],businessType:type,address:'ข้อมูลตัวอย่างสำหรับดูหน้าตา',active:true,rate:20,freeMinutes:15,draft:template(type),published:template(type),versions:[],tickets:[],memberships:[],reservations:[],devices:[],features:{membership:true,reservation:true}}));
        return {revision:0,user:{username:'Preview',role:'owner',tenantId:'preview',siteIds:[]},tenants:[{id:'preview',name:'GreenPark • ตัวอย่าง',plan:'trial'}],users:[],audit:[],sites};
    }
    function compatible(vehicle,slot) {
        if(slot==='MOTORCYCLE'||vehicle==='MOTORCYCLE') return slot==='MOTORCYCLE'&&vehicle==='MOTORCYCLE';
        if(vehicle==='TRUCK') return slot==='LARGE';
        if(slot==='EV_CHARGING') return vehicle==='ELECTRIC_VEHICLE';
        return ['CAR','ELECTRIC_VEHICLE'].includes(vehicle);
    }
    root.PlatformCore={templates,types,slotTypes,createId,clone,route,validate,template,sampleLayout,preview,compatible};
    if(typeof module!=='undefined') module.exports=root.PlatformCore;
})(typeof window==='undefined'?globalThis:window);
