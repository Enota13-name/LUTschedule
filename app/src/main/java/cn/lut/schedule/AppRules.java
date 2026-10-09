package cn.lut.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Rules shared by the screen and the one-shot idle job. No Android dependencies. */
public final class AppRules {
    public static final ZoneId SCHOOL=ZoneId.of("Asia/Shanghai");
    public static long idleTarget(long lastOperation){
        ZonedDateTime eligible=Instant.ofEpochMilli(lastOperation).atZone(SCHOOL).plusHours(24);
        ZonedDateTime morning=eligible.toLocalDate().atTime(8,0).atZone(SCHOOL);
        if(morning.isBefore(eligible))morning=morning.plusDays(1);
        return morning.toInstant().toEpochMilli();
    }
    public static double luminance(int color){
        double r=linear((color>>16)&255),g=linear((color>>8)&255),b=linear(color&255);
        return .2126*r+.7152*g+.0722*b;
    }
    private static double linear(int v){double s=v/255.0;return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
    public static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    /** Pick the light/dark inverse with the stronger measured contrast at this location. */
    public static int foreground(int background){return contrast(0xFF000000,background)>=contrast(0xFFFFFFFF,background)?0xFF000000:0xFFFFFFFF;}
    public static int composite(int foreground,int background,int alpha){
        int r=(((foreground>>16)&255)*alpha+((background>>16)&255)*(255-alpha))/255;
        int g=(((foreground>>8)&255)*alpha+((background>>8)&255)*(255-alpha))/255;
        int b=((foreground&255)*alpha+(background&255)*(255-alpha))/255;
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
    private AppRules(){}
}
