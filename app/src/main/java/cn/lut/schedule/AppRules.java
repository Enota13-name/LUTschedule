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
    /** Pick one readable foreground for the prevalent viewport color. */
    public static int foreground(int background){return contrast(0xFF000000,background)>=contrast(0xFFFFFFFF,background)?0xFF000000:0xFFFFFFFF;}
    public static int composite(int foreground,int background,int alpha){
        int r=(((foreground>>16)&255)*alpha+((background>>16)&255)*(255-alpha))/255;
        int g=(((foreground>>8)&255)*alpha+((background>>8)&255)*(255-alpha))/255;
        int b=((foreground&255)*alpha+(background&255)*(255-alpha))/255;
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
    /** 4-bit RGB buckets count area, then average only the winning color family. */
    public static int dominant(int[] samples){
        if(samples==null||samples.length==0)throw new IllegalArgumentException("没有背景颜色样本");
        int[] counts=new int[4096],red=new int[4096],green=new int[4096],blue=new int[4096];int winner=0;
        for(int color:samples){int r=(color>>16)&255,g=(color>>8)&255,b=color&255,key=((r>>4)<<8)|((g>>4)<<4)|(b>>4);
            counts[key]++;red[key]+=r;green[key]+=g;blue[key]+=b;
            if(counts[key]>counts[winner]||counts[key]==counts[winner]&&key<winner)winner=key;
        }
        int n=counts[winner];return 0xFF000000|((red[winner]/n)<<16)|((green[winner]/n)<<8)|(blue[winner]/n);
    }
    /** Preserve a card's hue while keeping the same screen foreground legible. */
    public static int readableSurface(int color,int foreground){
        if(contrast(color,foreground)>=4.5)return color|0xFF000000;
        int target=foreground==0xFFFFFFFF?0xFF000000:0xFFFFFFFF;
        for(int alpha=8;alpha<=255;alpha+=8){int candidate=composite(target,color,Math.min(255,alpha));if(contrast(candidate,foreground)>=4.5)return candidate;}
        return target;
    }
    public static final class Location {
        public final String building,room;
        Location(String building,String room){this.building=building;this.room=room;}
    }
    /** Only a terminal classroom identifier is separated; building numbers stay intact. */
    public static Location location(String value){
        String text=value==null?"":value.trim();if(text.isEmpty())return new Location("","地点待定");
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:[（(]\\s*)?([A-Za-z]*[0-9]{2,5}[A-Za-z]?)\\s*[）)]?$").matcher(text);
        if(m.find()){String building=text.substring(0,m.start()).replaceFirst("[\\s:：,，·-]+$","");return new Location(building,m.group(1));}
        return new Location("",text);
    }
    private AppRules(){}
}
