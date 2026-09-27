package platform;

import java.time.*;
import java.util.*;
import static platform.PlatformService.*;

/** Subscription ledger, separate from parking payments. Only verified manual payments become PAID. */
public final class PlatformBilling {
    public static final Set<String> ACTIONS=Set.of("configureBilling","issuePlatformInvoice","reportPlatformTransfer","confirmPlatformPayment","voidPlatformInvoice");
    public static final Map<String,Integer> PRICES=Map.of("STARTER",499,"BUSINESS",990,"MULTI_SITE",1990);
    private PlatformBilling() {}
    public static void initialize(Map<String,Object> state) {
        state.putIfAbsent("platformInvoices",new ArrayList<>());
        state.putIfAbsent("billingSettings",new LinkedHashMap<>(Map.of("paymentInstructions","")));
    }
    private static void require(boolean allowed) {if(!allowed)throw new SecurityException("ไม่มีสิทธิ์จัดการบิลค่าแพลตฟอร์ม");}
    private static LocalDate date(Map<String,Object> req,String key) {
        try{return LocalDate.parse(string(req,key));}catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("วันที่ไม่ถูกต้อง: "+key);}
    }
    public static String execute(Map<String,Object> state,Map<String,Object> user,String action,Map<String,Object> req) {
        boolean operator="super_admin".equals(user.get("role"));
        String tenant=Objects.toString(user.get("tenantId"),""),now=Instant.now().toString();
        if(action.equals("configureBilling")) {
            require(operator);Object value=req.get("paymentInstructions");
            if(!(value instanceof String text)||text.isBlank()||text.length()>2000)throw new IllegalArgumentException("กรอกข้อมูลรับโอน 1–2000 ตัวอักษร");
            map(state.get("billingSettings")).put("paymentInstructions",text.trim());return "";
        }
        if(action.equals("issuePlatformInvoice")) {
            require(operator);tenant=string(req,"tenantId");final String tid=tenant;
            var customer=list(state,"tenants").stream().filter(t->tid.equals(t.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("ไม่พบบริษัท"));
            if(Boolean.TRUE.equals(customer.get("sample")))throw new IllegalArgumentException("ไม่ออกบิลให้บริษัทตัวอย่าง");
            String plan=string(req,"plan");if(!PRICES.containsKey(plan))throw new IllegalArgumentException("แพ็กเกจไม่ถูกต้อง");
            String instructions=map(state.get("billingSettings")).get("paymentInstructions").toString();
            if(instructions.isBlank())throw new IllegalArgumentException("ตั้งค่าข้อมูลรับโอนก่อนออกบิล");
            LocalDate start=date(req,"periodStart"),end=start.plusMonths(1),due=date(req,"dueDate");
            if(start.getYear()<2000||end.getYear()>2100)throw new IllegalArgumentException("ปีของรอบบริการต้องอยู่ระหว่าง 2000–2099");
            if(due.isBefore(start)||!due.isBefore(end))throw new IllegalArgumentException("วันครบกำหนดต้องอยู่ในรอบบริการ");
            for(var inv:list(state,"platformInvoices")) if(tid.equals(inv.get("tenantId"))&&!"VOID".equals(inv.get("status"))&&start.isBefore(LocalDate.parse(inv.get("periodEnd").toString()))&&end.isAfter(LocalDate.parse(inv.get("periodStart").toString())))throw new IllegalArgumentException("มีบิลที่ทับซ้อนรอบบริการนี้แล้ว");
            int seq=((Number)state.getOrDefault("platformInvoiceSequence",0)).intValue()+1;state.put("platformInvoiceSequence",seq);
            var inv=new LinkedHashMap<String,Object>();
            inv.put("id",UUID.randomUUID().toString());inv.put("number",String.format(java.util.Locale.ROOT,"GP-%06d",seq));
            inv.put("tenantId",tenant);inv.put("tenantName",customer.get("name"));inv.put("plan",plan);inv.put("amount",PRICES.get(plan));inv.put("currency","THB");
            inv.put("periodStart",start.toString());inv.put("periodEnd",end.toString());inv.put("dueDate",due.toString());inv.put("createdAt",now);inv.put("status","ISSUED");inv.put("paymentInstructions",instructions);
            list(state,"platformInvoices").add(inv);customer.put("plan",plan);return tenant;
        }
        String invoiceId=string(req,"invoiceId");
        var inv=list(state,"platformInvoices").stream().filter(i->invoiceId.equals(i.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("ไม่พบบิล"));
        require(operator||"owner".equals(user.get("role"))&&tenant.equals(inv.get("tenantId")));tenant=inv.get("tenantId").toString();
        if(!Set.of("ISSUED","REPORTED").contains(inv.get("status")))throw new IllegalArgumentException("บิลนี้ชำระแล้วหรือยกเลิกแล้ว");
        if(action.equals("reportPlatformTransfer")) {
            require(!operator&&"owner".equals(user.get("role")));
            inv.put("transferReference",string(req,"reference"));inv.put("reportedAt",now);inv.put("status","REPORTED");
        } else if(action.equals("confirmPlatformPayment")) {
            require(operator);require(Boolean.TRUE.equals(req.get("confirmed")));
            String reference=string(req,"reference");
            if(list(state,"platformInvoices").stream().anyMatch(i->"PAID".equals(i.get("status"))&&reference.equals(i.get("paymentReference"))))throw new IllegalArgumentException("เลขอ้างอิงรับเงินนี้ใช้กับบิลอื่นแล้ว");
            inv.put("paymentReference",reference);inv.put("paidAt",now);inv.put("confirmedBy",user.get("username"));inv.put("status","PAID");
        } else if(action.equals("voidPlatformInvoice")) {
            require(operator);inv.put("voidReason",string(req,"reason"));inv.put("voidedAt",now);inv.put("status","VOID");
        } else throw new IllegalArgumentException("คำสั่งบิลไม่ถูกต้อง");
        return tenant;
    }
    public static Map<String,Object> view(Map<String,Object> state,Map<String,Object> user) {
        boolean operator="super_admin".equals(user.get("role"));
        require(operator||"owner".equals(user.get("role")));
        var invoices=list(state,"platformInvoices").stream().filter(i->operator||user.get("tenantId").equals(i.get("tenantId"))).toList();
        return Map.of("invoices",invoices,"plans",PRICES,"paymentInstructions",map(state.get("billingSettings")).get("paymentInstructions"),"today",LocalDate.now(ZoneId.of("Asia/Bangkok")).toString());
    }
}
