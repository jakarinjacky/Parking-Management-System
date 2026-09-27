package test;
import platform.*;
import java.util.*;
import static platform.PlatformService.*;
import static test.PlatformAnalyticsTest.*;
import static test.PlatformTenantLifecycleTest.denied;

public class PlatformBillingTest {
    static List<Map<String,Object>> bills(PlatformService s,String uid){return list(map(s.view(uid).get("platformBilling")),"invoices");}
    public static void main(String[] args)throws Exception {
        Store store=new Store();var s=new PlatformService(store,false,"Administrator123!");String root=s.login("superadmin","Administrator123!");
        for(String name:List.of("customer","other"))cmd(s,root,"createTenant",Map.of("name",name,"username",name,"password","CustomerPass123!"));
        String owner=s.login("customer","CustomerPass123!"),other=s.login("other","CustomerPass123!");String tenant=map(s.view(owner).get("user")).get("tenantId").toString();
        Map<String,Object> issue=Map.of("tenantId",tenant,"plan","STARTER","periodStart","2024-01-31","dueDate","2024-02-07","amount",1);
        denied(()->cmd(s,root,"issuePlatformInvoice",issue));
        cmd(s,root,"configureBilling",Map.of("paymentInstructions","Test bank details"));
        denied(()->cmd(s,owner,"issuePlatformInvoice",issue));
        cmd(s,root,"issuePlatformInvoice",issue);var inv=bills(s,owner).get(0);String id=inv.get("id").toString();
        check(((Number)inv.get("amount")).intValue()==499);check(inv.get("periodEnd").equals("2024-02-29"));check(bills(s,other).isEmpty());
        denied(()->cmd(s,root,"issuePlatformInvoice",issue));
        denied(()->cmd(s,root,"issuePlatformInvoice",Map.of("tenantId",tenant,"plan","BUSINESS","periodStart","2024-02-01","dueDate","2024-02-07")));
        denied(()->cmd(s,other,"reportPlatformTransfer",Map.of("invoiceId",id,"reference","wrong")));
        cmd(s,owner,"reportPlatformTransfer",Map.of("invoiceId",id,"reference","customer-report"));check(bills(s,root).get(0).get("status").equals("REPORTED"));
        denied(()->cmd(s,owner,"confirmPlatformPayment",Map.of("invoiceId",id,"reference","tx-1","confirmed",true)));
        denied(()->cmd(s,root,"confirmPlatformPayment",Map.of("invoiceId",id,"reference","tx-1","confirmed",false)));
        cmd(s,root,"confirmPlatformPayment",Map.of("invoiceId",id,"reference","tx-1","confirmed",true));
        denied(()->cmd(s,root,"confirmPlatformPayment",Map.of("invoiceId",id,"reference","tx-1","confirmed",true)));
        denied(()->cmd(s,root,"voidPlatformInvoice",Map.of("invoiceId",id,"reason","cannot void paid")));
        cmd(s,root,"configureBilling",Map.of("paymentInstructions","Changed bank"));check(bills(s,owner).get(0).get("paymentInstructions").equals("Test bank details"));
        cmd(s,root,"issuePlatformInvoice",Map.of("tenantId",tenant,"plan","BUSINESS","periodStart","2024-02-29","dueDate","2024-03-07"));
        String next=bills(s,root).get(1).get("id").toString();denied(()->cmd(s,root,"confirmPlatformPayment",Map.of("invoiceId",next,"reference","tx-1","confirmed",true)));
        cmd(s,root,"voidPlatformInvoice",Map.of("invoiceId",next,"reason","change plan"));
        cmd(s,root,"issuePlatformInvoice",Map.of("tenantId",tenant,"plan","MULTI_SITE","periodStart","2024-02-29","dueDate","2024-03-07"));
        check(bills(s,root).size()==3);check(((Number)bills(s,root).get(2).get("amount")).intValue()==1990);
        var restarted=new PlatformService(store,false,null);check(bills(restarted,owner).get(0).get("status").equals("PAID"));
        cmd(s,root,"setTenantActive",Map.of("tenantId",tenant,"active",false));
        denied(()->cmd(s,root,"deleteTenant",Map.of("tenantId",tenant,"confirmationName","customer","currentPassword","Administrator123!")));
        cmd(s,root,"setTenantActive",Map.of("tenantId",tenant,"active",true));
        cmd(s,owner,"createSite",Map.of("name","Lot","businessType","PUBLIC"));String site=list(s.view(owner),"sites").get(0).get("id").toString();
        cmd(s,owner,"createUser",Map.of("username","employee","password","EmployeePass123!","role","staff","siteIds",List.of(site)));
        String staff=s.login("employee","EmployeePass123!");check(!s.view(staff).containsKey("platformBilling"));
        denied(()->cmd(s,staff,"reportPlatformTransfer",Map.of("invoiceId",next,"reference","forbidden")));
        System.out.println("Billing PASS: roles, tenant isolation, server prices, overlap, leap month, manual confirmation, duplicate protection, void, snapshots, restart, financial retention");
    }
}
