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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView web;
    private LinearLayout remotePanel;
    private LinearLayout chatPanel;
    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText promptInput;
    private Button sendButton;
    private TextView keyStatus;
    private final JSONArray conversation = new JSONArray();
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
        Button chat=button("CHAT");
        chat.setOnClickListener(v->showMode(false));
        top.addView(chat);
        Button remote=button("MAC");
        remote.setOnClickListener(v->showMode(true));
        top.addView(remote);
        top.addView(api);
        root.addView(top);
        chatPanel=new LinearLayout(this);
        chatPanel.setOrientation(LinearLayout.VERTICAL);
        TextView introduction=new TextView(this);
        introduction.setText("VYRON  •  Phone assistant\nAsk a question below. Mac control is available in the MAC tab.");
        introduction.setTextColor(Color.rgb(126,225,226));
        introduction.setTextSize(16);
        introduction.setPadding(20,24,20,18);
        chatPanel.addView(introduction);
        keyStatus=new TextView(this);
        keyStatus.setTextColor(Color.rgb(91,200,143));
        keyStatus.setTextSize(13);
        keyStatus.setPadding(20,0,20,12);
        chatPanel.addView(keyStatus);
        updateKeyStatus();
        chatScroll=new ScrollView(this);
        messages=new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(16,8,16,8);
        chatScroll.addView(messages);
        chatPanel.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composer=new LinearLayout(this);
        composer.setPadding(8,8,8,12);
        promptInput=new EditText(this);
        promptInput.setHint("Ask VYRON…");
        promptInput.setTextColor(Color.WHITE);
        promptInput.setHintTextColor(Color.rgb(143,179,185));
        promptInput.setMaxLines(4);
        composer.addView(promptInput,new LinearLayout.LayoutParams(0,-2,1));
        sendButton=button("SEND");
        sendButton.setOnClickListener(v->sendChat());
        composer.addView(sendButton);
        chatPanel.addView(composer);
        root.addView(chatPanel,new LinearLayout.LayoutParams(-1,0,1));
        remotePanel=new LinearLayout(this);
        remotePanel.setOrientation(LinearLayout.VERTICAL);
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
        remotePanel.addView(connect);
        status=new TextView(this);
        status.setTextColor(Color.rgb(134,206,214));
        status.setTextSize(12);
        status.setPadding(13,2,13,4);
        status.setText("Connect to the HTTPS address shown by VYRON on your Mac.");
        remotePanel.addView(status);
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
        remotePanel.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(remotePanel,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        showMode(false);
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
    private void showMode(boolean remote) {
        chatPanel.setVisibility(remote ? View.GONE : View.VISIBLE);
        remotePanel.setVisibility(remote ? View.VISIBLE : View.GONE);
        if (remote && web.getUrl() == null && !urlInput.getText().toString().trim().isEmpty()) connect();
    }
    private String activeKey() {
        String override=SecretStore.load(this);
        return override == null || override.trim().isEmpty() ? BuildConfig.STARTER_GEMINI_API_KEY : override;
    }
    private void updateKeyStatus() {
        if(SecretStore.load(this)!=null) keyStatus.setText("GEMINI · PERSONAL KEY");
        else if(!BuildConfig.STARTER_GEMINI_API_KEY.trim().isEmpty()) keyStatus.setText("GEMINI · TEST STARTER READY");
        else keyStatus.setText("GEMINI · ADD A KEY IN API SETTINGS");
    }
    private void addMessage(String speaker,String value) {
        TextView item=new TextView(this);
        item.setText(speaker+"\n"+value);
        item.setTextColor(Color.rgb(218,247,248));
        item.setTextSize(17);
        item.setPadding(14,12,14,16);
        messages.addView(item,new LinearLayout.LayoutParams(-1,-2));
        chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
    }
    private void sendChat() {
        String prompt=promptInput.getText().toString().trim();
        if(prompt.isEmpty())return;
        String key=activeKey();
        if(key==null || key.trim().isEmpty()){
            showApiSettings();
            return;
        }
        promptInput.setText("");
        addMessage("YOU",prompt);
        sendButton.setEnabled(false);
        try {
            conversation.put(GeminiClient.turn("user",prompt));
        } catch(Exception ignored) {}
        JSONArray history=new JSONArray();
        int start=Math.max(0,conversation.length()-12);
        for(int i=start;i<conversation.length();i++)history.put(conversation.opt(i));
        new Thread(()->{
            String answer=GeminiClient.ask(key,history);
            runOnUiThread(()->{
                addMessage("VYRON",answer);
                boolean failed=answer.startsWith("Gemini error:")
                        || answer.startsWith("Gemini is unavailable")
                        || answer.startsWith("Gemini returned");
                if(failed) conversation.remove(conversation.length()-1);
                else
                    try {conversation.put(GeminiClient.turn("model",answer));}catch(Exception ignored){}
                sendButton.setEnabled(true);
            });
        },"vyron-phone-chat").start();
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
        field.setHint("Paste your Gemini API key");
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setPadding(24,16,24,16);
        String source=SecretStore.load(this)!=null ? "Your encrypted phone override is active."
                : BuildConfig.STARTER_GEMINI_API_KEY.trim().isEmpty() ? "No starter key is included in this build."
                : "The local test starter key is active.";
        new AlertDialog.Builder(this).setTitle("Gemini API key")
                .setMessage(source+" Enter a key to override it on this phone. MAC mode uses the paired Mac unless an override is saved.")
                .setView(field)
                .setPositiveButton("SAVE OVERRIDE",(d,w)->{
                    String value=field.getText().toString().trim();
                    if(value.isEmpty()){
                        Toast.makeText(this,"Enter a key or choose USE STARTER.",Toast.LENGTH_LONG).show();
                        return;
                    }
                    try { SecretStore.save(this,value); updateKeyStatus(); Toast.makeText(this,"Phone API override saved.",Toast.LENGTH_SHORT).show(); }
                    catch(Exception e){Toast.makeText(this,"Could not save the key securely.",Toast.LENGTH_LONG).show();}
                })
                .setNeutralButton("USE STARTER",(d,w)->{
                    try { SecretStore.save(this,null); updateKeyStatus(); Toast.makeText(this,"Using the starter key in CHAT mode.",Toast.LENGTH_SHORT).show(); }
                    catch(Exception e){Toast.makeText(this,"Could not clear the override.",Toast.LENGTH_LONG).show();}
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
    @Override public void onBackPressed() { if(remotePanel.getVisibility()==View.VISIBLE && web.canGoBack())web.goBack();else super.onBackPressed(); }
    @Override protected void onDestroy(){web.destroy();super.onDestroy();}

    private final class Bridge {
        @JavascriptInterface public boolean hasOverride(){return SecretStore.load(MainActivity.this)!=null;}
        @JavascriptInterface public void openSettings(){runOnUiThread(()->showApiSettings());}
        @JavascriptInterface public void sendOverride(String prompt){
            String key=SecretStore.load(MainActivity.this);
            if(key==null || key.trim().isEmpty()){reply("No override key saved. Use the Mac key or add one in API settings.");return;}
            new Thread(()->{
                String answer;
                try {
                    answer=GeminiClient.ask(key,new JSONArray().put(GeminiClient.turn("user",prompt)));
                } catch(Exception e){answer="Gemini is unavailable. Check your connection and override key.";}
                reply(answer);
            },"vyron-gemini").start();
        }
        private void reply(String answer){
            runOnUiThread(()->web.evaluateJavascript("window.vyronOverrideResult("+JSONObject.quote(answer)+")",null));
        }
    }
}
