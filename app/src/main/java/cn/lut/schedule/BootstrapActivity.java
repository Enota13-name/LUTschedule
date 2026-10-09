package cn.lut.schedule;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Keep the launcher independent from WebView, timetable and date classes. */
public final class BootstrapActivity extends Activity {
    public void onCreate(Bundle state){super.onCreate(state);
        if(Diagnostics.needsRecovery(this)){Diagnostics.showRecovery(this);return;}
        Intent open=new Intent(this,MainActivity.class);open.putExtra("user_launch",true);open.putExtra("show_changes",getIntent().getBooleanExtra("show_changes",false));startActivity(open);finish();
    }
}
