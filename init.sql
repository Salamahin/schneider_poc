create table if not exists mytopic_table(
    `id`        String,
    `timestamp` Int64,
    `value`     Float64
) engine = MergeTree() order by `timestamp`;

create table if not exists mytopic_queue(
    `id`        String,
    `timestamp` Int64,
    `value`     Float64
) engine = Kafka()
settings
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'mytopic',
    kafka_group_name = 'mytopic-clickhouse-consumer-1',
    kafka_num_consumers = 5,
    kafka_skip_broken_messages = 1,
    input_format_skip_unknown_fields = 1,
    input_format_import_nested_json = 1,
    kafka_max_block_size = 1,
    kafka_format = 'JSONEachRow';

create materialized view if not exists mytopic_mv to mytopic_table as select * from mytopic_queue;