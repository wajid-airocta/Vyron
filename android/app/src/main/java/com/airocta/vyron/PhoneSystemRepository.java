package com.airocta.vyron;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.FileReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reads the phone information Android actually exposes to a regular app. */
final class PhoneSystemRepository {
    private static long lastCpuTotal, lastCpuIdle, lastTrafficAt, lastRx, lastTx;
    private static Double lastCpuPercent, rxPerSecond, txPerSecond;
    private PhoneSystemRepository() {}

    static boolean hasUsageAccess(Context context) {
        AppOpsManager ops=(AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);
        return ops!=null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),context.getPackageName())==AppOpsManager.MODE_ALLOWED;
    }

    static boolean hasAccessibility(Context context) {
        String enabled=Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName service=new ComponentName(context,VyronAccessibilityService.class);
        return enabled!=null && (enabled.contains(service.flattenToString())
                || enabled.contains(service.flattenToShortString()));
    }

    static JSONObject permissions(Context context) {
        JSONObject out=new JSONObject();
        try {
            out.put("microphone",context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    ==PackageManager.PERMISSION_GRANTED);
            out.put("location",context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    ==PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED);
            out.put("usage_access",hasUsageAccess(context));
            out.put("accessibility_control",hasAccessibility(context));
        } catch(Exception ignored) {}
        return out;
    }

    static synchronized JSONObject snapshot(Context context) {
        JSONObject out=new JSONObject();
        try {
            out.put("device",Build.MANUFACTURER+" "+Build.MODEL);
            out.put("android_version",Build.VERSION.RELEASE);
            out.put("cpu_cores",Runtime.getRuntime().availableProcessors());
            out.put("cpu_architecture",Build.SUPPORTED_ABIS.length>0?Build.SUPPORTED_ABIS[0]:"unknown");
            Double cpu=sampleCpu();
            out.put("system_cpu_percent",cpu==null?JSONObject.NULL:Math.round(cpu));
            out.put("vyron_process_id",Process.myPid());
            out.put("vyron_process_memory_mb",Math.round(Debug.getPss()/1024.0));
            out.put("vyron_cpu_time_seconds",Process.getElapsedCpuTime()/1000);
            out.put("uptime_minutes",SystemClock.elapsedRealtime()/60000);
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager!=null){
                ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memory);
                out.put("ram_total_gb",round1(memory.totalMem/1073741824.0));
                out.put("ram_available_gb",round1(memory.availMem/1073741824.0));
                out.put("ram_used_percent",memory.totalMem==0?JSONObject.NULL:
                        Math.round(100.0*(memory.totalMem-memory.availMem)/memory.totalMem));
                List<ActivityManager.RunningAppProcessInfo> visible=manager.getRunningAppProcesses();
                JSONArray processes=new JSONArray();
                if(visible!=null)for(ActivityManager.RunningAppProcessInfo process:visible){
                    if(processes.length()>=12)break;
                    processes.put(new JSONObject().put("name",process.processName)
                            .put("pid",process.pid));
                }
                out.put("processes_visible_to_app",processes);
                out.put("process_visibility_note","Android may hide other apps' running processes.");
            }
            StatFs storage=new StatFs(Environment.getDataDirectory().getPath());
            out.put("storage_total_gb",round1(storage.getTotalBytes()/1073741824.0));
            out.put("storage_available_gb",round1(storage.getAvailableBytes()/1073741824.0));
            Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(battery!=null){
                int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
                int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
                out.put("battery_percent",level<0||scale<=0?JSONObject.NULL:level*100/scale);
                out.put("battery_temperature_c",round1(battery.getIntExtra(
                        BatteryManager.EXTRA_TEMPERATURE,0)/10.0));
                out.put("battery_voltage_mv",battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0));
                int status=battery.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
                out.put("charging",status==BatteryManager.BATTERY_STATUS_CHARGING
                        ||status==BatteryManager.BATTERY_STATUS_FULL);
            }
            ConnectivityManager network=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities caps=network==null?null:network.getNetworkCapabilities(network.getActiveNetwork());
            String transport="offline";
            if(caps!=null){
                if(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))transport="Wi-Fi";
                else if(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))transport="cellular";
                else if(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))transport="Ethernet";
                else if(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))transport="VPN";
            }
            out.put("network_transport",transport);
            out.put("internet_validated",caps!=null&&caps.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED));
            out.put("network_metered",network!=null&&network.isActiveNetworkMetered());
            if(caps!=null){
                out.put("link_downstream_estimate_mbps",round1(
                        caps.getLinkDownstreamBandwidthKbps()/1000.0));
                out.put("link_upstream_estimate_mbps",round1(
                        caps.getLinkUpstreamBandwidthKbps()/1000.0));
            }
            long rx=TrafficStats.getTotalRxBytes(),tx=TrafficStats.getTotalTxBytes();
            long now=SystemClock.elapsedRealtime();
            if(rx!=TrafficStats.UNSUPPORTED&&tx!=TrafficStats.UNSUPPORTED){
                out.put("network_received_since_boot_mb",round1(rx/1048576.0));
                out.put("network_sent_since_boot_mb",round1(tx/1048576.0));
                if(lastTrafficAt>0&&now>lastTrafficAt){
                    rxPerSecond=Math.max(0,(rx-lastRx)*1000.0/(now-lastTrafficAt)/1024);
                    txPerSecond=Math.max(0,(tx-lastTx)*1000.0/(now-lastTrafficAt)/1024);
                }
                lastTrafficAt=now;lastRx=rx;lastTx=tx;
                out.put("network_receive_kb_per_second",rxPerSecond==null?JSONObject.NULL:round1(rxPerSecond));
                out.put("network_send_kb_per_second",txPerSecond==null?JSONObject.NULL:round1(txPerSecond));
            }
            out.put("permissions",permissions(context));
            out.put("sampled_at",DateFormat.getTimeInstance().format(new Date()));
        } catch(Exception error){
            try{out.put("partial_data_reason","Some Android metrics are unavailable on this device.");}
            catch(Exception ignored){}
        }
        return out;
    }

    static JSONObject recentActivity(Context context) {
        JSONObject out=new JSONObject();
        try {
            if(!hasUsageAccess(context))return out.put("permission_required","Usage Access");
            UsageStatsManager manager=(UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar day=Calendar.getInstance();
            day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);
            day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
            List<UsageStats> stats=manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                    day.getTimeInMillis(),System.currentTimeMillis());
            if(stats==null)stats=new ArrayList<>();
            stats.sort(Comparator.comparingLong(UsageStats::getLastTimeUsed).reversed());
            JSONArray apps=new JSONArray();
            for(UsageStats item:stats){
                if(item.getLastTimeUsed()<=0||apps.length()>=12)continue;
                apps.put(new JSONObject().put("package",item.getPackageName())
                        .put("foreground_minutes_today",item.getTotalTimeInForeground()/60000)
                        .put("last_used",DateFormat.getTimeInstance().format(new Date(item.getLastTimeUsed()))));
            }
            out.put("recent_apps",apps);
            out.put("note","Usage history is not a live process list.");
        } catch(Exception ignored){
            try{out.put("error","Usage data unavailable on this phone.");}catch(Exception ignoredAgain){}
        }
        return out;
    }

    static String display(Context context) {
        JSONObject data=snapshot(context),history=recentActivity(context);
        StringBuilder text=new StringBuilder();
        text.append("DEVICE  ").append(data.optString("device","unknown"))
                .append("  ·  Android ").append(data.optString("android_version","?"));
        text.append("\n\nCPU  ").append(data.optInt("cpu_cores")).append(" cores  ·  ")
                .append(data.optString("cpu_architecture","?"));
        text.append("\nSYSTEM LOAD  ").append(data.isNull("system_cpu_percent")
                ?"restricted by Android":data.optString("system_cpu_percent")+"%");
        text.append("\nVYRON CPU TIME  ").append(data.optLong("vyron_cpu_time_seconds")).append("s");
        text.append("\n\nMEMORY  ").append(data.optString("ram_used_percent","?"))
                .append("% used  ·  ").append(data.optString("ram_available_gb","?"))
                .append(" / ").append(data.optString("ram_total_gb","?")) .append(" GB available");
        text.append("\nVYRON PROCESS  PID ").append(data.optInt("vyron_process_id"))
                .append("  ·  ").append(data.optLong("vyron_process_memory_mb")).append(" MB");
        text.append("\n\nSTORAGE  ").append(data.optString("storage_available_gb","?"))
                .append(" / ").append(data.optString("storage_total_gb","?")) .append(" GB available");
        text.append("\n\nBATTERY  ").append(data.optString("battery_percent","?"))
                .append("%  ·  ").append(data.optBoolean("charging")?"charging":"not charging")
                .append("\nTEMPERATURE  ").append(data.optString("battery_temperature_c","?"))
                .append("°C  ·  ").append(data.optInt("battery_voltage_mv")).append(" mV");
        text.append("\n\nNETWORK  ").append(data.optString("network_transport","offline"))
                .append(data.optBoolean("internet_validated")?"  ·  internet":"  ·  no verified internet")
                .append("\nSPEED  ↓ ").append(data.optString("network_receive_kb_per_second","—"))
                .append(" KB/s  ↑ ").append(data.optString("network_send_kb_per_second","—"))
                .append(" KB/s")
                .append("\nTRANSFER SINCE BOOT  ↓ ")
                .append(data.optString("network_received_since_boot_mb","—"))
                .append(" MB  ↑ ").append(data.optString("network_sent_since_boot_mb","—"))
                .append(" MB");
        JSONArray recent=history.optJSONArray("recent_apps");
        text.append("\n\nRECENT APPS");
        if(recent==null)text.append("  ·  Usage Access required");
        else for(int i=0;i<Math.min(recent.length(),6);i++){
            JSONObject app=recent.optJSONObject(i);
            if(app!=null)text.append("\n").append(app.optString("package"))
                    .append("  ·  ").append(app.optLong("foreground_minutes_today"))
                    .append(" min today");
        }
        text.append("\n\nOTHER PROCESSES  Android limits visibility for regular apps.");
        JSONArray visible=data.optJSONArray("processes_visible_to_app");
        if(visible!=null)for(int i=0;i<visible.length();i++){
            JSONObject process=visible.optJSONObject(i);
            if(process!=null)text.append("\n").append(process.optString("name"))
                    .append("  ·  PID ").append(process.optInt("pid"));
        }
        JSONObject grants=data.optJSONObject("permissions");
        if(grants!=null)text.append("\n\nPERMISSIONS  mic ")
                .append(grants.optBoolean("microphone")?"ON":"OFF")
                .append("  ·  location ").append(grants.optBoolean("location")?"ON":"OFF")
                .append("\nUSAGE ACCESS  ").append(grants.optBoolean("usage_access")?"ON":"OFF")
                .append("  ·  ACCESSIBILITY  ").append(grants.optBoolean("accessibility_control")?"ON":"OFF");
        text.append("\n\nSAMPLED  ").append(data.optString("sampled_at","—"));
        return text.toString();
    }

    private static double round1(double value){return Math.round(value*10.0)/10.0;}

    private static Double sampleCpu() {
        try(BufferedReader reader=new BufferedReader(new FileReader("/proc/stat"))){
            String[] fields=reader.readLine().trim().split("\\s+");
            if(!"cpu".equals(fields[0]))return null;
            long total=0;
            for(int i=1;i<Math.min(fields.length,9);i++)total+=Long.parseLong(fields[i]);
            long idle=Long.parseLong(fields[4])+Long.parseLong(fields[5]);
            if(lastCpuTotal>0&&total>lastCpuTotal){
                lastCpuPercent=100.0*(1.0-(idle-lastCpuIdle)*1.0/(total-lastCpuTotal));
                lastCpuPercent=Math.max(0,Math.min(100,lastCpuPercent));
            }
            lastCpuTotal=total;lastCpuIdle=idle;
            return lastCpuPercent;
        }catch(Exception ignored){return null;}
    }
}
