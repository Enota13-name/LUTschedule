package cn.lut.schedule;

/** Shared deterministic redaction; never feed raw page/response content to diagnostics. */
public final class ReportRedactor {
    public static String clean(String value){
        if(value==null)return "";
        String text=value.replaceAll("(?i)https?://[^\\s]+","[url-redacted]")
            .replaceAll("(?im)(authorization|cookie|set-cookie)\\s*[:=][^\\r\\n]*","$1: [redacted]")
            .replaceAll("(?i)[\"']?(password|passwd|pwd|token|ticket|access_token|session|jsessionid|studentid|username|account|xh|学号|密码)[\"']?\\s*[:=]\\s*(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s,;\\r\\n}]+)","$1=[redacted]")
            .replaceAll("(?<![a-zA-Z0-9])\\d{8,}(?![a-zA-Z0-9])","[number-redacted]");
        return text.length()>600?text.substring(0,600)+"…":text;
    }
    private ReportRedactor(){}
}
