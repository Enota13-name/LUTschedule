package cn.lut.schedule;

import android.content.Context;
import android.content.SharedPreferences;

/** Selecting a file never grants access to a school session. */
final class AccessMode {
    static final String NONE="none", OFFICIAL="official", IMPORT="import";
    static boolean official(Context c){return OFFICIAL.equals(c.getSharedPreferences("settings",0).getString("entry_mode",NONE));}
    static void migrate(SharedPreferences p,SnapshotStore store){
        if(!p.contains("entry_mode")){
            ScheduleCore.Snapshot official=store.load(ScheduleCore.Source.OFFICIAL),imported=store.load(ScheduleCore.Source.IMPORT);
            String mode=imported!=null&&(official==null||imported.fetchedAt>official.fetchedAt)?IMPORT:official!=null?OFFICIAL:NONE;
            p.edit().putString("entry_mode",mode).apply();
        }
        // Obsolete, user-editable preferences have no effect on current behavior.
        p.edit().remove("demo").remove("developer").apply();
    }
    private AccessMode(){}
}
