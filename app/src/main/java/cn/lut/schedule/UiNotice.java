package cn.lut.schedule;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import java.util.*;
/** Small app-owned transient notice, without Android Toast chrome. */
final class UiNotice {
    private static final WeakHashMap<Activity,View> active=new WeakHashMap<>();
    static void show(Activity owner,String message){if(owner.isFinishing()||owner.isDestroyed())return;int dp=Math.round(owner.getResources().getDisplayMetrics().density);ViewGroup host=owner.findViewById(android.R.id.content);if(host==null)return;View prior=active.remove(owner);if(prior!=null&&prior.getParent() instanceof ViewGroup)((ViewGroup)prior.getParent()).removeView(prior);TextView text=new TextView(owner);text.setText(message);int foreground=owner instanceof MainActivity?((MainActivity)owner).screenForeground():android.graphics.Color.WHITE;text.setTextColor(foreground);text.setTextSize(13);text.setGravity(Gravity.CENTER);text.setMaxLines(3);text.setPadding(16*dp,12*dp,16*dp,12*dp);GradientDrawable shape=new GradientDrawable();shape.setColor(foreground==android.graphics.Color.WHITE?0xFF252525:0xFFF5F5F5);shape.setCornerRadius(5*dp);text.setBackground(shape);text.setElevation(5*dp);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);p.setMargins(20*dp,0,20*dp,86*dp);host.addView(text,p);active.put(owner,text);new Handler(owner.getMainLooper()).postDelayed(()->{if(text.getParent() instanceof ViewGroup)((ViewGroup)text.getParent()).removeView(text);if(active.get(owner)==text)active.remove(owner);},2600);}
    private UiNotice(){}
}
