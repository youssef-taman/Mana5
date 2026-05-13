package org.example.service;


import com.diProject.broker.generated.StationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bitcask.BitCask;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CentralStationService {

    private final BitCask bitCask;

    @KafkaListener(topics = "station-events", groupId = "central-station-group")
    public void processStationEvent(StationMessage stationMessage) {
        log.info("Received station event: {}", stationMessage);
        try {
            String stationId = stationMessage.getStationId();
            bitCask.put(stationId, stationMessage.toString());
            log.info("stored {}", bitCask.get(stationId));
        } catch (Exception e) {
            log.error("Error processing station event: {}", e.getMessage(), e);
        }
    }
}

