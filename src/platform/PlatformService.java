package platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import util.SimpleJson;

/** Application service: tenant authorization, layout lifecycle and parking workflows.
 * Every write is copy-on-write; stale clients cannot overwrite newer edits.
 */
public final class PlatformService {
    private final PlatformStore store;
    private Map<String,Object> state;
    private int transactionDepth;
    public synchronized <T> T atomic(java.util.concurrent.Callable<T> action) {
        try {
            if(transactionDepth>0) return action.call();
            return store.transaction(()->{
                String saved=store.load();
                if(saved!=null&&!saved.isBlank()) state=map(Json.parse(saved));
                String before=state==null?null:SimpleJson.toJson(state);
                transactionDepth++;
                try { return action.call(); }
                catch(Exception e) { if(before!=null) state=map(Json.parse(before)); throw e; }
                finally { transactionDepth--; }
            });
        } catch(RuntimeException e) { throw e; }
        catch(Exception e) { throw new IllegalStateException("Platform transaction failed",e); }
    }
    public static final Set<String> BUSINESSES=Set.of("CONDO","MALL","HOTEL","OFFICE","HOSPITAL","SCHOOL","PUBLIC","EVENT","CUSTOM");
    public static final Set<String> ROLES=Set.of("super_admin","owner","admin","staff");
    public PlatformService(Path file, boolean demo, String adminPassword) throws Exception {
        this(new FilePlatformStore(file),demo,adminPassword);
    }
    public PlatformService(PlatformStore store, boolean demo, String adminPassword) throws Exception {
        this.store=store;
        atomic(()->{ initialize(demo,adminPassword); return null; });
    }
    private void initialize(boolean demo,String adminPassword) throws Exception {
        String saved=store.load();
        if(saved!=null&&!saved.isBlank()) { state=map(Json.parse(saved)); return; }
        state=new LinkedHashMap<>(Map.of("revision",0,"tenants",new ArrayList<>(),"sites",new ArrayList<>(),"users",new ArrayList<>(),"audit",new ArrayList<>()));
        if(demo) {
            addTenant("DEMO-A","Green Park • บริษัทตัวอย่าง", "trial");
            addTenant("DEMO-B","บริษัทตัวอย่าง B", "trial");
            addUser("superadmin","DemoPass123!","super_admin","",List.of());
            addUser("owner","DemoPass123!","owner","DEMO-A",List.of());
            addUser("owner2","DemoPass123!","owner","DEMO-B",List.of());
            var condo=createSite("DEMO-A","Green Residence","CONDO");
            createSite("DEMO-A","Green Avenue Mall","MALL");
            createSite("DEMO-A","Grey Garden Hotel","HOTEL");
            createSite("DEMO-B","ลานบริษัท B","PUBLIC");
            addUser("admin","DemoPass123!","admin","DEMO-A",List.of(condo.get("id")));
            addUser("staff","DemoPass123!","staff","DEMO-A",List.of(condo.get("id")));
            for(var site:list(state,"sites")) seedHistory(site);
        } else if(adminPassword!=null && adminPassword.length()>=12) {
            addUser("superadmin",adminPassword,"super_admin","",List.of());
        } else { throw new IllegalArgumentException("Set PLATFORM_ADMIN_PASSWORD (12–128 characters) before first startup"); }
        persist();
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object o) {
        if(!(o instanceof Map)) throw new IllegalArgumentException("Expected object"); return (Map<String,Object>)o;
    }
    @SuppressWarnings("unchecked") public static List<Map<String,Object>> list(Map<String,Object> m,String key) {
        if(!(m.get(key) instanceof List)) throw new IllegalArgumentException("Expected list: "+key);
        return (List<Map<String,Object>>)m.get(key);
    }
    public static String string(Map<String,Object> m,String key) {
        Object value=m.get(key); if(!(value instanceof String s)||s.isBlank()||s.length()>160) throw new IllegalArgumentException("กรอก "+key+" (1–160 ตัวอักษร)");
        return s.trim();
    }
    public static int integer(Map<String,Object> m,String key,int min,int max) {
        if(!(m.get(key) instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max) throw new IllegalArgumentException("ค่า "+key+" ไม่ถูกต้อง");
        return n.intValue();
    }
    public static String password(Map<String,Object> m,String key) {
        if(!(m.get(key) instanceof String s)||s.isEmpty()||s.length()>128) throw new IllegalArgumentException("รหัสผ่านไม่ถูกต้อง");
        return s;
    }
    private String id() { return UUID.randomUUID().toString(); }
    private String now() { return Instant.now().toString(); }
    private void persist() throws Exception {
        store.save(SimpleJson.toJson(state));
    }
    private String hash(String password,String salt) {
        try {
            PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),Base64.getDecoder().decode(salt),210000,256);
            try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
            finally { spec.clearPassword(); }
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private void addUser(String username,String password,String role,String tenant,List<?> sites) {
        if(!username.matches("[a-z0-9._-]{3,40}")||password.length()<12||password.length()>128) throw new IllegalArgumentException("ชื่อบัญชี 3–40 ตัวอักษรอังกฤษ และรหัสผ่าน 12–128 ตัวอักษร");
        if(list(state,"users").stream().anyMatch(u->u.get("username").equals(username))) throw new IllegalArgumentException("ชื่อผู้ใช้นี้มีแล้ว");
        byte[] bytes=new byte[16]; new SecureRandom().nextBytes(bytes); String salt=Base64.getEncoder().encodeToString(bytes);
        list(state,"users").add(new LinkedHashMap<>(Map.of("id",id(),"username",username,"role",role,"tenantId",tenant,"siteIds",new ArrayList<>(sites),"salt",salt,"hash",hash(password,salt))));
    }
    public synchronized String login(String username,String password) {
        return atomic(()->loginLocked(username,password));
    }
    private String loginLocked(String username,String password) {
        if(username==null||password==null||password.length()>128) throw new SecurityException("ข้อมูลเข้าสู่ระบบไม่ถูกต้อง");
        var u=list(state,"users").stream().filter(v->v.get("username").equals(username)).findFirst().orElse(null);
        String salt=u==null?"AAAAAAAAAAAAAAAAAAAAAA==":u.get("salt").toString();
        String calculated=hash(password,salt);
        if(u==null||Boolean.FALSE.equals(u.get("active"))||!MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8),u.get("hash").toString().getBytes(StandardCharsets.UTF_8))) throw new SecurityException("ข้อมูลเข้าสู่ระบบไม่ถูกต้อง");
        return u.get("id").toString();
    }
    private Map<String,Object> user(String userId) {
        return list(state,"users").stream().filter(u->u.get("id").equals(userId)).findFirst().orElseThrow(()->new SecurityException("กรุณาเข้าสู่ระบบ"));
    }
    private boolean superUser(Map<String,Object> u) { return u.get("role").equals("super_admin"); }
    public synchronized int sessionVersion(String userId) {
        return atomic(()->sessionVersionLocked(userId));
    }
    private int sessionVersionLocked(String userId) {
        var u=user(userId);
        if(Boolean.FALSE.equals(u.get("active"))) throw new SecurityException("บัญชีถูกปิดใช้งาน");
        return ((Number)u.getOrDefault("authVersion",0)).intValue();
    }
    private void revoke(Map<String,Object> u) {
        u.put("authVersion",((Number)u.getOrDefault("authVersion",0)).intValue()+1);
    }
    private void setPassword(Map<String,Object> u,String password) {
        if(password.length()<12||password.length()>128) throw new IllegalArgumentException("รหัสผ่านต้องยาว 12–128 ตัวอักษร");
        byte[] bytes=new byte[16]; new SecureRandom().nextBytes(bytes);
        String salt=Base64.getEncoder().encodeToString(bytes);
        u.put("salt",salt); u.put("hash",hash(password,salt)); revoke(u);
    }
    /** Offline recovery: stop every app instance first; preserves all business data. */
    public synchronized void recoverAdmin(String password) throws Exception {
        atomic(()->{recoverAdminLocked(password);return null;});
    }
    private void recoverAdminLocked(String password) throws Exception {
        String before=SimpleJson.toJson(state);
        try {
            var admin=list(state,"users").stream().filter(u->u.get("username").equals("superadmin")&&superUser(u)).findFirst().orElseThrow(()->new IllegalArgumentException("superadmin not found"));
            setPassword(admin,password); admin.put("active",true);
            list(state,"audit").add(new LinkedHashMap<>(Map.of("id",id(),"tenantId","","siteId","","actor","server-console","action","recoverAdmin","at",now())));
            if(list(state,"audit").size()>5000) list(state,"audit").remove(0);
            state.put("revision",((Number)state.get("revision")).intValue()+1); persist();
        } catch(Exception e) { state=map(Json.parse(before)); throw e; }
    }
    private boolean owner(Map<String,Object> u) { return superUser(u)||u.get("role").equals("owner"); }
    private void require(boolean allowed) { if(!allowed) throw new SecurityException("ไม่มีสิทธิ์สำหรับบริษัทหรือลานนี้"); }
    private boolean access(Map<String,Object> u,Map<String,Object> site) {
        return superUser(u)||(u.get("tenantId").equals(site.get("tenantId"))&&(owner(u)||((List<?>)u.get("siteIds")).contains(site.get("id"))));
    }
    private Map<String,Object> site(Map<String,Object> u,String id) {
        var site=list(state,"sites").stream().filter(s->s.get("id").equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("ไม่พบลาน"));
        require(access(u,site)); return site;
    }
    private Map<String,Object> publicUser(Map<String,Object> u) {
        var result=new LinkedHashMap<>(u); result.remove("salt"); result.remove("hash"); return result;
    }
    public synchronized Map<String,Object> view(String userId) {
        return atomic(()->viewLocked(userId));
    }
    private Map<String,Object> viewLocked(String userId) {
        var u=user(userId);
        purgeExpiredHistory();
        String cutoff=ZonedDateTime.now(ZoneOffset.UTC).minusMonths(3).toInstant().toString();
        List<Map<String,Object>> sites=new ArrayList<>();
        for(var site:list(state,"sites")) if(access(u,site)) {
            var copy=map(Json.parse(SimpleJson.toJson(site)));
            copy.put("devices",list(site,"devices").stream().map(DeviceGateway::publicView).toList());
            list(copy,"tickets").removeIf(t->!t.get("status").equals("ACTIVE")&&t.get("exitTime").toString().compareTo(cutoff)<0);
            if(u.get("role").equals("staff")) {
                list(copy,"tickets").removeIf(t->!t.get("status").equals("ACTIVE"));
                for(String key:List.of("versions","draft","memberships","reservations","devices")) copy.put(key,List.of());
            }
            sites.add(copy);
        }
        return Map.of("revision",state.get("revision"),"user",publicUser(u),"sites",sites,
            "tenants",list(state,"tenants").stream().filter(t->superUser(u)||t.get("id").equals(u.get("tenantId"))).toList(),
            "users",owner(u)?list(state,"users").stream().filter(v->superUser(u)||v.get("tenantId").equals(u.get("tenantId"))).map(this::publicUser).toList():List.of(),
            "audit",owner(u)?list(state,"audit").stream().filter(a->superUser(u)||a.get("tenantId").equals(u.get("tenantId"))).toList():List.of(),
            "storage",store.description());
    }
    /** Retention runs on reads as well as writes; an unopened/offline server cannot run background jobs. */
    private void purgeExpiredHistory() {
        String before=SimpleJson.toJson(state), cutoff=ZonedDateTime.now(ZoneOffset.UTC).minusMonths(3).toInstant().toString();
        boolean changed=false;
        for(var site:list(state,"sites")) changed|=list(site,"tickets").removeIf(t->!t.get("status").equals("ACTIVE")&&t.get("exitTime").toString().compareTo(cutoff)<0);
        if(changed) try { state.put("revision",((Number)state.get("revision")).intValue()+1); persist(); }
        catch(Exception e) { state=map(Json.parse(before)); throw new IllegalStateException("History retention save failed",e); }
    }
    public synchronized void maintenance() { atomic(()->{purgeExpiredHistory();return null;}); }
    public synchronized Map<String,Object> deviceEvent(String token,Map<String,Object> request) {
        return atomic(()->{
            var s=list(state,"sites").stream().filter(v->v.get("id").equals(string(request,"siteId"))).findFirst().orElseThrow(()->new SecurityException("Device authentication failed"));
            var d=list(s,"devices").stream().filter(v->v.get("id").equals(string(request,"deviceId"))).findFirst().orElseThrow(()->new SecurityException("Device authentication failed"));
            var result=DeviceGateway.event(d,token,request); persist(); return result;
        });
    }
    private void addTenant(String id,String name,String plan) { list(state,"tenants").add(new LinkedHashMap<>(Map.of("id",id,"name",name,"plan",plan))); }
    private Map<String,Object> createSite(String tenant,String name,String type) {
        if(!BUSINESSES.contains(type)) throw new IllegalArgumentException("แม่แบบไม่ถูกต้อง");
        var cells=ParkingLayout.template(type); var site=new LinkedHashMap<String,Object>();
        site.put("id",id()); site.put("tenantId",tenant); site.put("name",name); site.put("businessType",type);
        site.put("address",""); site.put("active",true); site.put("rate",20); site.put("freeMinutes",15);
        site.put("draft",cells); site.put("published",new ArrayList<>()); site.put("versions",new ArrayList<>());
        site.put("tickets",new ArrayList<>()); site.put("memberships",new ArrayList<>()); site.put("reservations",new ArrayList<>()); site.put("devices",new ArrayList<>());
        site.put("features",new LinkedHashMap<>(Map.of("membership",true,"reservation",true)));
        list(state,"sites").add(site); return site;
    }
    private void seedHistory(Map<String,Object> site) {
        site.put("published",Json.parse(SimpleJson.toJson(site.get("draft"))));
        list(site,"versions").add(new LinkedHashMap<>(Map.of("id",id(),"at",now(),"cells",Json.parse(SimpleJson.toJson(site.get("draft"))))));
        list(site,"memberships").add(new LinkedHashMap<>(Map.of("id",id(),"name","สมาชิกตัวอย่าง", "plate","DEMO-MEMBER", "room","101", "expires",LocalDate.now().plusMonths(1).toString())));
        list(site,"devices").add(new LinkedHashMap<>(Map.of("id",id(),"name","ESP32 ทางเข้า (ตัวอย่าง)","type","ESP32","mode","SIMULATED","status","NOT_CONNECTED")));
        for(int i=1;i<=30;i++) {
            var t=new LinkedHashMap<String,Object>(); var entry=Instant.now().minusSeconds((i*3L-2)*86400);
            t.putAll(Map.of("ticketId","SAMPLE-"+i,"licensePlate","ตัวอย่าง-"+i,"vehicleType","CAR","floorNumber",1,"slotNumber","A-2","slotId","A2","entryTime",entry.toString(),"exitTime",entry.plusSeconds(3600).toString(),"status","EXITED","fee",20));
            t.put("sample",true); list(site,"tickets").add(t);
        }
    }
    /** Transaction method: all role checks, validation and persistence succeed or the old state is restored. */
    public synchronized Map<String,Object> command(String userId,Map<String,Object> request) throws Exception {
        return atomic(()->commandLocked(userId,request));
    }
    private Map<String,Object> commandLocked(String userId,Map<String,Object> request) throws Exception {
        String before=SimpleJson.toJson(state);
        try {
            var u=user(userId); String action=string(request,"action");
            if(integer(request,"revision",0,Integer.MAX_VALUE)!=((Number)state.get("revision")).intValue()) throw new ConcurrentModificationException("มีข้อมูลใหม่ กรุณารีเฟรชก่อนบันทึก");
            String tenant=u.get("tenantId").toString(), siteId="";
            String deviceSecret=null;
            if(action.equals("changePassword")) {
                login(u.get("username").toString(),password(request,"currentPassword"));
                setPassword(u,password(request,"newPassword"));
            } else if(Set.of("setUserActive","revokeSessions").contains(action)) {
                require(owner(u)); var target=user(string(request,"userId"));
                require(!target.get("id").equals(userId));
                require(superUser(u)||target.get("tenantId").equals(u.get("tenantId"))&&Set.of("admin","staff").contains(target.get("role")));
                require(!superUser(target)); tenant=target.get("tenantId").toString();
                if(action.equals("setUserActive")) {
                    if(!(request.get("active") instanceof Boolean)) throw new IllegalArgumentException("active must be boolean");
                    target.put("active",request.get("active"));
                }
                revoke(target);
            } else if(action.equals("createTenant")) {
                require(superUser(u)); tenant=id(); addTenant(tenant,string(request,"name"),"trial");
                addUser(string(request,"username"),string(request,"password"),"owner",tenant,List.of());
            } else if(action.equals("createSite")) {
                require(owner(u)); if(superUser(u)) tenant=string(request,"tenantId");
                final String target=tenant;
                if(list(state,"tenants").stream().noneMatch(t->t.get("id").equals(target))) throw new IllegalArgumentException("ไม่พบบริษัท");
                siteId=createSite(tenant,string(request,"name"),string(request,"businessType")).get("id").toString();
            } else if(action.equals("createUser")) {
                require(owner(u)); if(superUser(u)) tenant=string(request,"tenantId");
                String role=string(request,"role");
                require(Set.of("admin","staff").contains(role));
                Object raw=request.get("siteIds"); if(!(raw instanceof List<?> assigned)||assigned.isEmpty()) throw new IllegalArgumentException("เลือกลานให้ผู้ใช้");
                for(Object sid:assigned) { var s=site(u,sid.toString()); require(s.get("tenantId").equals(tenant)); }
                addUser(string(request,"username"),string(request,"password"),role,tenant,assigned);
            } else {
                siteId=string(request,"siteId"); var s=site(u,siteId); tenant=s.get("tenantId").toString();
                if(Set.of("saveLayout","publish","restore","configure","configurePolicy","addRoadCurve","removeRoadCurve").contains(action)) require(owner(u));
                else if(!Set.of("checkin","checkout","requestVisitor","valet").contains(action)) require(!u.get("role").equals("staff"));
                switch(action) {
                    case "addRoadCurve" -> {
                        var curves=collection(s,"roadCurves"); if(curves.size()>=100) throw new IllegalArgumentException("ไม่เกิน 100 เส้น");
                        var curve=new LinkedHashMap<String,Object>(Map.of("id",id(),"label",string(request,"label"),"floor",integer(request,"floor",1,8),"width",integer(request,"width",2,12)));
                        for(String key:List.of("x1","y1","cx","cy","x2","y2")) curve.put(key,integer(request,key,0,100));
                        if(curve.get("x1").equals(curve.get("x2"))&&curve.get("y1").equals(curve.get("y2"))) throw new IllegalArgumentException("จุดเริ่มและจุดจบต้องต่างกัน");
                        curves.add(curve);
                    }
                    case "removeRoadCurve" -> collection(s,"roadCurves").removeIf(c->c.get("id").equals(string(request,"curveId")));
                    case "provisionDevice", "disableDevice", "queueLed" -> {
                        require(owner(u));
                        if(!"true".equalsIgnoreCase(System.getenv("PLATFORM_DEVICE_GATEWAY"))) throw new IllegalArgumentException("เปิด gateway หลังตั้ง HTTPS ก่อน");
                        var device=list(s,"devices").stream().filter(d->d.get("id").equals(string(request,"deviceId"))).findFirst().orElseThrow();
                        if(action.equals("provisionDevice")) deviceSecret=DeviceGateway.provision(device);
                        else if(action.equals("queueLed")) DeviceGateway.queueLed(device);
                        else {device.remove("tokenHash");device.remove("lastSeen");device.remove("ledCommand");device.put("mode","DISABLED");}
                    }
                    case "configurePolicy" -> s.put("policy",PricingPolicy.validate(map(request.get("policy"))));
                    case "requestVisitor" -> {
                        var visitors=collection(s,"visitors");
                        Instant expires=Instant.parse(string(request,"expires"));
                        if(!expires.isAfter(Instant.now())||expires.isAfter(Instant.now().plusSeconds(7*86400))) throw new IllegalArgumentException("สิทธิ์ผู้มาติดต่อไม่เกิน 7 วัน");
                        visitors.add(new LinkedHashMap<>(Map.of("id",id(),"plate",string(request,"plate"),"room",string(request,"room"),"expires",expires.toString(),"status","PENDING","requestedBy",u.get("username"))));
                    }
                    case "approveVisitor" -> {
                        var visitor=collection(s,"visitors").stream().filter(v->v.get("id").equals(string(request,"visitorId"))).findFirst().orElseThrow();
                        if(!visitor.get("status").equals("PENDING")) throw new IllegalArgumentException("คำขอนี้ดำเนินการแล้ว");
                        visitor.put("status",Boolean.TRUE.equals(request.get("approved"))?"APPROVED":"REJECTED"); visitor.put("approvedBy",u.get("username"));
                    }
                    case "applyCoupon" -> {
                        require(s.get("businessType").equals("MALL"));
                        var ticket=list(s,"tickets").stream().filter(t->t.get("ticketId").equals(string(request,"ticketId"))&&t.get("status").equals("ACTIVE")).findFirst().orElseThrow();
                        String code=string(request,"code");
                        if(collection(s,"coupons").stream().anyMatch(c->c.get("code").equals(code))||ticket.containsKey("couponCode")) throw new IllegalArgumentException("คูปองหรือตั๋วนี้ใช้ส่วนลดแล้ว");
                        int percent=integer(request,"percent",1,100);
                        ticket.put("couponCode",code); ticket.put("couponDiscountPercent",percent);
                        collection(s,"coupons").add(new LinkedHashMap<>(Map.of("code",code,"ticketId",ticket.get("ticketId"),"percent",percent,"verifiedBy",u.get("username"),"at",now())));
                    }
                    case "valet" -> {
                        require(s.get("businessType").equals("HOTEL"));
                        var ticket=list(s,"tickets").stream().filter(t->t.get("ticketId").equals(string(request,"ticketId"))&&t.get("status").equals("ACTIVE")).findFirst().orElseThrow();
                        String stage=string(request,"stage"),previous=ticket.getOrDefault("valetStage","").toString();
                        if(!(previous.isEmpty()&&stage.equals("RECEIVED")||previous.equals("RECEIVED")&&stage.equals("PARKED")||previous.equals("PARKED")&&stage.equals("RETURNED"))) throw new IllegalArgumentException("ลำดับ Valet ไม่ถูกต้อง");
                        ticket.put("valetStage",stage); ticket.put("valetStaff",u.get("username")); ticket.put("valetAt",now());
                    }
                    case "saveLayout" -> s.put("draft",new ParkingLayout(list(request,"cells")).cells());
                    case "publish" -> publish(s);
                    case "restore" -> {
                        String version=string(request,"versionId");
                        var v=list(s,"versions").stream().filter(x->x.get("id").equals(version)).findFirst().orElseThrow(()->new IllegalArgumentException("ไม่พบเวอร์ชัน"));
                        s.put("draft",Json.parse(SimpleJson.toJson(v.get("cells"))));
                    }
                    case "configure" -> {
                        s.put("name",string(request,"name")); s.put("address",string(request,"address"));
                        s.put("rate",integer(request,"rate",0,10000)); s.put("freeMinutes",integer(request,"freeMinutes",0,1440));
                        s.put("active",Boolean.TRUE.equals(request.get("active")));
                        var features=map(request.get("features")); s.put("features",new LinkedHashMap<>(Map.of("membership",Boolean.TRUE.equals(features.get("membership")),"reservation",Boolean.TRUE.equals(features.get("reservation")))));
                    }
                    case "checkin" -> checkin(s,request);
                    case "checkout" -> checkout(s,request);
                    case "addMember" -> {
                        require(Boolean.TRUE.equals(map(s.get("features")).get("membership")));
                        var member=new LinkedHashMap<String,Object>(Map.of("id",id(),"plate",string(request,"plate"),"name",string(request,"name"),"room",string(request,"room"),"expires",LocalDate.parse(string(request,"expires")).toString()));
                        LocalDate starts=request.containsKey("starts")?LocalDate.parse(string(request,"starts")):LocalDate.now(ZoneId.of("Asia/Bangkok"));
                        if(LocalDate.parse(member.get("expires").toString()).isBefore(starts)) throw new IllegalArgumentException("วันสิ้นสุดต้องไม่น้อยกว่าวันเริ่ม");
                        member.put("starts",starts.toString());
                        list(s,"memberships").removeIf(m->m.get("plate").toString().equalsIgnoreCase(member.get("plate").toString())); list(s,"memberships").add(member);
                    }
                    case "reserve" -> {
                        require(Boolean.TRUE.equals(map(s.get("features")).get("reservation")));
                        String slotId=string(request,"slotId"),plate=string(request,"plate");
                        findSlot(s,slotId); Instant from=Instant.parse(string(request,"from")),to=Instant.parse(string(request,"to"));
                        if(!to.isAfter(from)||to.isBefore(Instant.now())) throw new IllegalArgumentException("ช่วงเวลาจองไม่ถูกต้อง");
                        if(list(s,"tickets").stream().anyMatch(t->t.get("status").equals("ACTIVE")&&t.get("slotId").equals(slotId))) throw new IllegalArgumentException("ช่องมีรถจอดอยู่");
                        if(list(s,"reservations").stream().anyMatch(r->r.get("slotId").equals(slotId)&&r.get("status").equals("BOOKED")&&Instant.parse(r.get("from").toString()).isBefore(to)&&Instant.parse(r.get("to").toString()).isAfter(from))) throw new IllegalArgumentException("เวลาจองซ้อนกัน");
                        list(s,"reservations").add(new LinkedHashMap<>(Map.of("id",id(),"slotId",slotId,"plate",plate,"from",from.toString(),"to",to.toString(),"status","BOOKED")));
                    }
                    case "cancelReservation" -> {
                        String rid=string(request,"reservationId"); var r=list(s,"reservations").stream().filter(v->v.get("id").equals(rid)).findFirst().orElseThrow(); r.put("status","CANCELLED");
                    }
                    case "addDevice" -> {
                        String type=string(request,"type"); if(!Set.of("CAMERA","ESP32","SENSOR","BARRIER").contains(type)) throw new IllegalArgumentException("ชนิดอุปกรณ์ไม่ถูกต้อง");
                        list(s,"devices").add(new LinkedHashMap<>(Map.of("id",id(),"name",string(request,"name"),"type",type,"mode","SIMULATED","status","NOT_CONNECTED")));
                    }
                    default -> throw new IllegalArgumentException("ไม่พบคำสั่ง");
                }
            }
            String cutoff=ZonedDateTime.now(ZoneOffset.UTC).minusMonths(3).toInstant().toString();
            for(var s:list(state,"sites")) list(s,"tickets").removeIf(t->!t.get("status").equals("ACTIVE")&&t.get("exitTime").toString().compareTo(cutoff)<0);
            list(state,"audit").add(new LinkedHashMap<>(Map.of("id",id(),"tenantId",tenant,"siteId",siteId,"actor",u.get("username"),"action",action,"at",now())));
            if(list(state,"audit").size()>5000) list(state,"audit").remove(0);
            state.put("revision",((Number)state.get("revision")).intValue()+1); persist();
            var result=new LinkedHashMap<>(view(userId)); if(deviceSecret!=null) result.put("deviceSecret",deviceSecret); return result;
        } catch(Exception ex) { state=map(Json.parse(before)); throw ex; }
    }
    private void publish(Map<String,Object> site) {
        var layout=new ParkingLayout(list(site,"draft")); var errors=layout.validate();
        if(!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ",errors.subList(0,Math.min(8,errors.size()))));
        Set<String> protectedSlots=new HashSet<>();
        for(var t:list(site,"tickets")) if(t.get("status").equals("ACTIVE")) protectedSlots.add(t.get("slotId").toString());
        for(var r:list(site,"reservations")) if(r.get("status").equals("BOOKED")&&Instant.parse(r.get("to").toString()).isAfter(Instant.now())) protectedSlots.add(r.get("slotId").toString());
        for(String slotId:protectedSlots) {
            var old=findSlot(site,slotId); var replacement=layout.cells().stream().filter(c->c.get("id").equals(slotId)).findFirst().orElse(null);
            if(replacement==null||!SimpleJson.toJson(new TreeMap<>(old)).equals(SimpleJson.toJson(new TreeMap<>(replacement)))) {
                // Numeric representations differ after JSON reload, compare canonically using field values instead.
                if(replacement==null||!sameCell(old,replacement)) throw new IllegalArgumentException("ห้ามลบหรือย้ายช่องที่มีรถ/การจอง: "+old.get("label"));
            }
        }
        site.put("published",Json.parse(SimpleJson.toJson(layout.cells())));
        list(site,"versions").add(new LinkedHashMap<>(Map.of("id",id(),"at",now(),"cells",Json.parse(SimpleJson.toJson(layout.cells())))));
        if(list(site,"versions").size()>20) list(site,"versions").remove(0);
    }
    private boolean sameCell(Map<String,Object>a,Map<String,Object>b) {
        for(String key:List.of("id","type","label","slotType")) if(!Objects.equals(a.get(key),b.get(key))) return false;
        for(String key:List.of("x","y","floor","rotation")) if(((Number)a.get(key)).intValue()!=((Number)b.get(key)).intValue()) return false;
        return true;
    }
    private Map<String,Object> findSlot(Map<String,Object>s,String id) {
        return list(s,"published").stream().filter(c->c.get("id").equals(id)&&c.get("type").equals("SLOT")).findFirst().orElseThrow(()->new IllegalArgumentException("ช่องนี้ยังไม่ได้เผยแพร่"));
    }
    private void checkin(Map<String,Object>s,Map<String,Object>request) {
        require(Boolean.TRUE.equals(s.get("active")));
        String plate=string(request,"plate"),slotId=string(request,"slotId"),vehicleType=string(request,"vehicleType"); var slot=findSlot(s,slotId);
        if(!Set.of("CAR","ELECTRIC_VEHICLE","MOTORCYCLE","TRUCK").contains(vehicleType)) throw new IllegalArgumentException("ประเภทรถไม่ถูกต้อง");
        String slotType=slot.get("slotType").toString();
        if((slotType.equals("MOTORCYCLE")&&!vehicleType.equals("MOTORCYCLE"))||(vehicleType.equals("MOTORCYCLE")&&!slotType.equals("MOTORCYCLE"))||(vehicleType.equals("TRUCK")&&!slotType.equals("LARGE"))||(slotType.equals("EV_CHARGING")&&!vehicleType.equals("ELECTRIC_VEHICLE"))) throw new IllegalArgumentException("ประเภทรถไม่ตรงกับช่อง");
        if(list(s,"tickets").stream().anyMatch(t->t.get("status").equals("ACTIVE")&&(t.get("licensePlate").toString().equalsIgnoreCase(plate)||t.get("slotId").equals(slotId)))) throw new IllegalArgumentException("ทะเบียนหรือช่องจอดกำลังใช้งาน");
        Instant now=Instant.now();
        LocalDate today=now.atZone(ZoneId.of("Asia/Bangkok")).toLocalDate();
        var member=Boolean.TRUE.equals(map(s.get("features")).get("membership"))?list(s,"memberships").stream().filter(m->m.get("plate").toString().equalsIgnoreCase(plate)&&!LocalDate.parse(m.get("expires").toString()).isBefore(today)&&!LocalDate.parse(m.getOrDefault("starts","1970-01-01").toString()).isAfter(today)).findFirst().orElse(null):null;
        var policy=s.get("policy") instanceof Map?map(s.get("policy")):Map.<String,Object>of();
        long quota=PricingPolicy.number(policy,"roomQuota",0);
        if(member!=null&&quota>0&&list(s,"tickets").stream().filter(t->t.get("status").equals("ACTIVE")&&Objects.equals(t.get("memberRoom"),member.get("room"))).count()>=quota) throw new IllegalArgumentException("จำนวนรถในห้อง/หน่วยงานครบโควตาแล้ว");
        if(member==null&&Boolean.TRUE.equals(policy.get("requireVisitorApproval"))) {
            var visitor=collection(s,"visitors").stream().filter(v->v.get("status").equals("APPROVED")&&v.get("plate").toString().equalsIgnoreCase(plate)&&Instant.parse(v.get("expires").toString()).isAfter(now)).findFirst().orElseThrow(()->new IllegalArgumentException("ต้องอนุมัติผู้มาติดต่อก่อนรับรถ"));
            visitor.put("status","USED");
        }
        if(list(s,"reservations").stream().anyMatch(r->r.get("status").equals("BOOKED")&&r.get("slotId").equals(slotId)&&Instant.parse(r.get("to").toString()).isAfter(now)&&!r.get("plate").equals(plate))) throw new IllegalArgumentException("ช่องนี้มีการจอง กรุณาเลือกช่องอื่น");
        for(var r:list(s,"reservations")) if(r.get("slotId").equals(slotId)&&r.get("plate").equals(plate)&&r.get("status").equals("BOOKED")) r.put("status","ARRIVED");
        var t=new LinkedHashMap<String,Object>(); t.putAll(Map.of("ticketId",id(),"licensePlate",plate,"vehicleType",vehicleType,"slotId",slotId,"slotNumber",slot.get("label"),"floorNumber",slot.get("floor"),"entryTime",now.toString(),"status","ACTIVE","fee",0));
        t.put("rate",s.get("rate")); t.put("freeMinutes",s.get("freeMinutes")); t.put("sample",false);
        PricingPolicy.snapshot(s,t,member,now); list(s,"tickets").add(t);
    }
    private void checkout(Map<String,Object>s,Map<String,Object>request) {
        String tid=string(request,"ticketId"); var t=list(s,"tickets").stream().filter(v->v.get("ticketId").equals(tid)).findFirst().orElseThrow(()->new IllegalArgumentException("ไม่พบตั๋ว"));
        if(!t.get("status").equals("ACTIVE")) throw new IllegalArgumentException("ตั๋วนี้ออกแล้ว");
        if(t.containsKey("valetStage")&&!t.get("valetStage").equals("RETURNED")) throw new IllegalArgumentException("ยืนยันส่งคืนรถ Valet ก่อนนำรถออก");
        long fee=PricingPolicy.fee(t,Instant.now());
        t.put("fee",fee); t.put("status","EXITED"); t.put("exitTime",now()); t.put("paymentMethod","MANUAL_CASH");
    }
    private List<Map<String,Object>> collection(Map<String,Object> site,String name) {
        site.putIfAbsent(name,new ArrayList<>()); return list(site,name);
    }
}
