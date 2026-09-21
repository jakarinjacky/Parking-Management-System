package platform;

import java.time.*;
import java.util.*;

/** Integer-baht pricing, snapshotted on entry; cap applies per rolling 24 billable hours. */
public final class PricingPolicy {
    private PricingPolicy() {}
    public static long fee(Map<String,Object> ticket,Instant at) {
        long seconds=Math.max(0,Duration.between(Instant.parse(ticket.get("entryTime").toString()),at).getSeconds());
        long billable=Math.max(0,seconds-number(ticket,"freeMinutes",0)*60);
        long hours=(billable+3599)/3600,rate=number(ticket,"rate",0),cap=number(ticket,"dailyCap",0);
        long gross=cap>0?(hours/24)*Math.min(24*rate,cap)+Math.min((hours%24)*rate,cap):hours*rate;
        long discount=Math.max(number(ticket,"memberDiscountPercent",0),number(ticket,"couponDiscountPercent",0));
        return (gross*(100-discount)+99)/100;
    }
    public static long number(Map<String,Object> map,String key,long fallback) { return ((Number)map.getOrDefault(key,fallback)).longValue(); }
    public static Map<String,Object> validate(Map<String,Object> input) {
        var out=new LinkedHashMap<String,Object>();
        for(String key:List.of("carRate","evRate","motorcycleRate","truckRate","weekendRate","holidayRate")) out.put(key,PlatformService.integer(input,key,-1,10000));
        out.put("dailyCap",PlatformService.integer(input,"dailyCap",0,100000));
        out.put("memberDiscountPercent",PlatformService.integer(input,"memberDiscountPercent",0,100));
        out.put("roomQuota",PlatformService.integer(input,"roomQuota",0,100));
        if(!(input.get("requireVisitorApproval") instanceof Boolean)) throw new IllegalArgumentException("requireVisitorApproval must be boolean");
        out.put("requireVisitorApproval",input.get("requireVisitorApproval"));
        if(!(input.get("holidays") instanceof List<?> holidays)||holidays.size()>366) throw new IllegalArgumentException("Holiday dates required");
        out.put("holidays",holidays.stream().map(v->LocalDate.parse(v.toString()).toString()).distinct().toList());
        return out;
    }
    public static void snapshot(Map<String,Object> site,Map<String,Object> ticket,Map<String,Object> member,Instant at) {
        var policy=site.get("policy") instanceof Map?PlatformService.map(site.get("policy")):Map.<String,Object>of();
        String key=switch(ticket.get("vehicleType").toString()) {case "MOTORCYCLE"->"motorcycleRate";case "TRUCK"->"truckRate";case "ELECTRIC_VEHICLE"->"evRate";default->"carRate";};
        long rate=number(policy,key,-1); if(rate<0) rate=number(site,"rate",0);
        LocalDate date=at.atZone(ZoneId.of("Asia/Bangkok")).toLocalDate();
        if(date.getDayOfWeek().getValue()>=6&&number(policy,"weekendRate",-1)>=0) rate=number(policy,"weekendRate",-1);
        if(((List<?>)policy.getOrDefault("holidays",List.of())).contains(date.toString())&&number(policy,"holidayRate",-1)>=0) rate=number(policy,"holidayRate",-1);
        ticket.put("rate",rate); ticket.put("dailyCap",number(policy,"dailyCap",0));
        ticket.put("memberDiscountPercent",member==null?0:number(policy,"memberDiscountPercent",0));
        ticket.put("memberRoom",member==null?"":member.get("room"));
    }
}
