package cn.lut.schedule;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

public final class CoreTests {
    private static int checks;
    private static void check(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
    private static void rejects(Runnable operation,String name){boolean rejected=false;try{operation.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,name);}
    public static void main(String[] args){
        for(String invalid:new String[]{null,"","0,0,2","1,0","1,0,3","2;0;1"})check(Arrays.equals(AppRules.tabOrder(invalid),new int[]{1,0,2}),"invalid tab order safely uses academic/timetable/settings");
        for(String option:AppRules.TAB_ORDERS){int[] order=AppRules.tabOrder(option);check((order[0]+","+order[1]+","+order[2]).equals(option)&&order[0]!=order[1]&&order[0]!=order[2]&&order[1]!=order[2],"each permitted order retains all semantic pages");}
        int[] returned=AppRules.tabOrder(null);returned[0]=9;check(AppRules.tabOrder(null)[0]==1,"caller cannot mutate stored default");
        check(AppRules.tabLabel(null).equals("教务 — 课表 — 设置"),"default order puts timetable in the center");
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
        rejects(()->state.finish(current,ScheduleCore.Status.SUCCESS,null,100),"missing source never receives success icon");
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
        int[] majority=new int[100];Arrays.fill(majority,0xFF173248);for(int i=0;i<20;i++)majority[i]=0xFFFFFFFF;
        check(AppRules.dominant(majority)==0xFF173248,"largest area wins over minority brightness");
        Arrays.fill(majority,0xFFF4E1C3);for(int i=0;i<10;i++)majority[i]=0xFF000000;
        check(AppRules.foreground(AppRules.dominant(majority))==0xFF000000,"bright majority selects one dark foreground");
        int[] near={0xFF102030,0xFF112132,0xFF142435,0xFFE0E0E0};check(AppRules.dominant(near)==0xFF112132,"nearby colors form one quantized family");
        rejects(()->AppRules.dominant(new int[0]),"empty color data rejected");
        for(int fill:new int[]{0xFF30473C,0xFF624632,0xFF30495F,0xFF4C3B65,0xFF603B46,0xFFCCCCCC})for(int fg:new int[]{0xFFFFFFFF,0xFF000000})check(AppRules.contrast(AppRules.readableSurface(fill,fg),fg)>=4.5,"one foreground remains readable on course cards");
        check(AppRules.location("集贤楼（原西教2号楼）205").room.equals("205"),"room number separate from building numbers");
        check(AppRules.location("西教B实验楼501").building.equals("西教B实验楼"),"retain complete building above room");
        check(AppRules.location("示例教室 A201").room.equals("A201"),"alphanumeric room identifier");
        check(AppRules.location("蔚云楼 (306)").room.equals("306"),"parenthesized room number");
        check(AppRules.location("运动场").room.equals("运动场"),"outdoor location remains whole");
        check(AppRules.location("").room.equals("地点待定"),"missing location is explicit");
        System.out.println("PASS: "+checks+" meaningful core checks");
    }
}
