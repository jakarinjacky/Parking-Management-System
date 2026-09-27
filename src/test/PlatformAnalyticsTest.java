package test;
import platform.*;
import java.time.*;
import java.util.*;
import static platform.PlatformService.*;

public class PlatformAnalyticsTest {
    static class Store implements PlatformStore {
        String json; public String load(){return json;} public void save(String s){json=s;} public String description(){return "test";}
    }
    static void check(boolean condition){if(!condition)throw new AssertionError("Analytics assertion failed");}
    static Map<String,Object> window(PlatformService service,String user) {return map(map(map(service.view(user).get("platformAnalytics")).get("windows")).get("30"));}
    static Map<String,Object> cmd(PlatformService s,String user,String action,Map<String,Object> args)throws Exception {
        var r=new LinkedHashMap<>(args);r.put("action",action);r.put("revision",s.view(user).get("revision"));return s.command(user,r);
    }
    public static void main(String[] args)throws Exception {
        Store store=new Store(); var s=new PlatformService(store,false,"Administrator123!");String root=s.login("superadmin","Administrator123!");
        cmd(s,root,"createTenant",Map.of("name","Customer","username","customer","password","CustomerPass123!"));
        String owner=s.login("customer","CustomerPass123!");
        check(!s.view(owner).containsKey("platformAnalytics"));
        var w=window(s,root);check(((Number)w.get("newCustomers")).intValue()==1);check(((Number)w.get("activeCustomers")).intValue()==1);
        cmd(s,owner,"createSite",Map.of("name","Real lot","businessType","PUBLIC"));
        var row=list(window(s,root),"customers").get(0);check(((Number)row.get("logins")).intValue()==1);check(((Number)row.get("actions")).intValue()==1);
        try{s.login("customer","wrong");throw new AssertionError();}catch(SecurityException expected){}
        try{cmd(s,owner,"createTenant",Map.of("name","No","username","denied","password","CustomerPass123!"));throw new AssertionError();}catch(SecurityException expected){}
        check(((Number)list(window(s,root),"customers").get(0).get("actions")).intValue()==1);
        s=new PlatformService(store,false,null);check(((Number)list(window(s,root),"customers").get(0).get("logins")).intValue()==1);
        var raw=map(Json.parse(store.json));var t=list(raw,"tenants").get(0);t.put("createdAt",Instant.now().minusSeconds(60L*86400).toString());
        var old=new LinkedHashMap<String,Object>(Map.of("day",LocalDate.now(ZoneId.of("Asia/Bangkok")).minusDays(100).toString(),"tenantId",t.get("id"),"logins",100,"actions",100));list(raw,"usageDaily").add(old);
        PlatformAnalytics.record(raw,list(raw,"users").get(1),"","logins");check(list(raw,"usageDaily").size()==1);
        var report=map(map(PlatformAnalytics.report(raw).get("windows")).get("30"));check(((Number)report.get("returningCustomers")).intValue()==1);
        t.remove("createdAt");raw.put("audit",new ArrayList<>());PlatformAnalytics.initialize(raw);report=map(map(PlatformAnalytics.report(raw).get("windows")).get("30"));check(((Number)report.get("unknownCreated")).intValue()==1);
        t.put("sample",true);check(list(map(map(PlatformAnalytics.report(raw).get("windows")).get("30")),"customers").isEmpty());
        System.out.println("Analytics PASS: permissions, cohorts, success-only counters, restart, retention, legacy dates and samples");
    }
}
