package cn.lut.schedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure Java model. Imported and official snapshots never share a success state. */
public final class ScheduleCore {
    public enum Source { OFFICIAL, IMPORT }
    public enum Status { IDLE, UPDATING, SUCCESS, LOGIN_REQUIRED, OFFLINE, UNREACHABLE, SERVER_ERROR, ADAPTER_PENDING, PARSE_ERROR }
    public static final class Course {
        public final String name, teacher, room;
        public final int day, start, end;
        public final int rawStart,rawEnd,periodsPerRow;
        public final List<Integer> weeks;
        public Course(String name, String teacher, String room, int day, int start, int end, List<Integer> weeks) {
            this(name,teacher,room,day,start,end,weeks,0,0,1);
        }
        public Course(String name, String teacher, String room, int day, int start, int end, List<Integer> weeks,int rawStart,int rawEnd,int periodsPerRow) {
            if (name == null || name.trim().isEmpty() || name.length() > 200) throw new IllegalArgumentException("课程名称为空或过长");
            if (day < 1 || day > 7 || start < 1 || end < start || end > 24) throw new IllegalArgumentException("星期或节次无效；不能截断课程");
            if (weeks == null || weeks.isEmpty()) throw new IllegalArgumentException("必须指定课程周次");
            for (Integer w : weeks) if (w == null || w < 1 || w > 60) throw new IllegalArgumentException("课程周次应为 1–60");
            if(periodsPerRow<1 || periodsPerRow>2 || (rawStart!=0 || rawEnd!=0) && (rawStart<1 || rawEnd<rawStart || rawEnd>24 || start!=(rawStart-1)/periodsPerRow+1 || end!=(rawEnd-1)/periodsPerRow+1))throw new IllegalArgumentException("官网节次映射无效");
            this.rawStart=rawStart;this.rawEnd=rawEnd;this.periodsPerRow=periodsPerRow;
            this.name=name.trim(); this.teacher=teacher==null?"":teacher; this.room=room==null?"":room;
            this.day=day; this.start=start; this.end=end;
            this.weeks=Collections.unmodifiableList(new ArrayList<>(weeks));
        }
        public boolean appears(int week) { return weeks.contains(week); }
        public float firstRow(){return rawStart>0?(rawStart-1f)/periodsPerRow:start-1f;}
        public float afterLastRow(){return rawEnd>0?rawEnd/(float)periodsPerRow:end;}
        public boolean overlaps(Course other) { return day == other.day && firstRow()<other.afterLastRow() && other.firstRow()<afterLastRow(); }
    }
    public static final class Snapshot {
        public final String semester;
        public final LocalDate firstMonday;
        public final int totalWeeks,periodCount;
        public final Source source;
        public final long fetchedAt;
        public final List<Course> courses;
        public Snapshot(String semester, LocalDate monday, int totalWeeks, Source source, long fetchedAt, List<Course> courses) {
            this(semester,monday,totalWeeks,source,fetchedAt,courses,0);
        }
        public Snapshot(String semester, LocalDate monday, int totalWeeks, Source source, long fetchedAt, List<Course> courses,int count) {
            if (semester == null || semester.trim().isEmpty() || semester.length()>100) throw new IllegalArgumentException("缺少学期名称");
            if (monday == null || monday.getDayOfWeek().getValue()!=1) throw new IllegalArgumentException("第一周起始日期必须是周一");
            if (totalWeeks<1 || totalWeeks>60 || source==null || courses==null || courses.size()>500 || (source==Source.OFFICIAL && fetchedAt<=0)) throw new IllegalArgumentException("课表范围或同步时间无效");
            for(Course c:courses) for(Integer w:c.weeks) if(w>totalWeeks) throw new IllegalArgumentException("课程周次超过学期范围");
            int max=1;for(Course c:courses)max=Math.max(max,c.rawEnd>0?c.rawEnd:c.end);
            if(count<0||count>24||count>0&&count<max)throw new IllegalArgumentException("官网小节数量与课程范围不符");
            this.periodCount=count==0?max:count;
            this.semester=semester; firstMonday=monday; this.totalWeeks=totalWeeks; this.source=source; this.fetchedAt=fetchedAt;
            this.courses=Collections.unmodifiableList(new ArrayList<>(courses));
        }
        public int weekOf(LocalDate date) { return (int)Math.floorDiv(ChronoUnit.DAYS.between(firstMonday,date),7)+1; }
        public LocalDate mondayOf(int week) { return firstMonday.plusWeeks(week-1L); }
    }
    public static final class SyncState {
        private Status status=Status.IDLE;
        private long lastSuccess;
        private int generation;
        public int begin() { status=Status.UPDATING; return ++generation; }
        public boolean finish(int ticket, Status result, Source source, long timestamp) {
            if(ticket!=generation || status!=Status.UPDATING || result==Status.UPDATING || result==Status.IDLE) return false;
            if(result==Status.SUCCESS && (source!=Source.OFFICIAL || timestamp<=0)) throw new IllegalArgumentException("只有已验证的官网快照才能标记更新成功");
            status=result; if(result==Status.SUCCESS) lastSuccess=timestamp; return true;
        }
        public Status status(){return status;}
        public boolean isCurrent(int ticket){return ticket==generation && status==Status.UPDATING;}
        public long lastSuccess(){return lastSuccess;}
    }
    public static final class Placement {
        public final Course course;
        public final int lane, lanes;
        public final boolean conflict;
        Placement(Course c,int lane,int lanes,boolean conflict){course=c;this.lane=lane;this.lanes=lanes;this.conflict=conflict;}
    }
    /** Interval partitioning: use consistent lanes even for chained overlaps. */
    public static List<Placement> layout(List<Course> courses,int week){
        List<Placement> result=new ArrayList<>();
        for(int day=1;day<=7;day++){
            List<Course> today=new ArrayList<>();for(Course c:courses)if(c.day==day && c.appears(week))today.add(c);
            today.sort(Comparator.comparingDouble((Course c)->c.firstRow()).thenComparingDouble(c->c.afterLastRow()));
            List<Float> laneEnds=new ArrayList<>();List<Integer> assigned=new ArrayList<>();
            for(Course c:today){int lane=0;while(lane<laneEnds.size() && laneEnds.get(lane)>c.firstRow())lane++;
                if(lane==laneEnds.size())laneEnds.add(c.afterLastRow());else laneEnds.set(lane,c.afterLastRow());assigned.add(lane);}
            for(int i=0;i<today.size();i++){Course c=today.get(i);boolean conflict=false;for(Course other:today)if(other!=c&&c.overlaps(other)){conflict=true;break;}
                result.add(new Placement(c,assigned.get(i),laneEnds.size(),conflict));}
        }
        return result;
    }
    private ScheduleCore() {}
}
