package com.airocta.vyron;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opt-in voice control of visible Android UI. Enabled only by the phone owner in Settings. */
public final class VyronAccessibilityService extends AccessibilityService {
    private static volatile VyronAccessibilityService active;

    static VyronAccessibilityService current() { return active; }

    @Override protected void onServiceConnected() { super.onServiceConnected(); active=this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* Only inspect on request. */ }
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { if(active==this)active=null; super.onDestroy(); }

    JSONObject perform(String action, String value) {
        if(Looper.myLooper()==Looper.getMainLooper())return performOnMain(action,value);
        AtomicReference<JSONObject> result=new AtomicReference<>();
        CountDownLatch done=new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(()->{
            try { result.set(performOnMain(action,value)); }
            finally { done.countDown(); }
        });
        try { if(!done.await(4,TimeUnit.SECONDS))return status("error","Phone UI did not respond."); }
        catch(InterruptedException ignored){Thread.currentThread().interrupt();return status("error","Interrupted.");}
        return result.get()==null?status("error","Phone UI unavailable."):result.get();
    }

    private JSONObject performOnMain(String action,String value) {
        try {
            switch(action) {
                case "home": return global(GLOBAL_ACTION_HOME,"Opened Home.");
                case "back": return global(GLOBAL_ACTION_BACK,"Went back.");
                case "recents": return global(GLOBAL_ACTION_RECENTS,"Opened recent apps.");
                case "notifications": return global(GLOBAL_ACTION_NOTIFICATIONS,"Opened notifications.");
                case "quick_settings": return global(GLOBAL_ACTION_QUICK_SETTINGS,"Opened quick settings.");
                case "read_screen": return readScreen();
                case "tap_text": return tapText(value);
                case "scroll_forward": return scroll(true);
                case "scroll_backward": return scroll(false);
                case "type_text": return typeText(value);
                default: return status("error","Unknown phone action.");
            }
        } catch(Exception ignored){return status("error","Android could not perform that UI action.");}
    }

    private JSONObject global(int action,String message){
        return performGlobalAction(action)?status("ok",message):status("error","Android did not perform that action.");
    }

    private JSONObject readScreen() throws Exception {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return status("error","No readable app window is active.");
        JSONArray items=new JSONArray();
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        while(!queue.isEmpty()&&items.length()<70){
            AccessibilityNodeInfo node=queue.removeFirst();
            if(!node.isPassword()){
                CharSequence text=node.getText();
                CharSequence label=node.getContentDescription();
                String visible=text==null?"":text.toString().trim();
                if(visible.isEmpty()&&label!=null)visible=label.toString().trim();
                if(!visible.isEmpty())items.put(visible.length()>160?visible.substring(0,160):visible);
            }
            for(int i=0;i<node.getChildCount();i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)queue.add(child);
            }
            if(node!=root)node.recycle();
        }
        JSONObject result=status("ok","Visible screen text only; password fields omitted.");
        result.put("app",root.getPackageName()==null?"":root.getPackageName().toString());
        result.put("visible_text",items);
        root.recycle();
        return result;
    }

    private JSONObject tapText(String label) {
        if(label==null||label.trim().isEmpty())return status("error","Tell me which visible label to tap.");
        String lower=label.trim().toLowerCase(Locale.ROOT);
        String[] sensitive={"pay","purchase","buy","delete","remove account","transfer",
                "send","submit","confirm","authorize","accept","agree","install",
                "uninstall","allow","grant"};
        for(String word:sensitive)if(lower.contains(word))
            return status("manual_action_required","Tap sensitive actions yourself on the phone.");
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return status("error","No app window is active.");
        AccessibilityNodeInfo target=find(root,lower,false);
        if(target==null)target=find(root,lower,true);
        boolean clicked=false;
        for(AccessibilityNodeInfo node=target;node!=null;node=node.getParent()){
            if(node.isClickable()){clicked=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);break;}
        }
        root.recycle();
        return status(clicked?"ok":"error",clicked?"Tapped "+label:"Could not tap "+label);
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String label,boolean partial) {
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        int visited=0;
        while(!queue.isEmpty()&&visited++<300){
            AccessibilityNodeInfo node=queue.removeFirst();
            CharSequence text=node.getText(),description=node.getContentDescription();
            String shown=((text==null?"":text.toString())+" "+
                    (description==null?"":description.toString())).trim().toLowerCase(Locale.ROOT);
            if(!node.isPassword()&&(partial?shown.contains(label):shown.equals(label)))return node;
            for(int i=0;i<node.getChildCount();i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)queue.add(child);
            }
        }
        return null;
    }

    private JSONObject scroll(boolean forward) {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return status("error","No app window is active.");
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        while(!queue.isEmpty()){
            AccessibilityNodeInfo node=queue.removeFirst();
            if(node.isScrollable()){
                boolean success=node.performAction(forward?
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                root.recycle();
                return status(success?"ok":"error",success?"Scrolled.":"Cannot scroll further.");
            }
            for(int i=0;i<node.getChildCount();i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)queue.add(child);
            }
        }
        root.recycle();
        return status("error","No scrollable content found.");
    }

    private JSONObject typeText(String text) {
        if(text==null||text.isEmpty())return status("error","No text was supplied.");
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return status("error","No app window is active.");
        AccessibilityNodeInfo focused=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if(focused==null||!focused.isEditable()||focused.isPassword()){
            root.recycle();
            return status("error","Focus an ordinary text field first; password fields are excluded.");
        }
        Bundle args=new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        boolean success=focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
        root.recycle();
        return status(success?"ok":"error",success?"Text entered. Review before sending.":
                "Could not enter text in this field.");
    }

    private static JSONObject status(String code,String detail) {
        JSONObject result=new JSONObject();
        try{return result.put("status",code).put("detail",detail);}
        catch(Exception ignored){return result;}
    }
}
