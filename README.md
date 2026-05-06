# WeatherHub

## Description

Each `weather station` emits the current weather status readings to the `central base station` for persistence and analysis.<br>
Kafka is used for reliable and asynchronous message passing between services, ensuring efficient communication and data flow.<br>
Elasticsearch powers analytics and visualization, enabling real-time data exploration and insights.<br>
We also implemented our own version of the Riak Bitcask key-value store, focusing on high performance and persistence.

## System Architecture

The system is composed of three stages:
* Data Acquisition: multiple weather stations that feed a queueing service (Kafka) with
their readings.
* Data Processing & Archiving: The base central station consumes the streamed
data and archives all data in the form of Parquet files.
* Indexing: Two variants of the index are maintained
  * Key-value store (Bitcask) for the latest reading from each station
  * ElasticSearch / Kibana that are running over the Parquet files

![image](https://github.com/user-attachments/assets/7d27490e-03b9-4349-96f7-e2338ae54285)

### Channels:
* Adapter Channel: Transforming the format of the Open-Meteo api response to that of a weather message to be pushed to the Weather-Metrics topic.
* Dead Letter Channel: Expired messages or messages causing application-level errors end up here.
