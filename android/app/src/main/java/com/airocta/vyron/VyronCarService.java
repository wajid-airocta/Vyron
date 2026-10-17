package com.airocta.vyron;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.validation.HostValidator;
import androidx.core.graphics.drawable.IconCompat;

public final class VyronCarService extends CarAppService {
    @Override public HostValidator createHostValidator() {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
        return new HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build();
    }
    @Override public Session onCreateSession() {
        return new Session() {
            @Override public Screen onCreateScreen(Intent intent) {
                return new WeatherScreen(getCarContext());
            }
        };
    }
    private static final class WeatherScreen extends Screen {
        private final Handler main = new Handler(Looper.getMainLooper());
        private CarWeatherVoice voice;
        private String voiceState = "Tap microphone for weather voice";
        private String question = "";
        private String answer = "";
        WeatherScreen(CarContext context) {
            super(context);
            WeatherRepository.refresh(context, () -> invalidate());
        }
        @Override public Template onGetTemplate() {
            WeatherRepository.Snapshot weather=WeatherRepository.current();
            Pane.Builder pane=new Pane.Builder();
            if (weather == null) {
                pane.addRow(new Row.Builder().setTitle("Current weather")
                        .addText(WeatherRepository.status()).build());
            } else {
                pane.addRow(new Row.Builder().setTitle(weather.temperature + "°C · " + weather.condition)
                        .addText("Near your phone · updated " + weather.updated).build());
                pane.addRow(new Row.Builder().setTitle("Wind")
                        .addText(weather.wind + " km/h").build());
                pane.addRow(new Row.Builder().setTitle("Humidity")
                        .addText(weather.humidity + "%").build());
            }
            Row.Builder voiceRow=new Row.Builder().setTitle(answer.isEmpty() ? "Weather voice" : "VYRON");
            voiceRow.addText(answer.isEmpty() ? voiceState : answer);
            if(!question.isEmpty())voiceRow.addText("You: " + question);
            pane.addRow(voiceRow.build());
            Action microphone=new Action.Builder()
                    .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(),
                            R.drawable.ic_vyron_mic)).build())
                    .setOnClickListener(this::toggleVoice).build();
            return new PaneTemplate.Builder(pane.build())
                    .setTitle("VYRON WEATHER")
                    .setHeaderAction(Action.APP_ICON)
                    .setActionStrip(new ActionStrip.Builder().addAction(microphone).build())
                    .build();
        }
        private void toggleVoice() {
            if(voice!=null){voice.stop();voice=null;voiceState="Tap microphone for weather voice";invalidate();return;}
            question="";answer="";
            voice=new CarWeatherVoice(getCarContext(),(state,user,reply)->main.post(()->{
                voiceState=state;question=user;answer=reply;
                if(state.equals("Weather ready") || state.contains("unavailable")
                        || state.contains("reached") || state.contains("ended")
                        || state.contains("heard") || state.contains("failed")
                        || state.contains("busy") || state.contains("interrupted")
                        || state.contains("timed out")
                        || state.startsWith("Allow") || state.startsWith("Add"))voice=null;
                invalidate();
            }));
            voice.start();
        }
    }
}
