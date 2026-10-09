package cn.lut.schedule;
public final class ReportRedactorTests {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        check(ReportRedactor.clean(null).equals(""),"null input");
        check(!ReportRedactor.clean("https://example.test/path?ticket=secret#account").contains("secret"),"URL credentials removed");
        check(!ReportRedactor.clean("Authorization: Bearer secret\nCookie: SID=private\nkeep stack").contains("secret")&&!ReportRedactor.clean("Cookie: SID=private").contains("private"),"session headers removed");
        check(!ReportRedactor.clean("{\"token\":\"a secret with spaces\",'password':'private word'}").contains("secret")&&!ReportRedactor.clean("{\"token\":\"a secret with spaces\",'password':'private word'}").contains("private"),"quoted JSON secrets removed");
        check(!ReportRedactor.clean("学号=2026123456 password=hidden").contains("2026123456")&&!ReportRedactor.clean("password=hidden").contains("hidden"),"student ID and plain password removed");
        check(ReportRedactor.clean("MainActivity.java:109 IllegalStateException").equals("MainActivity.java:109 IllegalStateException"),"source location preserved");
        check(ReportRedactor.clean(new String(new char[2000]).replace('\0','x')).length()<=601,"bounded messages");
        System.out.println("PASS: "+checks+" report redaction assertions");
    }
}
