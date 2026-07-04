package com.example.anroidaiassistant.executor.handlers;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.util.Log;

import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public final class AlarmCommandHandler implements CommandHandler {
    private static final String TAG = "AlarmCommandHandler";

    @Override
    public String getIntent() {
        return "SET_ALARM";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        int alarmHour = ParameterReader.getIntParam(parameters, "alarm_hour");
        int alarmMinute = ParameterReader.getIntParam(parameters, "alarm_minute");
        String period = normalize(ParameterReader.getStringParam(parameters, "period"));
        String day = normalize(ParameterReader.getStringParam(parameters, "day"));

        if (alarmHour < 0) {
            context.showMessage("What time should I set the alarm for?");
            return;
        }

        if (alarmMinute < 0) {
            alarmMinute = 0;
        }

        alarmHour = resolveHour(alarmHour, period);
        if (alarmHour < 0 || alarmHour > 23 || alarmMinute > 59) {
            context.showMessage("Alarm time is not valid");
            return;
        }

        Context androidContext = context.getAndroidContext();
        if (androidContext == null) {
            context.showMessage("Alarm app is unavailable");
            return;
        }

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, alarmHour)
                .putExtra(AlarmClock.EXTRA_MINUTES, alarmMinute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Integer calendarDay = calendarDay(day);
        if (calendarDay != null) {
            ArrayList<Integer> days = new ArrayList<>();
            days.add(calendarDay);
            intent.putExtra(AlarmClock.EXTRA_DAYS, days);
        }

        try {
            androidContext.startActivity(intent);
        } catch (Exception exception) {
            Log.e(TAG, "Failed to set alarm", exception);
            context.showMessage("Alarm app is unavailable");
        }
    }

    private int resolveHour(int hour, String period) {
        if ("am".equals(period)) {
            return hour % 12;
        }
        if ("pm".equals(period)) {
            return hour % 12 == 0 ? 12 : (hour % 12) + 12;
        }
        return hour;
    }

    private Integer calendarDay(String day) {
        if (!TextNormalizer.hasText(day)) {
            return null;
        }

        switch (day) {
            case "monday":
                return Calendar.MONDAY;
            case "tuesday":
                return Calendar.TUESDAY;
            case "wednesday":
                return Calendar.WEDNESDAY;
            case "thursday":
                return Calendar.THURSDAY;
            case "friday":
                return Calendar.FRIDAY;
            case "saturday":
                return Calendar.SATURDAY;
            case "sunday":
                return Calendar.SUNDAY;
            default:
                return null;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
