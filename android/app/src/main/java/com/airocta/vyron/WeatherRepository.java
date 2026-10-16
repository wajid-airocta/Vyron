package com.airocta.vyron;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

final class WeatherRepository {
    static final class Snapshot {
        final String temperature, condition, wind, humidity, updated;
        Snapshot(String temperature,String condition,String wind,String humidity,String updated) {
            this.temperature=temperature;this.condition=condition;this.wind=wind;
            this.humidity=humidity;this.updated=updated;
        }
    }
    private static volatile Snapshot latest;
    private static volatile String message="Loading current weather…";
    private WeatherRepository() {}
    static Snapshot current(){return latest;}
    static String status(){return message;}
    static void refresh(Context context,Runnable done){
        new Thread(()->{
            try {
                if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                        && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
                    message="Open VYRON on your phone to allow location.";
                    latest=null;return;
                }
                LocationManager manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
                Location location=null;
                for(String provider:manager.getProviders(true)){
                    Location candidate=manager.getLastKnownLocation(provider);
                    if(candidate!=null && (location==null || candidate.getTime()>location.getTime())) location=candidate;
                }
                if(location==null || System.currentTimeMillis()-location.getTime()>6*60*60*1000L) {
                    message="Waiting for a recent phone location. Open VYRON on the phone.";
                    latest=null;return;
                }
                String query="latitude="+URLEncoder.encode(Double.toString(location.getLatitude()),"UTF-8")
                        +"&longitude="+URLEncoder.encode(Double.toString(location.getLongitude()),"UTF-8")
                        +"&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m";
                HttpsURLConnection connection=(HttpsURLConnection)new URL("https://api.open-meteo.com/v1/forecast?"+query).openConnection();
                connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
                JSONObject data;
                try(InputStream in=connection.getInputStream()){
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                    byte[] buffer=new byte[8192];
                    int n;
                    while((n=in.read(buffer))!=-1 && out.size()<1024*1024)
                        out.write(buffer,0,Math.min(n,1024*1024-out.size()));
                    data=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                }
                connection.disconnect();
                JSONObject now=data.getJSONObject("current");
                int code=now.optInt("weather_code",-1);
                String condition;
                if(code==0)condition="Clear";
                else if(code<=2)condition="Partly cloudy";
                else if(code==3)condition="Overcast";
                else if(code==45||code==48)condition="Fog";
                else if(code>=51&&code<=57)condition="Drizzle";
                else if((code>=61&&code<=67)||(code>=80&&code<=82))condition="Rain";
                else if((code>=71&&code<=77)||(code>=85&&code<=86))condition="Snow";
                else if(code>=95)condition="Thunderstorm";
                else condition="Current conditions";
                latest=new Snapshot(
                        Integer.toString((int)Math.round(now.getDouble("temperature_2m"))),
                        condition,
                        Integer.toString((int)Math.round(now.optDouble("wind_speed_10m",0))),
                        Integer.toString(now.optInt("relative_humidity_2m",0)),
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
                message="Weather ready";
            }catch(Exception ignored){message="Weather unavailable. Check your phone connection.";latest=null;}
            finally{new Handler(Looper.getMainLooper()).post(done);}
        },"vyron-weather").start();
    }
}
