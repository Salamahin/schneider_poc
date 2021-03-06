create table if not exists table_mytopic(
    `gateway`        String,
    `device`         Int64,
    `gauge`          String,
    `timestamp`:     Int64,
    `value`:         Float64
) engine = MergeTree() order by `timestamp`;

create table if not exists kafka_mytopic(
    `gateway`        String,
    `device`         Int64,
    `gauge`          String,
    `timestamp`:     Int64,
    `value`:         Float64
) engine = Kafka() settings
    kafka_broker_list = 'kafka-headless:9092',
    kafka_topic_list = 'my_topic',
    kafka_group_name = 'mytopic-clickhouse-consumer-1',
    kafka_num_consumers = 5,
    kafka_skip_broken_messages = 1,
    input_format_skip_unknown_fields = 1,
    input_format_import_nested_json = 1,
    kafka_max_block_size = 1,
    kafka_format = 'JSONEachRow';

create materialized view if not exists view_mytopic to table_mytopic as select * from kafka_mytopic;