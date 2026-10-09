package cn.lut.schedule;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.*;
import android.media.ExifInterface;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;

/** Local, bounded bitmap crop. The original background is replaced only on confirmation. */
final class BackgroundCrop extends Dialog {
    final CropView crop;
    private final Activity owner;
    private final Runnable saved;
    private final boolean dark;
    BackgroundCrop(Activity activity,byte[] bytes,float aspect,boolean dark,int veil,Runnable saved)throws Exception{
        super(activity,AppDialog.theme(activity,dark));
        owner=activity;this.saved=saved;this.dark=dark;requestWindowFeature(Window.FEATURE_NO_TITLE);
        crop=new CropView(activity,decode(bytes),aspect,dark,veil);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setBackgroundColor(dark?0xFF121212:0xFFF5F5F5);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(button("取消",this::dismiss),new LinearLayout.LayoutParams(-2,dp(48)));
        TextView title=label("调整背景",18,dark);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        header.addView(button("使用",this::save),new LinearLayout.LayoutParams(-2,dp(48)));body.addView(header);
        LinearLayout modes=new LinearLayout(activity);modes.setGravity(Gravity.CENTER);TextView shared=button("共用一张",()->{crop.layoutMode(false);crop.invalidate();}),split=button("分为三份",()->{crop.layoutMode(true);crop.invalidate();});modes.addView(shared,new LinearLayout.LayoutParams(0,dp(44),1));modes.addView(split,new LinearLayout.LayoutParams(0,dp(44),1));body.addView(modes);
        TextView hint=label("双指拉伸缩放，单指拖动 · 三份依次为课表 / 教务 / 设置",12,dark);hint.setGravity(Gravity.CENTER);body.addView(hint,new LinearLayout.LayoutParams(-1,dp(40)));
        body.addView(crop,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout tools=new LinearLayout(activity);tools.setGravity(Gravity.CENTER_VERTICAL);tools.addView(button("重置",()->{crop.reset();crop.invalidate();}));
        TextView scale=label("100%",13,dark);scale.setGravity(Gravity.CENTER);tools.addView(scale,new LinearLayout.LayoutParams(0,dp(48),1));TextView preview=button("页面预览",()->{crop.preview=!crop.preview;crop.invalidate();});tools.addView(preview);body.addView(tools);
        crop.changed=()->{scale.setText(Math.round(crop.zoom*100)+"%");shared.setTypeface(null,crop.split?android.graphics.Typeface.NORMAL:android.graphics.Typeface.BOLD);split.setTypeface(null,crop.split?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);};crop.layoutMode(activity.getSharedPreferences("settings",0).getString("background_layout","shared").equals("split"));
        setContentView(body);setCanceledOnTouchOutside(false);setOnDismissListener(d->crop.release());
        Fullscreen.prepare(this,body,dark);
    }
    private int dp(float n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,boolean dark){TextView t=new TextView(owner);t.setText(value);t.setTextSize(size);t.setTextColor(dark?Color.WHITE:0xFF222222);return t;}
    private TextView button(String value,Runnable action){TextView t=label(value,15,dark);t.setGravity(Gravity.CENTER);t.setPadding(dp(16),0,dp(16),0);t.setMinHeight(dp(48));t.setContentDescription(value);t.setOnClickListener(v->action.run());return t;}
    public void show(){super.show();Fullscreen.expand(this);}
    private void save(){Bitmap output=null;File temp=new File(owner.getFilesDir(),"background-new.jpg");try{
        output=crop.export();try(FileOutputStream out=new FileOutputStream(temp)){if(!output.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("图片保存失败");out.getFD().sync();}
        ExifInterface metadata=new ExifInterface(temp.getAbsolutePath());metadata.setAttribute(ExifInterface.TAG_USER_COMMENT,"LUTBACKGROUND:"+(crop.split?"split":"shared"));metadata.saveAttributes();try(RandomAccessFile verify=new RandomAccessFile(temp,"rw")){verify.getFD().sync();}Files.move(temp.toPath(),new File(owner.getFilesDir(),"background.jpg").toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        owner.getSharedPreferences("settings",0).edit().putString("background_layout",crop.split?"split":"shared").commit();dismiss();saved.run();
    }catch(Exception|OutOfMemoryError error){temp.delete();Diagnostics.record(owner,"背景裁剪保存",error,false);UiNotice.show(owner,"背景未更改，错误报告已保存");}finally{if(output!=null)output.recycle();}}
    static Bitmap decode(byte[] bytes)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(o.outWidth<=0||o.outHeight<=0)throw new IOException("无法读取该图片");o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>2400)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(image==null)throw new IOException("图片解码失败");
        int orientation=1;try{orientation=new ExifInterface(new ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
        Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(90);m.postScale(1,-1);break;case 8:m.setRotate(270);break;}
        if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);if(rotated!=image)image.recycle();image=rotated;}return image;
    }
    static final class CropView extends View {
        Bitmap image;final Paint paint=new Paint(3);final RectF frame=new RectF();final float pageAspect;float aspect;boolean split;final boolean dark;final int veil;
        float zoom=1,baseScale,x,y,lastX,lastY;boolean preview;Runnable changed;private final ScaleGestureDetector detector;
        CropView(Activity context,Bitmap bitmap,float aspect,boolean dark,int veil){super(context);image=bitmap;this.pageAspect=Math.max(.2f,Math.min(3,aspect));this.aspect=pageAspect;this.dark=dark;this.veil=veil;setContentDescription("背景裁剪：单指拖动，双指拉伸缩放，共用或分三份");
            detector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){zoomTo(zoom*d.getScaleFactor(),d.getFocusX(),d.getFocusY());invalidate();return true;}});
        }
        void layoutMode(boolean value){split=value;aspect=pageAspect*(split?3:1);updateFrame(getWidth(),getHeight());}
        protected void onSizeChanged(int w,int h,int oldW,int oldH){updateFrame(w,h);}
        private void updateFrame(int w,int h){if(w<=0||h<=0)return;float margin=12*getResources().getDisplayMetrics().density,fw=Math.max(1,w-2*margin),fh=fw/aspect;if(fh>h-2*margin){fh=Math.max(1,h-2*margin);fw=fh*aspect;}frame.set((w-fw)/2,(h-fh)/2,(w+fw)/2,(h+fh)/2);reset();}
        void reset(){if(image==null)return;baseScale=Math.max(frame.width()/image.getWidth(),frame.height()/image.getHeight());zoom=1;x=frame.centerX()-image.getWidth()*baseScale/2;y=frame.centerY()-image.getHeight()*baseScale/2;clamp();if(changed!=null)changed.run();}
        void clamp(){if(image==null)return;float width=image.getWidth()*baseScale*zoom,height=image.getHeight()*baseScale*zoom;x=Math.max(frame.right-width,Math.min(frame.left,x));y=Math.max(frame.bottom-height,Math.min(frame.top,y));}
        void zoomTo(float desired,float fx,float fy){float old=zoom;zoom=Math.max(1,Math.min(4,desired));x=fx-(fx-x)*zoom/old;y=fy-(fy-y)*zoom/old;clamp();if(changed!=null)changed.run();}
        public boolean onTouchEvent(MotionEvent e){detector.onTouchEvent(e);int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}else if(a==MotionEvent.ACTION_MOVE){if(!detector.isInProgress()&&e.getPointerCount()==1){x+=e.getX()-lastX;y+=e.getY()-lastY;clamp();invalidate();}lastX=e.getX();lastY=e.getY();}else if(a==MotionEvent.ACTION_POINTER_UP){int index=e.getActionIndex()==0?1:0;lastX=e.getX(index);lastY=e.getY(index);}else if(a==MotionEvent.ACTION_UP)performClick();return true;}
        public boolean performClick(){super.performClick();return true;}
        protected void onDraw(Canvas c){if(image==null)return;c.drawColor(0xFF111111);float s=baseScale*zoom;c.drawBitmap(image,null,new RectF(x,y,x+image.getWidth()*s,y+image.getHeight()*s),paint);
            paint.setColor(0xBB000000);c.drawRect(0,0,getWidth(),frame.top,paint);c.drawRect(0,frame.bottom,getWidth(),getHeight(),paint);c.drawRect(0,frame.top,frame.left,frame.bottom,paint);c.drawRect(frame.right,frame.top,getWidth(),frame.bottom,paint);
            if(preview&&!split){c.save();c.clipRect(frame);paint.setColor(Color.argb(Math.max(0,Math.min(255,veil*255/100)),dark?18:245,dark?18:245,dark?18:245));c.drawRect(frame,paint);
                float unit=frame.width()/390f;drawLabel(c,"当前学期",frame.left+16*unit,frame.top+38*unit,13*unit);drawLabel(c,"✓",frame.right-30*unit,frame.top+38*unit,19*unit);
                String[] days={"一","二","三","四","五","六","日"};for(int i=0;i<7;i++)drawLabel(c,"周"+days[i],frame.left+(44+i*47)*unit,frame.top+78*unit,11*unit);
                paint.setColor(0xDDBACFC3);float cw=43*unit;for(int i=0;i<3;i++){float left=frame.left+(40+i*94)*unit,top=frame.top+(110+i*58)*unit;c.drawRoundRect(left,top,left+cw,top+94*unit,3*unit,3*unit,paint);}
                drawLabel(c,"‹   第6周   ›   本周",frame.right-181*unit,frame.bottom-71*unit,13*unit);drawLabel(c,"课表             教务             设置",frame.left+42*unit,frame.bottom-24*unit,12*unit);c.restore();}
            if(split){String[] names={"课表","教务","设置"};float width=frame.width()/3;for(int i=0;i<3;i++){float left=frame.left+i*width;paint.setColor(0xCC171717);c.drawRect(left,frame.top,left+width,frame.top+24*getResources().getDisplayMetrics().density,paint);paint.setColor(Color.WHITE);paint.setTextSize(12*getResources().getDisplayMetrics().density);c.drawText(names[i],left+8*getResources().getDisplayMetrics().density,frame.top+17*getResources().getDisplayMetrics().density,paint);if(i>0){paint.setColor(Color.WHITE);paint.setStrokeWidth(2);c.drawLine(left,frame.top,left,frame.bottom,paint);}}}
            paint.setColor(0xBBFFFFFF);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);c.drawRect(frame,paint);paint.setStyle(Paint.Style.FILL);
        }
        private void drawLabel(Canvas c,String text,float px,float py,float size){float s=baseScale*zoom;int ix=Math.max(0,Math.min(image.getWidth()-1,(int)((px-x)/s))),iy=Math.max(0,Math.min(image.getHeight()-1,(int)((py-y)/s)));int sample=AppRules.composite(dark?0xFF121212:0xFFF5F5F5,image.getPixel(ix,iy),Math.max(0,Math.min(255,veil*255/100)));paint.setColor(AppRules.foreground(sample));paint.setTextSize(size);c.drawText(text,px,py,paint);}
        Bitmap export(){if(image==null||frame.width()<=0||baseScale<=0)throw new IllegalStateException("预览尚未准备好");int height=1600,width=Math.max(1,Math.round(height*aspect)),limit=split?2400:1600;if(width>limit){width=limit;height=Math.round(width/aspect);}Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);float factor=width/frame.width();c.drawColor(Color.BLACK);Matrix m=new Matrix();m.setScale(baseScale*zoom*factor,baseScale*zoom*factor);m.postTranslate((x-frame.left)*factor,(y-frame.top)*factor);c.drawBitmap(image,m,paint);return out;}
        void release(){if(image!=null){image.recycle();image=null;}}
    }
}
