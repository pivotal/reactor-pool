/*
 * Copyright (c) 2018-Present Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.pool;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.ShortCountsHistogram;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;

/**
 * @author Simon Baslé
 */
public class TestUtils {

    public static final class TestPooledRef<T> implements PooledRef<T>,
                                                          PooledRefMetadata {

        final T poolable;
        final int msSinceRelease;
        final int msSinceAllocation;
        final int acquireCount;

        public TestPooledRef(T poolable, int acquireCount, int secondsSinceRelease, int secondsSinceAllocation) {
            this.poolable = poolable;
            this.acquireCount = acquireCount;
            this.msSinceRelease = secondsSinceRelease * 1000;
            this.msSinceAllocation = secondsSinceAllocation * 1000;
        }

        @Override
        public T poolable() {
            return this.poolable;
        }

        @Override
        public Mono<Void> release() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> invalidate() {
            return Mono.empty();
        }

        @Override
        public PooledRefMetadata metadata() {
            return this;
        }

        @Override
        public int acquireCount() {
            return acquireCount;
        }

        @Override
        public long lifeTime() {
            return msSinceAllocation;
        }

        @Override
        public long idleTime() {
            return msSinceRelease;
        }

        @Override
        public long allocationTimestamp() {
            return 0;
        }

        @Override
        public long releaseTimestamp() {
            return (long) msSinceAllocation - msSinceRelease;
        }
    }

    public static final class PoolableTest implements Disposable {

        private static AtomicInteger defaultId = new AtomicInteger();

        public int usedUp;
        public int discarded;
        public final int id;
        public final int maxUse;

        public PoolableTest() {
            this(defaultId.incrementAndGet());
        }

        public PoolableTest(int id) {
            this(id, 5);
        }

        public PoolableTest(int id, int maxUse) {
            this.id = id;
            this.usedUp = 0;
            this.maxUse = maxUse;
        }

        public void clean() {
            this.usedUp++;
        }

        public boolean isHealthy() {
            return usedUp < maxUse;
        }

        @Override
        public void dispose() {
            discarded++;
        }

        @Override
        public boolean isDisposed() {
            return discarded > 0;
        }

        @Override
        public String toString() {
            return "PoolableTest{id=" + id + ", used=" + usedUp + "/" + maxUse + "}";
        }
    }


    /**
     * A simple in memory {@link PoolMetricsRecorder} based on HdrHistograms than can also be used to get the metrics.
     *
     * @author Simon Baslé
     */
    public static class InMemoryPoolMetrics implements PoolMetricsRecorder {

        private final ShortCountsHistogram allocationSuccessHistogram;
        private final ShortCountsHistogram allocationErrorHistogram;
        private final ShortCountsHistogram resetHistogram;
        private final ShortCountsHistogram destroyHistogram;
        private final LongAdder recycledCounter;
        private final LongAdder slowPathCounter;
        private final LongAdder fastPathCounter;
        private final Histogram lifetimeHistogram;
        private final Histogram idleTimeHistogram;

        private final Clock clock;

        public InMemoryPoolMetrics(Clock clock) {
            this.clock = clock;
            long maxLatency = TimeUnit.HOURS.toMillis(1);
            int precision = 3; //precision 3 = 1/1000 of each time unit
            allocationSuccessHistogram = new ShortCountsHistogram(1L, maxLatency, precision);
            allocationErrorHistogram = new ShortCountsHistogram(1L, maxLatency, precision);
            resetHistogram = new ShortCountsHistogram(1L, maxLatency, precision);
            destroyHistogram = new ShortCountsHistogram(1L, maxLatency, precision);
            lifetimeHistogram = new Histogram(precision);
            idleTimeHistogram = new Histogram(precision);
            recycledCounter = new LongAdder();
            slowPathCounter = new LongAdder();
            fastPathCounter = new LongAdder();
        }

        public Clock getClock() {
            return this.clock;
        }

