/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.langstream.kafka.runtime;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

import ai.langstream.api.model.SchemaDefinition;
import ai.langstream.api.runtime.ConnectionImplementation;
import ai.langstream.api.runtime.Topic;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record KafkaTopic(
        String name,
        int partitions,
        int replicationFactor,
        SchemaDefinition keySchema,
        SchemaDefinition valueSchema,
        String createMode,
        boolean implicit,
        Map<String, Object> config,
        Map<String, Object> options)
        implements ConnectionImplementation, Topic {
    public Map<String, Object> createConsumerConfiguration() {
        Map<String, Object> configuration = new HashMap<>();

        // this is for the Agent
        configuration.put("topic", name);

        configuration.put(KEY_DESERIALIZER_CLASS_CONFIG, getDeserializerForSchema(keySchema));
        configuration.put(VALUE_DESERIALIZER_CLASS_CONFIG, getDeserializerForSchema(valueSchema));

        if (options != null) {
            options.forEach(
                    (key, value) -> {
                        if (key.startsWith("consumer.")) {
                            configuration.put(key.substring("consumer.".length()), value);
                        }
                    });

            Object deadLetterTopicProducer = options.get("deadLetterTopicProducer");
            if (deadLetterTopicProducer != null) {
                configuration.put("deadLetterTopicProducer", deadLetterTopicProducer);
            }
        }

        return configuration;
    }

    private String getDeserializerForSchema(SchemaDefinition schema) {
        if (schema == null) {
            // the default is String, because people usually use schemaless JSON
            return org.apache.kafka.common.serialization.StringDeserializer.class.getName();
        }

        return switch (schema.type()) {
            case "string" -> org.apache.kafka.common.serialization.StringDeserializer.class
                    .getName();
            case "bytes" -> org.apache.kafka.common.serialization.ByteArrayDeserializer.class
                    .getName();
            case "avro" -> io.confluent.kafka.serializers.KafkaAvroDeserializer.class.getName();
            default -> throw new IllegalArgumentException(
                    "Unsupported schema type: " + schema.type());
        };
    }

    private String getSerializerForSchema(SchemaDefinition schema) {
        if (schema == null) {
            // we use reflection to dynamically configure the Serializer for the object, see
            // KafkaProducerWrapper
            return org.apache.kafka.common.serialization.ByteArraySerializer.class.getName();
        }

        return switch (schema.type()) {
            case "string" -> org.apache.kafka.common.serialization.StringSerializer.class.getName();
            case "bytes" -> org.apache.kafka.common.serialization.ByteArraySerializer.class
                    .getName();
            case "avro" -> io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName();
            default -> throw new IllegalArgumentException(
                    "Unsupported schema type: " + schema.type());
        };
    }

    public Map<String, Object> createProducerConfiguration() {
        Map<String, Object> configuration = new HashMap<>();

        // this is for the Agent
        configuration.put("topic", name);

        configuration.put(KEY_SERIALIZER_CLASS_CONFIG, getSerializerForSchema(keySchema));
        configuration.put(VALUE_SERIALIZER_CLASS_CONFIG, getSerializerForSchema(valueSchema));

        if (options != null) {
            options.forEach(
                    (key, value) -> {
                        if (key.startsWith("producer.")) {
                            configuration.put(key.substring("producer.".length()), value);
                        }
                    });
        }

        return configuration;
    }

    @Override
    public String topicName() {
        return this.name;
    }

    @Override
    public boolean implicit() {
        return this.implicit;
    }

    @Override
    public void bindDeadletterTopic(Topic deadletterTopic) {
        if (!(deadletterTopic instanceof KafkaTopic kafkaTopic)) {
            throw new IllegalArgumentException();
        }
        log.info("Binding deadletter topic {} to topic {}", deadletterTopic, this.topicName());
        options.put("deadLetterTopicProducer", kafkaTopic.createProducerConfiguration());
    }
}
