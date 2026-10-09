package cn.lut.schedule;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

public final class CoreTests {
    private static int checks;
    private static void check(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
    private static void rejects(Runnable operation,String name){boolean rejected=false;try{operation.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,name);}
    public static void main(String[] args){
        ScheduleCore.Course a=new ScheduleCore.Course("单周课","教师","教室",1,1,2,Arrays.asList(1,3,5));
        ScheduleCore.Course b=new ScheduleCore.Course("冲突课","教师","教室",1,2,3,Arrays.asList(1,2));
        check(a.appears(3)&&!a.appears(2),"odd-week filtering");check(a.overlaps(b),"cross-slot conflict");
        ScheduleCore.Course chain=new ScheduleCore.Course("接续课","","",1,3,4,Arrays.asList(1));
        java.util.List<ScheduleCore.Placement> placements=ScheduleCore.layout(Arrays.asList(a,b,chain),1);
        check(placements.size()==3&&placements.get(0).lanes==2&&placements.get(1).lanes==2&&placements.get(2).lanes==2,"consistent chained-overlap lanes");
        check(placements.get(0).lane==0&&placements.get(1).lane==1&&placements.get(2).lane==0,"reuse freed lane without hiding a course");
        ScheduleCore.Course firstHalf=new ScheduleCore.Course("第一小节","","",1,1,1,Arrays.asList(1),1,1,2);
        ScheduleCore.Course secondHalf=new ScheduleCore.Course("第二小节","","",1,1,1,Arrays.asList(1),2,2,2);
        check(!firstHalf.overlaps(secondHalf),"different actual periods in same display row are not a conflict");
        check(firstHalf.firstRow()==0 && firstHalf.afterLastRow()==0.5f && secondHalf.firstRow()==0.5f,"preserve actual period geometry");
        ScheduleCore.Course late=new ScheduleCore.Course("晚课","","",7,6,6,Arrays.asList(1),11,12,2);
        check(late.firstRow()==5 && late.afterLastRow()==6,"map all twelve periods to six rows without truncation");
        check(new ScheduleCore.Course("晚课","","",1,11,12,Arrays.asList(1)).end==12,"preserve official night periods");
        rejects(()->new ScheduleCore.Course("越界课","","",1,25,26,Arrays.asList(1)),"reject invalid periods");
        rejects(()->new ScheduleCore.Course("","","",1,1,1,Arrays.asList(1)),"reject missing name");
        rejects(()->new ScheduleCore.Course("课","","",8,1,1,Arrays.asList(1)),"reject invalid weekday");
        rejects(()->new ScheduleCore.Course("课","","",1,1,1,Collections.emptyList()),"require explicit weeks");
        ScheduleCore.Snapshot s=new ScheduleCore.Snapshot("2026秋",LocalDate.of(2026,9,7),20,ScheduleCore.Source.IMPORT,0,Arrays.asList(a,b));
        check(s.weekOf(LocalDate.of(2026,9,7))==1,"semester start");
        check(s.weekOf(LocalDate.of(2026,9,13))==1,"Sunday stays in current week");
        check(s.weekOf(LocalDate.of(2026,9,14))==2,"Monday advances week");
        check(s.weekOf(LocalDate.of(2026,9,6))==0,"before-semester arithmetic");
        check(s.mondayOf(6).equals(LocalDate.of(2026,10,12)),"date headers follow selected week");
        rejects(()->new ScheduleCore.Snapshot("秋",LocalDate.of(2026,9,8),20,ScheduleCore.Source.IMPORT,0,Arrays.asList(a)),"Monday validation");
        rejects(()->new ScheduleCore.Snapshot("秋",LocalDate.of(2026,9,7),2,ScheduleCore.Source.IMPORT,0,Arrays.asList(a)),"weeks outside semester");
        rejects(()->new ScheduleCore.Snapshot("秋",LocalDate.of(2026,9,7),20,ScheduleCore.Source.OFFICIAL,0,Arrays.asList(a)),"official sync timestamp required");
        ScheduleCore.SyncState state=new ScheduleCore.SyncState();int old=state.begin();int current=state.begin();
        check(!state.finish(old,ScheduleCore.Status.SUCCESS,ScheduleCore.Source.OFFICIAL,100),"ignore late prior refresh");
        check(!state.isCurrent(old)&&state.isCurrent(current),"stale response cannot replace disk cache");
        rejects(()->state.finish(current,ScheduleCore.Status.SUCCESS,ScheduleCore.Source.DEMO,100),"demo never receives success icon");
        rejects(()->state.finish(current,ScheduleCore.Status.SUCCESS,ScheduleCore.Source.IMPORT,100),"import never receives success icon");
        check(state.finish(current,ScheduleCore.Status.SUCCESS,ScheduleCore.Source.OFFICIAL,100),"verified official sync succeeds");
        check(state.lastSuccess()==100,"store successful sync timestamp");
        int failed=state.begin();check(state.status()==ScheduleCore.Status.UPDATING,"clear green check during new refresh");
        state.finish(failed,ScheduleCore.Status.UNREACHABLE,null,0);check(state.lastSuccess()==100,"failure keeps last successful timestamp");
        check(state.status()==ScheduleCore.Status.UNREACHABLE,"failure no longer shows green check");
        check(!state.finish(failed,ScheduleCore.Status.SUCCESS,ScheduleCore.Source.OFFICIAL,200),"one completion per refresh");
        long last=java.time.ZonedDateTime.of(2026,10,9,14,30,0,0,AppRules.SCHOOL).toInstant().toEpochMilli();
        check(AppRules.idleTarget(last)==java.time.ZonedDateTime.of(2026,10,11,8,0,0,0,AppRules.SCHOOL).toInstant().toEpochMilli(),"first Beijing morning after full 24h");
        long early=java.time.ZonedDateTime.of(2026,10,9,7,30,0,0,AppRules.SCHOOL).toInstant().toEpochMilli();
        check(AppRules.idleTarget(early)==java.time.ZonedDateTime.of(2026,10,10,8,0,0,0,AppRules.SCHOOL).toInstant().toEpochMilli(),"before-eight operation targets tomorrow morning");
        long exact=java.time.ZonedDateTime.of(2026,10,9,8,0,0,0,AppRules.SCHOOL).toInstant().toEpochMilli();check(AppRules.idleTarget(exact)==exact+24*3600000L,"exact-eight boundary");
        check(AppRules.foreground(0xFFFFFFFF)==0xFF000000&&AppRules.foreground(0xFF000000)==0xFFFFFFFF,"inverse foreground at light and dark locations");
        for(int gray=0;gray<=255;gray+=5){int sample=0xFF000000|(gray<<16)|(gray<<8)|gray;check(AppRules.contrast(AppRules.foreground(sample),sample)>=4.5,"readable local contrast at gray "+gray);}
        ScheduleCore.Course rawNight=new ScheduleCore.Course("晚课","","",2,11,12,Arrays.asList(1));ScheduleCore.Snapshot raw=new ScheduleCore.Snapshot("秋",LocalDate.of(2026,9,7),20,ScheduleCore.Source.IMPORT,0,Arrays.asList(rawNight),12);check(raw.periodCount==12,"official period count preserves late and empty periods");
        System.out.println("PASS: "+checks+" meaningful core checks");
    }
}
