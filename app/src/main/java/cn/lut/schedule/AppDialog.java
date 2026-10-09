package cn.lut.schedule;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** App-owned popup chrome; no platform AlertDialog, radio list or native buttons. */
final class AppDialog extends Dialog {
    private final Activity owner;
    private final int ink,muted,panelColor,line;
    private final Map<Integer,TextView> buttons=new HashMap<>();
    private final LinearLayout panel;
    private boolean built;
    private AppDialog(Builder b){
        super(b.owner,theme(b.owner,dark(b.owner)));owner=b.owner;
        ink=owner instanceof MainActivity?((MainActivity)owner).screenForeground():dark(owner)?Color.WHITE:Color.BLACK;muted=ink;boolean dark=ink==Color.WHITE;panelColor=dark?0xFF202020:0xFFFAFAFA;line=dark?0xFF3B3B3B:0xFFE1E1E1;
        requestWindowFeature(Window.FEATURE_NO_TITLE);setCancelable(b.cancelable);setCanceledOnTouchOutside(b.cancelable);
        FrameLayout overlay=new FrameLayout(owner);overlay.setBackgroundColor(0x77000000);overlay.setOnClickListener(v->{if(b.cancelable)dismiss();});
        panel=new LinearLayout(owner);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(20),dp(12),dp(20),dp(16));panel.setBackground(shape(panelColor,6));panel.setElevation(dp(10));panel.setClickable(true);
        FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(Math.min(dp(420),owner.getResources().getDisplayMetrics().widthPixels-dp(32)),-2,Gravity.CENTER);box.setMargins(dp(16),dp(28),dp(16),dp(28));overlay.addView(panel,box);
        LinearLayout header=new LinearLayout(owner);header.setGravity(Gravity.CENTER_VERTICAL);TextView heading=label(b.title,18,ink);heading.setTypeface(Typeface.DEFAULT_BOLD);header.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));
        if(b.cancelable){TextView close=action("×",false);close.setContentDescription("关闭弹窗");close.setOnClickListener(v->dismiss());header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));}panel.addView(header);
        LinearLayout content=new LinearLayout(owner);content.setOrientation(LinearLayout.VERTICAL);
        if(b.message!=null&&!b.message.isEmpty()){TextView message=label(b.message,14,muted);message.setTextIsSelectable(true);message.setLineSpacing(dp(4),1);message.setPadding(0,dp(4),0,dp(14));content.addView(message);}
        if(b.view!=null){if(b.view.getParent() instanceof ViewGroup)((ViewGroup)b.view.getParent()).removeView(b.view);tintText(b.view);content.addView(b.view);}
        if(b.items!=null)for(int i=0;i<b.items.length;i++){final int index=i;LinearLayout row=new LinearLayout(owner);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(10),dp(2),dp(10));row.setMinimumHeight(dp(48));row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22888888),shape(Color.TRANSPARENT,2),null));TextView item=label(b.items[i],15,ink);row.addView(item,new LinearLayout.LayoutParams(0,-2,1));if(b.single&&i==b.selected){TextView check=label("✓",18,ink);row.addView(check);}row.setContentDescription(b.items[i]);row.setOnClickListener(v->{if(b.itemClick!=null)b.itemClick.onClick(this,index);if(isShowing())dismiss();});content.addView(row);if(i<b.items.length-1){View divider=new View(owner);divider.setBackgroundColor(line);content.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}}
        ScrollView scroll=new ScrollView(owner);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(content);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(owner);actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(14),0,0);
        for(int type:new int[]{BUTTON_NEUTRAL,BUTTON_NEGATIVE,BUTTON_POSITIVE}){String name=b.labels.get(type);if(name==null)continue;TextView action=action(name,type==BUTTON_POSITIVE);buttons.put(type,action);action.setOnClickListener(v->{DialogInterface.OnClickListener cb=b.callbacks.get(type);if(cb!=null)cb.onClick(this,type);if(isShowing())dismiss();});LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,dp(44));ap.leftMargin=dp(8);actions.addView(action,ap);}if(!buttons.isEmpty())panel.addView(actions);
        setContentView(overlay);Window w=getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        overlay.addOnLayoutChangeListener((v,l,t,r,bt,ol,ot,or,ob)->{int max=Math.max(dp(80),bt-t-dp(76)-header.getHeight()-actions.getMeasuredHeight()-panel.getPaddingTop()-panel.getPaddingBottom());if(content.getMeasuredHeight()>max&&scroll.getLayoutParams().height!=max){scroll.getLayoutParams().height=max;scroll.requestLayout();}});
        built=true;
    }
    static boolean dark(Activity a){String theme=a.getSharedPreferences("settings",0).getString("theme","light");return theme.equals("dark")||theme.equals("system")&&(a.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;}
    static int theme(Activity a,boolean dark){return a.getResources().getIdentifier(dark?"AppThemeDark":"AppThemeLight","style",a.getPackageName());}
    private int dp(float n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String value,int size,int color){TextView t=new TextView(owner);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;}
    private void tintText(View view){if(view instanceof TextView){((TextView)view).setTextColor(ink);if(view instanceof EditText)((EditText)view).setHintTextColor(ink);}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)tintText(((ViewGroup)view).getChildAt(i));}
    private TextView action(String value,boolean primary){TextView t=label(value,14,ink);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),0,dp(12),0);t.setMinimumHeight(dp(44));t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33888888),shape(primary?(ink==Color.WHITE?0xFF393939:0xFFE6E6E6):Color.TRANSPARENT,4),null));return t;}
    TextView getButton(int which){return buttons.get(which);}
    public void show(){super.show();Window w=getWindow();if(w!=null)w.setLayout(-1,-1);if(built&&animations(owner)){panel.setAlpha(0);panel.setTranslationY(dp(8));panel.animate().alpha(1).translationY(0).setDuration(160).start();}}
    static boolean animations(Context context){try{return android.provider.Settings.Global.getFloat(context.getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}catch(Exception e){return true;}}
    static final class Builder {
        final Activity owner;String title="",message;View view;String[] items;boolean cancelable=true,single;int selected=-1;DialogInterface.OnClickListener itemClick;
        final Map<Integer,String> labels=new HashMap<>();final Map<Integer,DialogInterface.OnClickListener> callbacks=new HashMap<>();
        Builder(Activity owner){this.owner=owner;}
        Builder setTitle(String s){title=s;return this;}Builder setMessage(String s){message=s;return this;}Builder setView(View v){view=v;return this;}Builder setCancelable(boolean v){cancelable=v;return this;}
        Builder setItems(String[] s,DialogInterface.OnClickListener c){items=s;itemClick=c;return this;}
        Builder setSingleChoiceItems(String[] s,int selected,DialogInterface.OnClickListener c){setItems(s,c);this.selected=selected;single=true;return this;}
        Builder setPositiveButton(String s,DialogInterface.OnClickListener c){return button(BUTTON_POSITIVE,s,c);}Builder setNegativeButton(String s,DialogInterface.OnClickListener c){return button(BUTTON_NEGATIVE,s,c);}Builder setNeutralButton(String s,DialogInterface.OnClickListener c){return button(BUTTON_NEUTRAL,s,c);}
        private Builder button(int type,String s,DialogInterface.OnClickListener c){labels.put(type,s);callbacks.put(type,c);return this;}
        AppDialog create(){return new AppDialog(this);}AppDialog show(){AppDialog d=create();d.show();return d;}
    }
}
