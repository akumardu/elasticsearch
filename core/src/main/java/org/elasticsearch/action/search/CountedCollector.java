/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.action.search;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.util.concurrent.CountDown;
import org.elasticsearch.search.SearchPhaseResult;
import org.elasticsearch.search.SearchShardTarget;

/**
 * This is a simple base class to simplify fan out to shards and collect their results. Each results passed to
 * {@link #onResult(int, SearchPhaseResult, SearchShardTarget)} will be set to the provided result array
 * where the given index is used to set the result on the array.
 */
final class CountedCollector<R extends SearchPhaseResult> {
    private final ResultConsumer<R> resultConsumer;
    private final CountDown counter;
    private final Runnable onFinish;
    private final SearchPhaseContext context;

    CountedCollector(ResultConsumer<R> resultConsumer, int expectedOps, Runnable onFinish, SearchPhaseContext context) {
        this.resultConsumer = resultConsumer;
        this.counter = new CountDown(expectedOps);
        this.onFinish = onFinish;
        this.context = context;
    }

    /**
     * Forcefully counts down an operation and executes the provided runnable
     * if all expected operations where executed
     */
    void countDown() {
        assert counter.isCountedDown() == false : "more operations executed than specified";
        if (counter.countDown()) {
            onFinish.run();
        }
    }

    /**
     * Sets the result to the given array index and then runs {@link #countDown()}
     */
    void onResult(int index, R result, SearchShardTarget target) {
        try {
            result.shardTarget(target);
            resultConsumer.consume(index, result);
        } finally {
            countDown();
        }
    }

    /**
     * Escalates the failure via {@link SearchPhaseContext#onShardFailure(int, SearchShardTarget, Exception)}
     * and then runs {@link #countDown()}
     */
    void onFailure(final int shardIndex, @Nullable SearchShardTarget shardTarget, Exception e) {
        try {
            context.onShardFailure(shardIndex, shardTarget, e);
        } finally {
            countDown();
        }
    }

    /**
     * A functional interface to plug in shard result consumers to this collector
     */
    @FunctionalInterface
    public interface ResultConsumer<R extends SearchPhaseResult> {
        void consume(int shardIndex, R result);
    }
}
