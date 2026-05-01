package com.example.demo.service;

import com.diProject.broker.generated.StationMessage;
import com.example.demo.Utilities.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class StationTaskService {

    private final StationMessageService stationMessageService;
    private final KafkaTemplate<String , byte[]> kafkaTemplate;


    private final AtomicLong messageNumber = new AtomicLong(0);

    @Value("${station.id:1}")
    private long stationId;

    @Scheduled(fixedRate = 1000)
    public void runTask() {
        try {
            long sNo = messageNumber.incrementAndGet();

            if (Utils.tenPercentChance()) {
                //Drop the message with a 10% chance
                log.info("Message dropped for stationId: {}, sNo: {}", stationId, sNo);
                return;
            }

            StationMessage message = stationMessageService.generateStationMessage(stationId, sNo);
            kafkaTemplate.send("station-events", String.valueOf(stationId), message.toByteArray());

            log.info("Generated and sent message for stationId: {}, sNO: {}", stationId, sNo);
        } catch (Exception e) {
            log.error("Error in scheduled task", e);
        }
    }

    }