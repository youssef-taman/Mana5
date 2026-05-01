package com.example.demo.service;

import com.diProject.broker.generated.StationMessage;
import com.diProject.broker.generated.WeatherStatus;
import com.example.demo.Utilities.Utils;
import org.springframework.stereotype.Service;

@Service
public class StationMessageService {

    private static final double BATTERY_LOW_THRESHOLD = 0.3;
    private static final double BATTERY_MEDIUM_THRESHOLD = 0.7;
    private static final int HUMIDITY_MIN_VALUE = 20;
    private static final int HUMIDITY_MAX_VALUE = 95;
    private static final int TEMPERATURE_MIN_VALUE = 60;
    private static final int TEMPERATURE_MAX_VALUE = 110;
    private static final int WIND_SPEED_MIN_VALUE = 0;
    private static final int WIND_SPEED_MAX_VALUE = 60;



    public  StationMessage generateStationMessage(long stationId, long sNo) {
        String batteryStatus = generateBatteryStatus();
        long timestamp = System.currentTimeMillis();
        WeatherStatus weatherStatus = generateWeatherStatus();
        return StationMessage.newBuilder()
                .setStationId(stationId)
                .setSNo(sNo)
                .setBatteryStatus(batteryStatus)
                .setTimestamp(timestamp)
                .setWeatherStatus(weatherStatus)
                .build();
    }

    private  String generateBatteryStatus() {
        double chance = Math.random();
        if (chance < BATTERY_LOW_THRESHOLD) {
            return "LOW";
        } else if (chance < BATTERY_MEDIUM_THRESHOLD) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }
    private  WeatherStatus generateWeatherStatus() {
        int humidity = Utils.randomInt(HUMIDITY_MIN_VALUE, HUMIDITY_MAX_VALUE);
        int temperature =Utils.randomInt(TEMPERATURE_MIN_VALUE, TEMPERATURE_MAX_VALUE);
        int windSpeed = Utils.randomInt(WIND_SPEED_MIN_VALUE, WIND_SPEED_MAX_VALUE);
        return WeatherStatus.newBuilder()
                .setHumidity(humidity)
                .setTemperature(temperature)
                .setWindSpeed(windSpeed)
                .build();
    }

}
