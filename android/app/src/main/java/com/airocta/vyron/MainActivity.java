package com.airocta.vyron;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.BatteryManager;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView web;
    private LinearLayout root;
    private LinearLayout remotePanel;
    private LinearLayout chatPanel;
    private LinearLayout systemPanel;
    private TextView systemReadout;
    private final android.os.Handler systemHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshSystem=new Runnable(){@Override public void run(){
        if(systemReadout!=null && systemPanel!=null && systemPanel.getVisibility()==View.VISIBLE)
            new Thread(()->{String value=PhoneSystemRepository.display(MainActivity.this);
                runOnUiThread(()->{if(systemPanel.getVisibility()==View.VISIBLE)systemReadout.setText(value);});
            },"vyron-system-sample").start();
        systemHandler.postDelayed(this,3000);
    }};
    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText promptInput;
    private Button sendButton;
    private Button voiceButton;
    private ReactorView reactor;
    private TextView voiceStatus;
    private TextView userTranscript;
    private TextView assistantTranscript;
    private LiveVoiceSession liveSession;
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
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try (InputStream in=getResources().openRawResource(R.raw.vyron_mac)) {
            X509Certificate cert=(X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            pinnedHash=MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        } catch (Exception error) {
            pinnedHash=new byte[0];
        }
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFocusableInTouchMode(true);
        LinearLayout top=new LinearLayout(this);
        top.setPadding(16,8,12,2);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText("◈  VYRON");
        title.setTextColor(Color.rgb(0,220,244));
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView subtitle=new TextView(this);
        subtitle.setText("PERSONAL AUTONOMOUS INTELLIGENCE");
        subtitle.setTextColor(Color.rgb(80,154,171));
        subtitle.setTextSize(9);
        subtitle.setTypeface(android.graphics.Typeface.MONOSPACE);
        top.addView(subtitle);
        root.addView(top);
        chatPanel=new LinearLayout(this);
        chatPanel.setOrientation(LinearLayout.VERTICAL);
        keyStatus=new TextView(this);
        keyStatus.setTextColor(Color.rgb(77,170,182));
        keyStatus.setTextSize(10);
        keyStatus.setTypeface(android.graphics.Typeface.MONOSPACE);
        keyStatus.setGravity(android.view.Gravity.CENTER);
        keyStatus.setPadding(10,0,10,0);
        chatPanel.addView(keyStatus);
        updateKeyStatus();
        reactor=new ReactorView(this);
        chatPanel.addView(reactor,new LinearLayout.LayoutParams(-1,0,1));
        voiceStatus=new TextView(this);
        voiceStatus.setText("READY  ·  TAP TO SPEAK");
        voiceStatus.setTextColor(Color.rgb(0,220,244));
        voiceStatus.setTextSize(13);
        voiceStatus.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);
        voiceStatus.setGravity(android.view.Gravity.CENTER);
        chatPanel.addView(voiceStatus);
        userTranscript=new TextView(this);
        userTranscript.setTextColor(Color.rgb(126,198,211));
        userTranscript.setTextSize(14);
        userTranscript.setGravity(android.view.Gravity.CENTER);
        userTranscript.setPadding(18,6,18,2);
        userTranscript.setMaxLines(2);
        chatPanel.addView(userTranscript);
        assistantTranscript=new TextView(this);
        assistantTranscript.setTextColor(Color.rgb(218,247,248));
        assistantTranscript.setTextSize(16);
        assistantTranscript.setGravity(android.view.Gravity.CENTER);
        assistantTranscript.setPadding(18,4,18,8);
        assistantTranscript.setMaxLines(3);
        chatPanel.addView(assistantTranscript);
        voiceButton=button("●  START VOICE");
        voiceButton.setTextSize(16);
        voiceButton.setTextColor(Color.rgb(0,229,201));
        voiceButton.setOnClickListener(v->toggleVoice());
        chatPanel.addView(voiceButton,new LinearLayout.LayoutParams(-1,-2));
        chatScroll=new ScrollView(this);
        messages=new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(16,8,16,8);
        chatScroll.addView(messages);
        chatPanel.addView(chatScroll,new LinearLayout.LayoutParams(-1,dp(78)));
        chatScroll.setVisibility(View.GONE);
        LinearLayout composer=new LinearLayout(this);
        composer.setPadding(8,2,8,8);
        promptInput=new EditText(this);
        promptInput.setHint("Or type to VYRON…");
        promptInput.setTextColor(Color.WHITE);
        promptInput.setHintTextColor(Color.rgb(143,179,185));
        promptInput.setSingleLine(true);
        promptInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        promptInput.setOnEditorActionListener((view,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEND){sendChat();return true;}
            return false;
        });
        composer.addView(promptInput,new LinearLayout.LayoutParams(0,-2,1));
        sendButton=button("SEND");
        sendButton.setOnClickListener(v->sendChat());
        composer.addView(sendButton);
        chatPanel.addView(composer);
        root.addView(chatPanel,new LinearLayout.LayoutParams(-1,0,1));
        systemPanel=new LinearLayout(this);
        systemPanel.setOrientation(LinearLayout.VERTICAL);
        systemPanel.setPadding(dp(18),dp(10),dp(18),dp(6));
        TextView systemTitle=new TextView(this);
        systemTitle.setText("◈  PHONE SYSTEM  ·  LIVE");
        systemTitle.setTextColor(Color.rgb(0,220,244));
        systemTitle.setTextSize(16);
        systemTitle.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);
        systemPanel.addView(systemTitle);
        ScrollView systemScroll=new ScrollView(this);
        systemReadout=new TextView(this);
        systemReadout.setTextColor(Color.rgb(164,227,232));
        systemReadout.setTextSize(14);
        systemReadout.setTypeface(android.graphics.Typeface.MONOSPACE);
        systemReadout.setLineSpacing(dp(3),1.05f);
        systemReadout.setText("Reading phone status…");
        systemScroll.addView(systemReadout);
        systemPanel.addView(systemScroll,new LinearLayout.LayoutParams(-1,0,1));
        Button apps=button("OPEN APP");
        apps.setOnClickListener(v->showAppLauncher());
        systemPanel.addView(apps);
        Button controls=button("PHONE ACTIONS");
        controls.setOnClickListener(v->showPhoneActions());
        systemPanel.addView(controls);
        Button permissions=button("PERMISSIONS & PHONE CONTROL");
        permissions.setOnClickListener(v->showPermissionSetup());
        systemPanel.addView(permissions);
        root.addView(systemPanel,new LinearLayout.LayoutParams(-1,0,1));
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
        urlInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        urlInput.setOnEditorActionListener((view,action,event)->{
            if(action==EditorInfo.IME_ACTION_DONE){connect();return true;}
            return false;
        });
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
        web.setBackgroundColor(Color.BLACK);
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
        LinearLayout navigation=new LinearLayout(this);
        navigation.setGravity(android.view.Gravity.CENTER);
        String[] sections={"VOICE","SYSTEM","WEATHER","MAC","SETTINGS"};
        for(int i=0;i<sections.length;i++){
            final int section=i;
            TextView entry=new TextView(this);
            entry.setText(sections[i]);
            entry.setTextColor(i==0?Color.rgb(0,220,244):Color.rgb(99,161,176));
            entry.setTextSize(10);
            entry.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);
            entry.setGravity(android.view.Gravity.CENTER);
            entry.setMinHeight(dp(44));
            entry.setOnClickListener(v->openSection(section));
            navigation.addView(entry,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(navigation);
        setContentView(root);
        root.requestFocus();
        showMode(0);
        systemHandler.post(refreshSystem);
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},10);
    }

    private Button button(String label) {
        Button b=new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(145,225,234));
        b.setTextSize(11);
        b.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
        return b;
    }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private void hideKeyboard(){
        View focused=getCurrentFocus();
        if(focused!=null){
            InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(focused.getWindowToken(),0);
            focused.clearFocus();
        }
        if(root!=null)root.requestFocus();
    }
    private void openSection(int section){
        hideKeyboard();
        if(section==4){showApiSettings();return;}
        showMode(section==3?2:section==1?1:0);
        if(section==3)return;
        if(section==0){
            voiceStatus.setText(liveSession==null?"READY  ·  TAP TO SPEAK":"LIVE  ·  TAP TO END");
            return;
        }
        if(section==1){
            systemReadout.setText("Reading phone status…");
        } else {
            voiceStatus.setText("WEATHER  ·  CURRENT LOCATION");
            assistantTranscript.setText("Loading current conditions…");
            WeatherRepository.refresh(this,()->{
                WeatherRepository.Snapshot value=WeatherRepository.current();
                assistantTranscript.setText(value==null?WeatherRepository.status():
                        value.temperature+"°C  ·  "+value.condition+"  ·  wind "+value.wind+
                                " km/h  ·  humidity "+value.humidity+"%");
            });
        }
    }
    private void showMode(int mode) {
        hideKeyboard();
        if(mode==2) stopVoice();
        chatPanel.setVisibility(mode==0 ? View.VISIBLE : View.GONE);
        systemPanel.setVisibility(mode==1 ? View.VISIBLE : View.GONE);
        remotePanel.setVisibility(mode==2 ? View.VISIBLE : View.GONE);
        if (mode==2 && web.getUrl() == null && !urlInput.getText().toString().trim().isEmpty()) connect();
    }

    private void showAppLauncher(){
        EditText name=new EditText(this);
        name.setSingleLine(true);
        name.setHint("App name, e.g. Maps");
        name.setTextColor(Color.WHITE);
        LinearLayout holder=new LinearLayout(this);
        holder.setPadding(dp(20),0,dp(20),0);
        holder.addView(name,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle("Open an installed app").setView(holder)
                .setPositiveButton("OPEN",(dialog,which)->{
                    JSONObject args=new JSONObject();
                    try{args.put("name",name.getText().toString());}catch(Exception ignored){}
                    JSONObject result=new PhoneTools(this).execute("open_app",args);
                    if(!"ok".equals(result.optString("status")))
                        Toast.makeText(this,result.optString("detail",result.optJSONArray("matches")==null
                                ?"No matching app.":"Several apps match. Enter a more exact name."),Toast.LENGTH_LONG).show();
                }).setNegativeButton("CANCEL",null).show();
    }

    private void showPermissionSetup(){
        JSONObject grants=PhoneSystemRepository.permissions(this);
        String state="MICROPHONE  "+(grants.optBoolean("microphone")?"ON":"OFF")
                +"\nLOCATION  "+(grants.optBoolean("location")?"ON":"OFF")
                +"\nUSAGE ACCESS  "+(grants.optBoolean("usage_access")?"ON":"OFF")
                +"\nACCESSIBILITY  "+(grants.optBoolean("accessibility_control")?"ON":"OFF")
                +"\n\nAndroid requires you to grant Usage Access and Accessibility in Settings. "
                +"Accessibility can read and act on the visible screen; use it only if you want phone control.";
        new AlertDialog.Builder(this).setTitle("VYRON phone permissions").setMessage(state)
                .setPositiveButton("USAGE ACCESS",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                .setNeutralButton("ACCESSIBILITY",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("APP PERMISSIONS",(d,w)->startActivity(new Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:"+getPackageName())))).show();
    }

    private void showPhoneActions(){
        String[] labels={"Home","Back","Recent apps","Notifications","Quick settings",
                "Read visible screen","Tap visible text","Scroll down","Scroll up","Type into focused field"};
        String[] actions={"home","back","recents","notifications","quick_settings",
                "read_screen","tap_text","scroll_forward","scroll_backward","type_text"};
        new AlertDialog.Builder(this).setTitle("Phone actions")
                .setItems(labels,(dialog,which)->{
                    String action=actions[which];
                    if("tap_text".equals(action)||"type_text".equals(action)){
                        EditText value=new EditText(this);
                        value.setHint("tap_text".equals(action)?"Visible label":"Text to enter");
                        new AlertDialog.Builder(this).setTitle(labels[which]).setView(value)
                                .setPositiveButton("RUN",(d,w)->runPhoneAction(action,value.getText().toString()))
                                .setNegativeButton("CANCEL",null).show();
                    }else runPhoneAction(action,"");
                }).setNegativeButton("CANCEL",null).show();
    }
    private void runPhoneAction(String action,String value){
        new Thread(()->{
            JSONObject args=new JSONObject();
            try{args.put("action",action).put("value",value);}catch(Exception ignored){}
            JSONObject result=new PhoneTools(this).execute("control_phone",args);
            runOnUiThread(()->{
                JSONArray screen=result.optJSONArray("visible_text");
                String content=screen==null?result.optString("detail","Action unavailable"):
                        result.optString("app","")+"\n\n"+screen.toString();
                new AlertDialog.Builder(this).setTitle("Phone action")
                        .setMessage(content).setPositiveButton("OK",null).show();
            });
        },"vyron-phone-action").start();
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
        item.setText(speaker+"  ·  "+value);
        item.setTextColor(Color.rgb(138,204,214));
        item.setTextSize(12);
        item.setPadding(14,2,14,3);
        messages.addView(item,new LinearLayout.LayoutParams(-1,-2));
        chatScroll.setVisibility(View.VISIBLE);
        chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
    }
    private void toggleVoice() {
        hideKeyboard();
        if(liveSession!=null){stopVoice();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},13);
            return;
        }
        if(android.os.Build.VERSION.SDK_INT>=33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                && !getPreferences(0).getBoolean("notification_prompted",false)){
            getPreferences(0).edit().putBoolean("notification_prompted",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},14);
            return;
        }
        String key=activeKey();
        if(key==null || key.trim().isEmpty()){showApiSettings();return;}
        userTranscript.setText("");
        assistantTranscript.setText("");
        final LiveVoiceSession[] current=new LiveVoiceSession[1];
        current[0]=new LiveVoiceSession(this,getPreferences(0).getString("user_name",""),
                new PhoneTools(this),
                new LiveVoiceSession.Listener(){
                    @Override public void onState(String value){runOnUiThread(()->{
                        if(liveSession!=current[0])return;
                        reactor.setState(value);
                        voiceStatus.setText(value+"  ·  TAP TO END");
                    });}
                    @Override public void onTranscript(boolean user,String text){runOnUiThread(()->{
                        if(liveSession!=current[0])return;
                        (user?userTranscript:assistantTranscript).setText((user?"YOU  ·  ":"VYRON  ·  ")+text);
                    });}
                    @Override public void onLevel(float level){runOnUiThread(()->{
                        if(liveSession==current[0])reactor.setMicLevel(level);
                    });}
                    @Override public void onOutputLevel(float level){runOnUiThread(()->{
                        if(liveSession==current[0])reactor.setOutputLevel(level);
                    });}
                    @Override public void onStopped(String message){runOnUiThread(()->{
                        if(liveSession!=current[0])return;
                        stopVoice();
                        assistantTranscript.setText(message);
                    });}
                });
        liveSession=current[0];
        voiceButton.setText("■  END VOICE");
        try{startForegroundService(new Intent(this,VoiceKeeperService.class));}
        catch(Exception ignored){Toast.makeText(this,"Background voice is unavailable on this phone.",Toast.LENGTH_LONG).show();}
        liveSession.start(key);
    }
    private void stopVoice(){
        LiveVoiceSession old=liveSession;
        liveSession=null;
        if(old!=null)old.stop();
        stopService(new Intent(this,VoiceKeeperService.class));
        if(reactor!=null)reactor.setState("READY");
        if(reactor!=null){reactor.setMicLevel(0);reactor.setOutputLevel(0);}
        if(voiceStatus!=null)voiceStatus.setText("READY  ·  TAP TO SPEAK");
        if(voiceButton!=null)voiceButton.setText("●  START VOICE");
    }
    private void sendChat() {
        String prompt=promptInput.getText().toString().trim();
        hideKeyboard();
        if(prompt.isEmpty())return;
        String key=activeKey();
        if(key==null || key.trim().isEmpty()){
            showApiSettings();
            return;
        }
        promptInput.setText("");
        addMessage("YOU",prompt);
        if(liveSession!=null && liveSession.isReady()){
            liveSession.sendText(prompt);
            return;
        }
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
        hideKeyboard();
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
        hideKeyboard();
        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20),0,dp(20),0);
        EditText nameField=new EditText(this);
        nameField.setSingleLine(true);
        nameField.setHint("Your name (optional)");
        nameField.setText(getPreferences(0).getString("user_name",""));
        form.addView(nameField);
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setHint("Paste your Gemini API key");
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setPadding(0,dp(12),0,dp(12));
        form.addView(field);
        String source=SecretStore.load(this)!=null ? "Your encrypted phone override is active."
                : BuildConfig.STARTER_GEMINI_API_KEY.trim().isEmpty() ? "No starter key is included in this build."
                : "The local test starter key is active.";
        new AlertDialog.Builder(this).setTitle("VYRON settings")
                .setMessage(source+" Set your name and optionally override the Gemini key on this phone.")
                .setView(form)
                .setPositiveButton("SAVE",(d,w)->{
                    getPreferences(0).edit().putString("user_name",nameField.getText().toString().trim()).apply();
                    String value=field.getText().toString().trim();
                    if(value.isEmpty()){
                        Toast.makeText(this,"Settings saved. Current API key is unchanged.",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try { SecretStore.save(this,value); updateKeyStatus(); Toast.makeText(this,"Phone API override saved.",Toast.LENGTH_SHORT).show(); }
                    catch(Exception e){Toast.makeText(this,"Could not save the key securely.",Toast.LENGTH_LONG).show();}
                })
                .setNeutralButton("USE STARTER",(d,w)->{
                    getPreferences(0).edit().putString("user_name",nameField.getText().toString().trim()).apply();
                    try { SecretStore.save(this,null); updateKeyStatus(); Toast.makeText(this,"Using the starter key for Gemini Live.",Toast.LENGTH_SHORT).show(); }
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
        if(requestCode==13){
            if(granted)toggleVoice();
            else Toast.makeText(this,"Microphone permission is required for voice.",Toast.LENGTH_LONG).show();
        }
        if(requestCode==14)toggleVoice();
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
    @Override protected void onPause(){super.onPause();}
    @Override protected void onDestroy(){systemHandler.removeCallbacks(refreshSystem);stopVoice();web.destroy();super.onDestroy();}

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
