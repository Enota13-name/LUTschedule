package cn.lut.schedule;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.*;

/** Activity theme prevents floating/inset dialog windows on phone and tablet. */
final class Fullscreen {
    static void prepare(Dialog dialog,View body,boolean dark){Window w=dialog.getWindow();if(w==null)return;w.setBackgroundDrawable(new ColorDrawable(dark?0xFF121212:0xFFF5F5F5));w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(android.os.Build.VERSION.SDK_INT>=30){w.setDecorFitsSystemWindows(false);body.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});}
        else w.getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.setStatusBarColor(dark?0xFF121212:0xFFF5F5F5);w.setNavigationBarColor(dark?0xFF121212:0xFFF5F5F5);
        body.post(()->{if(android.os.Build.VERSION.SDK_INT>=30){WindowInsetsController c=body.getWindowInsetsController();if(c!=null){int flags=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;c.setSystemBarsAppearance(dark?0:flags,flags);}}});
    }
    static void expand(Dialog dialog){Window w=dialog.getWindow();if(w!=null){w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);w.getDecorView().setPadding(0,0,0,0);}}
}
