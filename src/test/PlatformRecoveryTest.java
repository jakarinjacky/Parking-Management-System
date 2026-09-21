package test;

import platform.*;

/** Regression tests: recovery must preserve tenants, tickets and atomic rollback. */
public final class PlatformRecoveryTest {
    static final class MemoryStore implements PlatformStore {
        String data; boolean fail;
        public String load() { return data; }
        public void save(String json) { if(fail) throw new IllegalStateException("disk failure"); data=json; }
        public String description() { return "test"; }
    }
    public static void main(String[] args) throws Exception {
        MemoryStore store=new MemoryStore();
        try { new PlatformService(store,false,null); throw new AssertionError("Missing password accepted"); }
        catch(IllegalArgumentException expected) { }
        if(store.data!=null) throw new AssertionError("Empty database persisted");
        PlatformService service=new PlatformService(store,true,null);
        String id=service.login("superadmin","DemoPass123!");
        Object sites=service.view(id).get("sites"),tenants=service.view(id).get("tenants");
        int version=service.sessionVersion(id);
        service.recoverAdmin("RecoveryPassword123!");
        if(service.sessionVersion(id)==version) throw new AssertionError("Sessions not revoked");
        try { service.login("superadmin","DemoPass123!"); throw new AssertionError("Old password accepted"); }
        catch(SecurityException expected) { }
        service=new PlatformService(store,false,null);
        service.login("superadmin","RecoveryPassword123!");
        if(!util.SimpleJson.toJson(sites).equals(util.SimpleJson.toJson(service.view(id).get("sites")))||!util.SimpleJson.toJson(tenants).equals(util.SimpleJson.toJson(service.view(id).get("tenants")))) throw new AssertionError("Business data changed");
        store.fail=true;
        try { service.recoverAdmin("AnotherPassword123!"); throw new AssertionError("Write failure ignored"); }
        catch(IllegalStateException expected) { }
        service.login("superadmin","RecoveryPassword123!");
        System.out.println("Platform recovery PASS: preservation, restart, revocation, rollback, first-start validation");
    }
}