        /**
         * Helper to measure the time with the precision needed for tests.
         * @param startTimeMillis the starting time, as measured by {@link #getClock() the Clock's} {@link Clock#millis() millis()}
         * @return the elapsed time
         */
        public long measureTime(long startTimeMillis) {
            final long l = clock.millis() - startTimeMillis;
            if (l <= 0) return 1;
            return l;
        }

        @Override
        public void recordAllocationSuccessAndLatency(long latencyMs) {
            allocationSuccessHistogram.recordValue(latencyMs);
        }

        @Override
        public void recordAllocationFailureAndLatency(long latencyMs) {
            allocationErrorHistogram.recordValue(latencyMs);
        }

        @Override
        public void recordResetLatency(long latencyMs) {
            resetHistogram.recordValue(latencyMs);
        }

        @Override
        public void recordDestroyLatency(long latencyMs) {
            destroyHistogram.recordValue(latencyMs);
        }

        @Override
        public void recordRecycled() {
            recycledCounter.increment();
        }

        @Override
        public void recordLifetimeDuration(long millisecondsSinceAllocation) {
            this.lifetimeHistogram.recordValue(millisecondsSinceAllocation);
        }

        @Override
        public void recordIdleTime(long millisecondsIdle) {
            this.idleTimeHistogram.recordValue(millisecondsIdle);
        }

        @Override
        public void recordSlowPath() {
            this.slowPathCounter.increment();
        }

        @Override
        public void recordFastPath() {
            this.fastPathCounter.increment();
        }

        public long getAllocationTotalCount() {
            return allocationSuccessHistogram.getTotalCount() + allocationErrorHistogram.getTotalCount();
        }

        public long getAllocationSuccessCount() {
            return allocationSuccessHistogram.getTotalCount();
        }

        public long getAllocationErrorCount() {
            return allocationErrorHistogram.getTotalCount();
        }

        public long getResetCount() {
            return resetHistogram.getTotalCount();
        }

        public long getDestroyCount() {
            return destroyHistogram.getTotalCount();
        }

        public long getRecycledCount() {
            return recycledCounter.sum();
        }

        public ShortCountsHistogram getAllocationSuccessHistogram() {
            return allocationSuccessHistogram;
        }

        public ShortCountsHistogram getAllocationErrorHistogram() {
            return allocationErrorHistogram;
        }

        public ShortCountsHistogram getResetHistogram() {
            return resetHistogram;
        }

        public ShortCountsHistogram getDestroyHistogram() {
            return destroyHistogram;
        }

        public Histogram getLifetimeHistogram() {
            return lifetimeHistogram;
        }

        public Histogram getIdleTimeHistogram() {
            return idleTimeHistogram;
        }

        public long getFastPathCount() {
            return fastPathCounter.sum();
        }

        public long getSlowPathCount() {
            return slowPathCounter.sum();
        }
    }

    /**
     * A simple test {@link Clock} that is based on {@link System#nanoTime()}.
     */
    public static class NanoTimeClock extends Clock {
        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public long millis() {
            return System.nanoTime() / 1000000;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }
    }

    /**
     * A simple virtual {@link Clock} that can be moved backward and forward. Starts at time 0.
     */
    public static class VirtualClock extends Clock {

        private Instant now;

        public VirtualClock() {
            this(Instant.EPOCH);
        }

        public VirtualClock(Instant startTime) {
            now = startTime;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        /**
         * Advance this {@link Clock} by the given (positive or negative) {@link Duration}.
         *
         * @param duration the {@link Duration} to advance by
         */
        public void advanceTimeBy(Duration duration) {
            now = now.plus(duration);
        }

        /**
         * Set this {@link Clock} to the given timestamp in milliseconds from the origin (0).
         *
         * @param timestampInMillis the new timestamp
         */
        public void setTimeTo(long timestampInMillis) {
            now = Instant.ofEpochMilli(timestampInMillis);
        }
    }
}
