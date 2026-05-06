package org.example.rainstreamalert.service;

import com.diProject.broker.generated.StationMessage;
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
public class RainAlertStream {
    private static final int  RAIN_HUMIDITY_THRESHOLD = 70;
    private static final String INPUT_TOPIC  = "station-events";
    private static final String OUTPUT_TOPIC = "rain-alerts";

    private final KafkaStreams kafkaStreams;
    private final String schemaRegistryUrl;

    public RainAlertStream(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.streams.application-id}") String appId,
            @Value("${spring.kafka.properties.schema.registry.url}") String schemaRegistryUrl) {

        this.schemaRegistryUrl = schemaRegistryUrl;
        this.kafkaStreams = new KafkaStreams(buildTopology(), buildProperties(appId, bootstrapServers));

    }

    private Topology buildTopology() {


        StreamsBuilder builder = new StreamsBuilder();
        KafkaProtobufSerde<StationMessage> stationSerde = configureKafkaProtobuffSerde();
        KStream<String, StationMessage> source = builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), stationSerde));

        source.filter((key, value) -> isRaining(value))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), stationSerde));

        return builder.build();

    }

    private Properties buildProperties(String appId, String bootstrapServers) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return props;
    }

    private KafkaProtobufSerde<StationMessage> configureKafkaProtobuffSerde(){
        Map<String, String> serdeConfig = Map.of(
                "schema.registry.url", schemaRegistryUrl,
                "specific.protobuf.value.type", StationMessage.class.getName()
        );
        final KafkaProtobufSerde<StationMessage> stationSerde = new KafkaProtobufSerde<>();
        stationSerde.configure(serdeConfig, false);
        return stationSerde;
    }

    private  boolean isRaining(StationMessage message) {
        if (message == null) return false;

        boolean raining = message.getWeatherStatus().getHumidity() > RAIN_HUMIDITY_THRESHOLD;

        if (raining) {
            log.info("Rain detected — mNo={} humidity={}",
                    message.getSNo(),
                    message.getWeatherStatus().getHumidity());
        }

        return raining;
    }

    @PostConstruct
    public void start() {
        kafkaStreams.start();
        log.info("Rain alert stream started — filter: humidity > {}%",
                RAIN_HUMIDITY_THRESHOLD);
    }
    @PreDestroy
    public void stop() {
        kafkaStreams.close();
        log.info("Rain alert stream stopped");
    }
    }
