package com.airocta.vyron;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.InputType;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.HttpsURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView web;
    private EditText urlInput;
    private TextView status;
    private String allowedHost;
    private byte[] pinnedHash;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private PermissionRequest webPermission;
    private ValueCallback<Uri[]> fileCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(2,7,10));
        getWindow().setNavigationBarColor(Color.rgb(2,7,10));
        try (InputStream in=getResources().openRawResource(R.raw.vyron_mac)) {
            X509Certificate cert=(X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            pinnedHash=MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        } catch (Exception error) {
            pinnedHash=new byte[0];
        }
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(2,7,10));
        LinearLayout top=new LinearLayout(this);
        top.setPadding(10,6,10,6);
        top.setBackgroundColor(Color.rgb(21,36,44));
        TextView title=new TextView(this);
        title.setText("◈  VYRON");
        title.setTextColor(Color.rgb(202,241,245));
        title.setTextSize(18);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button api=button("API");
        api.setOnClickListener(v->showApiSettings());
        top.addView(api);
        root.addView(top);
        LinearLayout connect=new LinearLayout(this);
        connect.setPadding(8,4,8,4);
        urlInput=new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint("https://Mac-IP:8000");
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.rgb(149,180,187));
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(getPreferences(0).getString("url",""));
        connect.addView(urlInput,new LinearLayout.LayoutParams(0,-2,1));
        Button go=button("CONNECT");
        go.setOnClickListener(v->connect());
        connect.addView(go);
        root.addView(connect);
        status=new TextView(this);
        status.setTextColor(Color.rgb(134,206,214));
        status.setTextSize(12);
        status.setPadding(13,2,13,4);
        status.setText("Connect to the HTTPS address shown by VYRON on your Mac.");
        root.addView(status);
        web=new WebView(this);
        web.setBackgroundColor(Color.rgb(2,7,10));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(),"VyronAndroid");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri=request.getUrl();
                if ("https".equals(uri.getScheme()) && allowedHost!=null && allowedHost.equals(uri.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); } catch(Exception ignored) {}
                return true;
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                try {
                    Bundle bundle=SslCertificate.saveState(error.getCertificate());
                    byte[] raw=bundle.getByteArray("x509-certificate");
                    X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509")
                            .generateCertificate(new java.io.ByteArrayInputStream(raw));
                    byte[] hash=MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
                    if (pinnedHash.length==32 && MessageDigest.isEqual(hash,pinnedHash)
                            && allowedHost!=null && allowedHost.equals(Uri.parse(error.getUrl()).getHost())) {
                        handler.proceed();
                        return;
                    }
                } catch (Exception ignored) {}
                handler.cancel();
                status.setText("Mac certificate changed. Rebuild VYRON Android with the new public certificate.");
            }
            @Override public void onPageFinished(WebView view,String url) {
                status.setText("Connected to VYRON. Pair using the Mac's Remote Control code.");
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) callback.invoke(origin,true,false);
                else { geoCallback=callback;geoOrigin=origin;requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},10); }
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                webPermission=request;
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);
            }
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,WebChromeClient.FileChooserParams params) {
                if(fileCallback!=null)fileCallback.onReceiveValue(null);
                fileCallback=callback;
                try {
                    Intent intent=params.createIntent();
                    startActivityForResult(intent,12);
                    return true;
                } catch(Exception error) {
                    fileCallback.onReceiveValue(null);
                    fileCallback=null;
                    status.setText("No file picker is available on this phone.");
                    return false;
                }
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        if(!urlInput.getText().toString().isBlank()) connect();
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},10);
    }

    private Button button(String label) {
        Button b=new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(218,247,248));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(30,70,81)));
        return b;
    }
    private void connect() {
        String value=urlInput.getText().toString().trim().replaceAll("/+$","");
        Uri uri=Uri.parse(value);
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getPort()<1) {
            status.setText("Enter the full HTTPS Mac address, including its port.");
            return;
        }
        allowedHost=uri.getHost();
        getPreferences(0).edit().putString("url",value).apply();
        web.loadUrl(value);
    }
    private void showApiSettings() {
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setHint("Optional Gemini API key");
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setPadding(24,16,24,16);
        new AlertDialog.Builder(this).setTitle("VYRON API source")
                .setMessage("With no override, VYRON uses the Gemini key on your paired Mac. An override stays encrypted on this phone.")
                .setView(field)
                .setPositiveButton("SAVE OVERRIDE",(d,w)->{
                    try { SecretStore.save(this,field.getText().toString().trim()); status.setText("API source updated."); }
                    catch(Exception e){status.setText("Could not save the override securely.");}
                })
                .setNeutralButton("USE MAC KEY",(d,w)->{
                    try { SecretStore.save(this,null); status.setText("Using the Mac's Gemini key."); }
                    catch(Exception e){status.setText("Could not clear the override.");}
                })
                .setNegativeButton("CANCEL",null).show();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        boolean granted=grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED;
        if(requestCode==10){
            if(geoCallback!=null){geoCallback.invoke(geoOrigin,granted,false);geoCallback=null;}
            if(granted) primeLocation();
        }
        if(requestCode==11 && webPermission!=null){if(granted)webPermission.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else webPermission.deny();webPermission=null;}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=12 || fileCallback==null)return;
        Uri[] result=null;
        if(resultCode==RESULT_OK && data!=null){
            if(data.getClipData()!=null){
                int n=data.getClipData().getItemCount();
                result=new Uri[n];
                for(int i=0;i<n;i++)result[i]=data.getClipData().getItemAt(i).getUri();
            }else if(data.getData()!=null)result=new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(result);
        fileCallback=null;
    }
    private void primeLocation(){
        try{
            LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);
            if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,location->{},android.os.Looper.getMainLooper());
        }catch(Exception ignored){}
    }
    @Override public void onBackPressed() { if(web.canGoBack())web.goBack();else super.onBackPressed(); }
    @Override protected void onDestroy(){web.destroy();super.onDestroy();}

    private final class Bridge {
        @JavascriptInterface public boolean hasOverride(){return SecretStore.load(MainActivity.this)!=null;}
        @JavascriptInterface public void openSettings(){runOnUiThread(()->showApiSettings());}
        @JavascriptInterface public void sendOverride(String prompt){
            String key=SecretStore.load(MainActivity.this);
            if(key==null || key.isBlank()){reply("No override key saved. Use the Mac key or add one in API settings.");return;}
            new Thread(()->{
                String answer;
                try {
                    URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
                    HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
                    c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setRequestMethod("POST");c.setDoOutput(true);
                    c.setRequestProperty("Content-Type","application/json");
                    c.setRequestProperty("x-goog-api-key",key);
                    JSONObject body=new JSONObject().put("contents",new JSONArray().put(
                            new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",prompt)))));
                    try(java.io.OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                    InputStream in=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();
                    byte[] data=readLimited(in);
                    JSONObject response=new JSONObject(new String(data,StandardCharsets.UTF_8));
                    if(c.getResponseCode()>=400) {
                        JSONObject detail=response.optJSONObject("error");
                        answer="Gemini request failed: "+(detail==null?"HTTP "+c.getResponseCode():detail.optString("message","HTTP "+c.getResponseCode()));
                    }
                    else {
                        JSONArray parts=response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts");
                        StringBuilder text=new StringBuilder();
                        for(int i=0;i<parts.length();i++)text.append(parts.getJSONObject(i).optString("text"));
                        answer=text.toString();
                    }
                    c.disconnect();
                } catch(Exception e){answer="Gemini is unavailable. Check your connection and override key.";}
                reply(answer);
            },"vyron-gemini").start();
        }
        private void reply(String answer){
            runOnUiThread(()->web.evaluateJavascript("window.vyronOverrideResult("+JSONObject.quote(answer)+")",null));
        }
    }
    private static byte[] readLimited(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[8192];
        int n;
        while((n=in.read(buffer))!=-1 && out.size()<1024*1024)
            out.write(buffer,0,Math.min(n,1024*1024-out.size()));
        return out.toByteArray();
    }
}
