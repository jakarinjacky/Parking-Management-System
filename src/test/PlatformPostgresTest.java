package test;

import platform.*;
import java.util.*;
import java.util.concurrent.*;

/** Runs only against an explicitly named disposable CI database. */
public final class PlatformPostgresTest {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("TEST_DATABASE_URL");
        if(url==null||!url.endsWith("/parking_ci")) throw new IllegalArgumentException("Use disposable parking_ci database");
        String user=System.getenv("TEST_DB_USER"),password=System.getenv("TEST_DB_PASSWORD");
        var storeA=new PostgresPlatformStore(url,user,password);
        if(storeA.load()!=null) throw new IllegalArgumentException("CI database must be empty");
        var a=new PlatformService(storeA,true,null);
        var b=new PlatformService(new PostgresPlatformStore(url,user,password),false,null);
        String owner=a.login("owner","DemoPass123!");var view=a.view(owner);
        String site=PlatformService.list(view,"sites").get(0).get("id").toString();
        var request=new LinkedHashMap<String,Object>(Map.of("action","checkin","revision",view.get("revision"),"siteId",site,"slotId","A4","plate","CONCURRENT","vehicleType","CAR"));
        ExecutorService workers=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        List<Future<Boolean>> results=new ArrayList<>();
        for(var service:List.of(a,b)) results.add(workers.submit(()->{ready.countDown();go.await();try{service.command(owner,request);return true;}catch(ConcurrentModificationException expected){return false;}}));
        ready.await();go.countDown();int successes=0;for(var result:results)if(result.get())successes++;workers.shutdown();
        if(successes!=1)throw new AssertionError("Lost update/duplicate check-in");
        var tickets=PlatformService.list(PlatformService.list(b.view(owner),"sites").get(0),"tickets");
        if(tickets.stream().filter(t->t.get("licensePlate").equals("CONCURRENT")).count()!=1)throw new AssertionError("Duplicated ticket");
        String before=storeA.load();
        try{storeA.transaction(()->{storeA.save("{}");throw new IllegalArgumentException("injected failure");});}catch(IllegalArgumentException expected){}
        if(!before.equals(storeA.load()))throw new AssertionError("Transaction failed to roll back");
        String admin=a.login("superadmin","DemoPass123!");int previous=b.sessionVersion(admin);
        a.recoverAdmin("PostgresRecovery123!");
        if(b.sessionVersion(admin)==previous)throw new AssertionError("Cross-instance revoke failed");
        b.login("superadmin","PostgresRecovery123!");
        System.out.println("PostgreSQL PASS: concurrent writers, fresh reads, rollback, cross-instance password recovery");
    }
}
