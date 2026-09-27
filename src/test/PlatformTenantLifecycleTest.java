package test;
import platform.*;
import java.util.*;
import static platform.PlatformService.*;
import static test.PlatformAnalyticsTest.*;

/** Customer lifecycle must revoke sessions without touching another company. */
public class PlatformTenantLifecycleTest {
    interface Attempt {void run()throws Exception;}
    static void denied(Attempt attempt)throws Exception {try{attempt.run();throw new AssertionError("Accepted forbidden operation");}catch(SecurityException|IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception {
        Store store=new Store();var s=new PlatformService(store,false,"Administrator123!");String root=s.login("superadmin","Administrator123!");
        for(String name:List.of("customer","other"))cmd(s,root,"createTenant",Map.of("name",name,"username",name,"password","CustomerPass123!"));
        String owner=s.login("customer","CustomerPass123!"),other=s.login("other","CustomerPass123!");
        String tenant=map(s.view(owner).get("user")).get("tenantId").toString();
        cmd(s,owner,"createSite",Map.of("name","Lot","businessType","PUBLIC"));
        String site=list(s.view(owner),"sites").get(0).get("id").toString();
        cmd(s,owner,"createUser",Map.of("username","employee","password","EmployeePass123!","role","staff","siteIds",List.of(site)));
        String staff=s.login("employee","EmployeePass123!");int version=s.sessionVersion(staff);
        cmd(s,owner,"addDevice",Map.of("siteId",site,"name","LED","type","ESP32"));
        String device=list(list(s.view(owner),"sites").get(0),"devices").get(0).get("id").toString();
        var fixture=map(Json.parse(store.json));
        String token=DeviceGateway.provision(list(list(fixture,"sites").get(0),"devices").get(0));store.save(util.SimpleJson.toJson(fixture));
        denied(()->cmd(s,owner,"setTenantActive",Map.of("tenantId",tenant,"active",false)));
        denied(()->cmd(s,root,"deleteTenant",Map.of("tenantId",tenant,"confirmationName","customer","currentPassword","Administrator123!")));
        cmd(s,root,"setTenantActive",Map.of("tenantId",tenant,"active",false));
        denied(()->s.login("customer","CustomerPass123!"));denied(()->s.view(owner));denied(()->s.sessionVersion(staff));
        denied(()->s.deviceEvent(token,Map.of("siteId",site,"deviceId",device,"type","heartbeat")));
        check(!list(s.view(other),"tenants").isEmpty());
        cmd(s,root,"setTenantActive",Map.of("tenantId",tenant,"active",true));
        check(s.sessionVersion(staff)>version);check(list(s.view(owner),"sites").size()==1);s.login("customer","CustomerPass123!");
        cmd(s,root,"setTenantActive",Map.of("tenantId",tenant,"active",false));
        denied(()->cmd(s,root,"deleteTenant",Map.of("tenantId",tenant,"confirmationName","WRONG","currentPassword","Administrator123!")));
        denied(()->cmd(s,root,"deleteTenant",Map.of("tenantId",tenant,"confirmationName","customer","currentPassword","wrong")));
        check(list(s.view(root),"tenants").size()==2);
        cmd(s,root,"deleteTenant",Map.of("tenantId",tenant,"confirmationName","customer","currentPassword","Administrator123!"));
        check(list(s.view(root),"tenants").size()==1);check(list(s.view(root),"sites").isEmpty());
        denied(()->s.login("customer","CustomerPass123!"));denied(()->s.sessionVersion(staff));
        var restarted=new PlatformService(store,false,null);check(list(restarted.view(root),"tenants").size()==1);check(!list(restarted.view(other),"tenants").isEmpty());
        System.out.println("Tenant lifecycle PASS: roles, suspend, session revocation, devices, resume, delete confirmations, isolation and restart");
    }
}
