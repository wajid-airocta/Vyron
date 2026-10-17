package com.airocta.vyron;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit, bounded device actions available to the live voice assistant. */
final class PhoneTools {
    private final MainActivity activity;
    PhoneTools(MainActivity activity) { this.activity=activity; }

    static JSONArray declarations() throws Exception {
        JSONArray result=new JSONArray();
        result.put(function("get_phone_status","Read live CPU, RAM, storage, battery, network, VYRON process and permission data.",new JSONObject()));
        result.put(function("get_recent_apps","Read today's app usage history if Usage Access is enabled. This is not a live process list.",new JSONObject()));
        result.put(function("list_apps","Find installed launchable apps by name.",new JSONObject()
                .put("query",string("Optional app name filter"))));
        result.put(function("open_app","Open an installed app by its visible name. Ask for clarification when matches are ambiguous.",new JSONObject()
                .put("name",string("App name to open")),"name"));
        result.put(function("control_phone","Control the visible phone interface only when the user explicitly asks. Requires VYRON Accessibility. Use action home, back, recents, notifications, quick_settings, read_screen, tap_text, scroll_forward, scroll_backward, or type_text. Never approve payments, delete data, or grant permissions.",new JSONObject()
                .put("action",string("Phone action"))
                .put("value",string("Visible label or text where needed")),"action"));
        result.put(function("open_permission_settings","Open Android settings so the user can grant an optional permission. This tool cannot grant the permission.",new JSONObject()
                .put("kind",string("usage, accessibility, or app")),"kind"));
        return result;
    }

    private static JSONObject string(String description) throws Exception {
        return new JSONObject().put("type","STRING").put("description",description);
    }
    private static JSONObject function(String name,String description,JSONObject properties,String... required) throws Exception {
        JSONObject parameters=new JSONObject().put("type","OBJECT").put("properties",properties);
        if(required.length>0){JSONArray names=new JSONArray();for(String item:required)names.put(item);parameters.put("required",names);}
        return new JSONObject().put("name",name).put("description",description).put("parameters",parameters);
    }

    JSONObject execute(String name,JSONObject args) {
        try {
            switch(name){
                case "get_phone_status": return PhoneSystemRepository.snapshot(activity);
                case "get_recent_apps": return PhoneSystemRepository.recentActivity(activity);
                case "list_apps": return listApps(args.optString("query",""));
                case "open_app": return openApp(args.optString("name",""));
                case "control_phone": {
                    VyronAccessibilityService service=VyronAccessibilityService.current();
                    return service==null?status("permission_required","Enable VYRON Accessibility in Android Settings."):
                            service.perform(args.optString("action",""),args.optString("value",""));
                }
                case "open_permission_settings": return openPermission(args.optString("kind",""));
                default: return status("error","Unknown phone tool.");
            }
        }catch(Exception error){return status("error","Phone action unavailable on this device.");}
    }

    private List<ResolveInfo> launchable() {
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=activity.getPackageManager().queryIntentActivities(query,0);
        apps.sort(Comparator.comparing(app->label(app).toLowerCase(Locale.ROOT)));
        return apps;
    }
    private String label(ResolveInfo app){return app.loadLabel(activity.getPackageManager()).toString();}
    private JSONObject listApps(String query) throws Exception {
        String needle=query.trim().toLowerCase(Locale.ROOT);
        JSONArray found=new JSONArray();
        for(ResolveInfo app:launchable()){
            String visible=label(app);
            if(!visible.toLowerCase(Locale.ROOT).contains(needle))continue;
            found.put(new JSONObject().put("name",visible).put("package",app.activityInfo.packageName));
            if(found.length()>=50)break;
        }
        return new JSONObject().put("apps",found).put("note","Only launchable apps visible to Android are listed.");
    }
    private JSONObject openApp(String name) throws Exception {
        String needle=name.trim().toLowerCase(Locale.ROOT);
        if(needle.isEmpty())return status("error","Specify an app name.");
        List<ResolveInfo> exact=new ArrayList<>(),partial=new ArrayList<>();
        for(ResolveInfo app:launchable()){
            String visible=label(app).toLowerCase(Locale.ROOT);
            if(visible.equals(needle)||app.activityInfo.packageName.equalsIgnoreCase(needle))exact.add(app);
            else if(visible.contains(needle))partial.add(app);
        }
        List<ResolveInfo> matches=exact.isEmpty()?partial:exact;
        if(matches.isEmpty())return status("not_found","No launchable app matches "+name+".");
        if(matches.size()>1){
            JSONArray choices=new JSONArray();
            for(int i=0;i<Math.min(matches.size(),10);i++)choices.put(label(matches.get(i)));
            return new JSONObject().put("status","ambiguous").put("matches",choices);
        }
        ResolveInfo app=matches.get(0);
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(app.activityInfo.packageName,app.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        JSONObject result=onMain(()->{
            activity.startActivity(intent);
            return status("ok","Opened "+label(app)+".");
        });
        return result;
    }
    private JSONObject openPermission(String kind) {
        Intent intent;
        switch(kind.toLowerCase(Locale.ROOT)){
            case "usage": intent=new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);break;
            case "accessibility": intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);break;
            case "app": intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+activity.getPackageName()));break;
            default:return status("error","Choose usage, accessibility, or app.");
        }
        return onMain(()->{activity.startActivity(intent);return status("ok","Opened Android Settings. The phone owner must grant access there.");});
    }
    private interface Action { JSONObject run() throws Exception; }
    private JSONObject onMain(Action action) {
        if(Looper.myLooper()==Looper.getMainLooper()){
            try{return action.run();}catch(Exception ignored){return status("error","Could not open that screen.");}
        }
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<JSONObject> result=new AtomicReference<>();
        new Handler(Looper.getMainLooper()).post(()->{
            try{result.set(action.run());}catch(Exception ignored){result.set(status("error","Could not open that screen."));}
            finally{done.countDown();}
        });
        try{if(!done.await(4,TimeUnit.SECONDS))return status("error","Phone did not respond.");}
        catch(InterruptedException ignored){Thread.currentThread().interrupt();return status("error","Interrupted.");}
        return result.get();
    }
    private static JSONObject status(String code,String detail){
        JSONObject result=new JSONObject();
        try{return result.put("status",code).put("detail",detail);}catch(Exception ignored){return result;}
    }
}
