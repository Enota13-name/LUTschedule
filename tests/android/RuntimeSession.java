package cn.lut.schedule.tests;

import android.app.Instrumentation;
import android.content.Context;
import android.webkit.CookieManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Cross-process CookieManager check with only explicitly synthetic cookies on an invalid test host. */
public final class RuntimeSession {
    private static final String URL="https://session-fixture.invalid/";
    public static String run(Instrumentation test,boolean write)throws Exception{
        CookieManager[] jar={null};test.runOnMainSync(()->{jar[0]=CookieManager.getInstance();jar[0].setAcceptCookie(true);});
        if(write){CountDownLatch accepted=new CountDownLatch(2);boolean[] ok={false,false};
            test.runOnMainSync(()->{
                jar[0].setCookie(URL,"synthetic_persistent=fixture; Max-Age=86400; Path=/; Secure; HttpOnly",value->{ok[0]=value;accepted.countDown();});
                jar[0].setCookie(URL,"synthetic_session=fixture; Path=/; Secure; HttpOnly",value->{ok[1]=value;accepted.countDown();});
            });
            if(!accepted.await(8,TimeUnit.SECONDS)||!ok[0]||!ok[1])throw new AssertionError("Synthetic cookie write failed");
            jar[0].flush();
        }
        String value=jar[0].getCookie(URL);boolean persistent=value!=null&&value.contains("synthetic_persistent=fixture"),session=value!=null&&value.contains("synthetic_session=fixture");
        if(!persistent)throw new AssertionError("Synthetic persistent cookie missing");
        return "PASS: synthetic CookieManager "+(write?"write":"read after process restart")+"; persistentPresent="+persistent+"; sessionPresent="+session+"; no real cookies exported; server session validity not tested";
    }
}
