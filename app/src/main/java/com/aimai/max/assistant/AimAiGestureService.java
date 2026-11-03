package com.aimai.max.assistant;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class AimAiGestureService extends AccessibilityService {
 private static volatile AimAiGestureService instance;
 private static volatile boolean gameForeground=false;
 private static final String TAG="AimAiAutoPlay";
 @Override public void onServiceConnected(){super.onServiceConnected();instance=this;Log.i(TAG,"Accessibility connected; gesture capability declared in XML");}
 public static boolean isConnected(){return instance!=null;}
 public static boolean isGameForeground(){return gameForeground;}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){
  if(e==null)return;
  int t=e.getEventType();
  if(t!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&t!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
  CharSequence p=e.getPackageName(); boolean active=p!=null&&"com.miniclip.carrom".contentEquals(p);
  if(active!=gameForeground){gameForeground=active;AimAiGestureService s=instance;
   if(s!=null){s.getSharedPreferences("aimai_settings",MODE_PRIVATE).edit().putBoolean("game_foreground",active).apply();
    android.content.Intent i=new android.content.Intent("com.aimai.max.GAME_FOREGROUND");i.setPackage(s.getPackageName());i.putExtra("foreground",active);s.sendBroadcast(i);}
   Log.i(TAG,"Foreground="+active+" package="+p);
  }
 }
 public static boolean swipeShot(float x,float y,float gx,float gy,int w,int h,float power){
  AimAiGestureService s=instance;
  if(s==null||Build.VERSION.SDK_INT<24||!gameForeground){Log.w(TAG,"Gesture rejected: game not foreground or service unavailable");return false;}
  float dx=gx-x,dy=gy-y,len=(float)Math.hypot(dx,dy);
  if(len<2f)return false;
  float strength=Math.max(0f,Math.min(1f,power));
  float pull=Math.max(45f,Math.min(Math.min(w,h)*0.18f,45f+strength*170f));
  float sx=clamp(x-dx/len*pull,2,w-2),sy=clamp(y-dy/len*pull,2,h-2);
  float ex=clamp(x,2,w-2),ey=clamp(y,2,h-2);
  Path path=new Path();path.moveTo(sx,sy);path.lineTo(ex,ey);
  GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,180)).build();
  boolean ok=s.dispatchGesture(g,new GestureResultCallback(){
   @Override public void onCompleted(GestureDescription d){Log.i(TAG,"Gesture completed at striker "+ex+","+ey);}
   @Override public void onCancelled(GestureDescription d){Log.e(TAG,"Gesture cancelled");}
  },null);
  Log.i(TAG,"dispatchGesture accepted="+ok+" pullback="+sx+","+sy+" striker="+ex+","+ey+" power="+strength);
  return ok;
 }
 private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
 @Override public void onInterrupt(){Log.w(TAG,"Accessibility interrupted");}
 @Override public void onDestroy(){gameForeground=false;if(instance==this)instance=null;super.onDestroy();}
}