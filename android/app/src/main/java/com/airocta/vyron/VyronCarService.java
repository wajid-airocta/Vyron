package com.airocta.vyron;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.model.Action;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.validation.HostValidator;

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
                        .addText("Current conditions near your phone").build());
                pane.addRow(new Row.Builder().setTitle("Wind")
                        .addText(weather.wind + " km/h").build());
                pane.addRow(new Row.Builder().setTitle("Humidity")
                        .addText(weather.humidity + "%").build());
                pane.addRow(new Row.Builder().setTitle("Updated")
                        .addText(weather.updated).build());
            }
            return new PaneTemplate.Builder(pane.build())
                    .setTitle("VYRON WEATHER")
                    .setHeaderAction(Action.APP_ICON)
                    .build();
        }
    }
}
