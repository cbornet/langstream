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
package com.datastax.oss.streaming.ai.embeddings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockEmbeddingsService implements EmbeddingsService {

    private final Map<String, List<Double>> embeddingsMapping = new HashMap<>();

    public void setEmbeddingsForText(String text, List<Double> embeddings) {
        embeddingsMapping.put(text, embeddings);
    }

    @Override
    public List<List<Double>> computeEmbeddings(List<String> texts) {
        return texts.stream()
                .map(text -> embeddingsMapping.get(text))
                .collect(java.util.stream.Collectors.toList());
    }
}
