package platform;

import java.time.*;
import java.util.*;
import static platform.PlatformService.*;

/** Daily counters use real Bangkok calendar dates, independent of demo parking clocks. */
public final class PlatformAnalytics {
    private static final ZoneId ZONE=ZoneId.of("Asia/Bangkok");
    private PlatformAnalytics() {}
    public static void initialize(Map<String,Object> state) {
        state.putIfAbsent("usageStartedAt",Instant.now().toString());
        state.putIfAbsent("usageDaily",new ArrayList<>());
        for(var tenant:list(state,"tenants")) if(!tenant.containsKey("createdAt")) {
            list(state,"audit").stream().filter(a->"createTenant".equals(a.get("action")) && tenant.get("id").equals(a.get("tenantId")))
                .map(a->a.get("at").toString()).min(String::compareTo).ifPresent(at->tenant.put("createdAt",at));
        }
    }
    public static void record(Map<String,Object> state,Map<String,Object> user,String siteId,String kind) {
        initialize(state);
        if("super_admin".equals(user.get("role"))) return;
        var tenant=list(state,"tenants").stream().filter(t->t.get("id").equals(user.get("tenantId"))).findFirst().orElse(null);
        if(tenant==null || Boolean.TRUE.equals(tenant.get("sample"))) return;
        if(list(state,"sites").stream().anyMatch(s->s.get("id").equals(siteId)&&Boolean.TRUE.equals(s.get("sample")))) return;
        String day=LocalDate.now(ZONE).toString();
        var days=list(state,"usageDaily");
        days.removeIf(d->d.get("day").toString().compareTo(LocalDate.now(ZONE).minusDays(89).toString())<0);
        var row=days.stream().filter(d->day.equals(d.get("day"))&&tenant.get("id").equals(d.get("tenantId"))).findFirst().orElse(null);
        if(row==null) { row=new LinkedHashMap<>(Map.of("day",day,"tenantId",tenant.get("id"),"logins",0,"actions",0)); days.add(row); }
        row.put(kind,((Number)row.get(kind)).longValue()+1);
        tenant.put("lastActiveAt",Instant.now().toString());
    }
    public static Map<String,Object> report(Map<String,Object> state) {
        var windows=new LinkedHashMap<String,Object>();
        for(int count:new int[]{7,30,90}) {
            LocalDate start=LocalDate.now(ZONE).minusDays(count-1);
            var customers=new ArrayList<Map<String,Object>>();
            var trend=new LinkedHashMap<String,Map<String,Object>>();
            for(int i=0;i<count;i++) {String day=start.plusDays(i).toString(); trend.put(day,new LinkedHashMap<>(Map.of("day",day,"logins",0L,"actions",0L,"active",0L,"newCustomers",0L)));}
            long fresh=0,unknown=0,active=0,returning=0;
            for(var tenant:list(state,"tenants")) {
                if(Boolean.TRUE.equals(tenant.get("sample"))) continue;
                String created=Objects.toString(tenant.get("createdAt"),"");
                String createdDay=created.isEmpty()?"":Instant.parse(created).atZone(ZONE).toLocalDate().toString();
                boolean isNew=trend.containsKey(createdDay);
                if(isNew) {fresh++; increment(trend.get(createdDay),"newCustomers",1);} else if(created.isEmpty()) unknown++;
                long logins=0,actions=0,days=0;
                for(var row:list(state,"usageDaily")) if(tenant.get("id").equals(row.get("tenantId")) && trend.containsKey(row.get("day"))) {
                    long l=((Number)row.get("logins")).longValue(),a=((Number)row.get("actions")).longValue();
                    logins+=l; actions+=a; if(l+a>0)days++;
                    var d=trend.get(row.get("day"));increment(d,"logins",l);increment(d,"actions",a);if(l+a>0)increment(d,"active",1);
                }
                if(days>0) {active++; if(!isNew&&!created.isEmpty()) returning++;}
                var c=new LinkedHashMap<String,Object>();
                c.put("id",tenant.get("id"));c.put("name",tenant.get("name"));c.put("createdAt",created);
                c.put("cohort",isNew?"new":created.isEmpty()?"unknown":"existing");
                c.put("lastActiveAt",tenant.getOrDefault("lastActiveAt",""));c.put("logins",logins);c.put("actions",actions);c.put("activeDays",days);
                c.put("sites",list(state,"sites").stream().filter(s->tenant.get("id").equals(s.get("tenantId"))&&!Boolean.TRUE.equals(s.get("sample"))).count());
                customers.add(c);
            }
            customers.sort(Comparator.<Map<String,Object>>comparingLong(c->((Number)c.get("actions")).longValue()+((Number)c.get("logins")).longValue()).reversed());
            windows.put(Integer.toString(count),Map.of("customers",customers,"trend",new ArrayList<>(trend.values()),"newCustomers",fresh,"unknownCreated",unknown,"activeCustomers",active,"returningCustomers",returning,"inactiveCustomers",customers.size()-active));
        }
        return Map.of("windows",windows,"startedAt",state.get("usageStartedAt"),"generatedAt",Instant.now().toString());
    }
    private static void increment(Map<String,Object> row,String key,long n) {row.put(key,((Number)row.get(key)).longValue()+n);}
}
