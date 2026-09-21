package test;

import platform.*;
import java.util.*;
import java.time.*;

public final class PlatformBusinessTest {
    static final class Store implements PlatformStore {
        String data;
        public String load(){return data;}
        public void save(String value){data=value;}
        public String description(){return "test";}
    }
    static Map<String,Object> command(PlatformService s,String user,String action,Map<String,Object> fields) throws Exception {
        var request=new LinkedHashMap<String,Object>(fields);request.put("action",action);request.put("revision",s.view(user).get("revision"));return s.command(user,request);
    }
    static void expectFailure(Runnable test) {try {test.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|SecurityException expected){}}
    public static void main(String[] args) throws Exception {
        Store store=new Store();PlatformService s=new PlatformService(store,true,null);
        String owner=s.login("owner","DemoPass123!");
        var site=PlatformService.list(s.view(owner),"sites").get(0);String sid=site.get("id").toString();
        var p=new LinkedHashMap<String,Object>();
        for(String key:List.of("carRate","evRate","motorcycleRate","truckRate","weekendRate","holidayRate"))p.put(key,-1);
        p.putAll(Map.of("dailyCap",100,"memberDiscountPercent",50,"roomQuota",1,"requireVisitorApproval",true,"holidays",List.of()));
        command(s,owner,"configurePolicy",Map.of("siteId",sid,"policy",p));
        command(s,owner,"addMember",Map.of("siteId",sid,"plate","TEST-MEMBER","name","Test","room","101","expires","2030-12-31"));
        command(s,owner,"checkin",Map.of("siteId",sid,"plate","TEST-MEMBER","slotId","A4","vehicleType","CAR"));
        command(s,owner,"addMember",Map.of("siteId",sid,"plate","TEST-MEMBER-2","name","Test2","room","101","expires","2030-12-31"));
        try{command(s,owner,"checkin",Map.of("siteId",sid,"plate","TEST-MEMBER-2","slotId","A6","vehicleType","CAR"));throw new AssertionError("quota");}catch(IllegalArgumentException expected){}
        var t=new LinkedHashMap<String,Object>(Map.of("entryTime","2026-01-01T00:00:00Z","rate",20,"freeMinutes",15,"dailyCap",100,"memberDiscountPercent",50));
        if(PricingPolicy.fee(t,Instant.parse("2026-01-01T00:15:00Z"))!=0||PricingPolicy.fee(t,Instant.parse("2026-01-01T00:15:01Z"))!=10||PricingPolicy.fee(t,Instant.parse("2026-01-02T00:15:00Z"))!=50)throw new AssertionError("pricing boundary");
        p.put("memberDiscountPercent",101);expectFailure(()->PricingPolicy.validate(p));
        var device=new LinkedHashMap<String,Object>(Map.of("id","test","type","ESP32"));String token=DeviceGateway.provision(device);
        expectFailure(()->DeviceGateway.event(device,"bad",Map.of("type","HEARTBEAT")));
        DeviceGateway.event(device,token,Map.of("type","HEARTBEAT"));
        if(!DeviceGateway.publicView(device).get("status").equals("ONLINE")||DeviceGateway.publicView(device).containsKey("tokenHash"))throw new AssertionError("device privacy/status");
        DeviceGateway.queueLed(device);String commandId=PlatformService.map(device.get("ledCommand")).get("id").toString();
        expectFailure(()->DeviceGateway.queueLed(device));
        DeviceGateway.event(device,token,Map.of("type","ACK","commandId",commandId));
        DeviceGateway.event(device,token,Map.of("type","ACK","commandId",commandId));
        expectFailure(()->DeviceGateway.event(device,token,Map.of("type","OPEN_BARRIER")));
        String replacement=DeviceGateway.provision(device);expectFailure(()->DeviceGateway.event(device,token,Map.of("type","HEARTBEAT")));
        DeviceGateway.event(device,replacement,Map.of("type","HEARTBEAT"));
        System.out.println("Business PASS: member quota, pricing boundaries, device auth/rotation, ACK retries, no barrier commands");
    }
}
