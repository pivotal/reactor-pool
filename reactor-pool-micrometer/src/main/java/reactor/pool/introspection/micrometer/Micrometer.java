/*
 * Copyright (c) 2022 VMware Inc. or its affiliates, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.pool.introspection.micrometer;

import io.micrometer.core.instrument.MeterRegistry;

import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;
import reactor.pool.PoolMetricsRecorder;

/**
 * Micrometer supporting utilities for instrumentation of reactor-pool.
 *
 * @author Simon Baslé
 */
public final class Micrometer {

	/**
	 * Create an {@link InstrumentedPool} starting from the provided {@link PoolBuilder}. The pool publishes metrics to
	 * a Micrometer {@link MeterRegistry}. One can differentiate between pools thanks to the provided {@code poolName},
	 * which will be set on all meters as the value for the {@link PoolMetersDocumentation.CommonTags#POOL_NAME} tag.
	 * <p>
	 * The steps involved are as follows:
	 * <ol>
	 *     <li> create a {@link PoolMetricsRecorder} similar to {@link #recorder(String, MeterRegistry)} </li>
	 *     <li> mutate the builder to use that recorder by calling {@link PoolBuilder#metricsRecorder(PoolMetricsRecorder)} </li>
	 *     <li> create an {@link InstrumentedPool} via {@link PoolBuilder#buildPool()} </li>
	 *     <li> instrument the {@link reactor.pool.InstrumentedPool.PoolMetrics} via {@link #gaugesOf(InstrumentedPool.PoolMetrics, String, MeterRegistry)} </li>
	 *     <li> return that {@link InstrumentedPool} instance </li>
	 * </ol>
	 *
	 * @param poolBuilder a pre-configured {@link PoolBuilder} on which to configure a {@link PoolMetricsRecorder}
	 * @param poolName the tag value to use on the gauges and the recorder's meters to differentiate between pools
	 * @param meterRegistry the registry to use for the gauges and the recorder's meters
	 * @param <POOLABLE> the type of resources in the pool
	 * @return a new {@link InstrumentedPool} with a Micrometer recorder and with gauges attached
	 * @see PoolMetersDocumentation
	 */
	public static <POOLABLE> InstrumentedPool<POOLABLE> instrumentedPool(PoolBuilder<POOLABLE, ?> poolBuilder, String poolName, MeterRegistry meterRegistry) {
		PoolMetricsRecorder recorder = recorder(poolName, meterRegistry);
		InstrumentedPool<POOLABLE> pool = poolBuilder.metricsRecorder(recorder).buildPool();
		gaugesOf(pool.metrics(), poolName, meterRegistry);
		return pool;
	}

	/**
	 * Create a {@link PoolGaugesBinder} and bind to the provided {@link MeterRegistry}. This registers gauges around the
	 * {@link InstrumentedPool}'s {@link reactor.pool.InstrumentedPool.PoolMetrics}.
	 * One can differentiate between pools thanks to the provided {@code poolName}, which will be set on all meters as
	 * the value for the {@link PoolMetersDocumentation.CommonTags#POOL_NAME} tag.
	 * <p>
	 * {@link PoolMetersDocumentation} include the gauges which are:
	 * <ul>
	 *     <li> {@link PoolMetersDocumentation#ACQUIRED} </li>
	 *     <li> {@link PoolMetersDocumentation#ALLOCATED}, </li>
	 *     <li> {@link PoolMetersDocumentation#IDLE} </li>
	 *     <li> {@link PoolMetersDocumentation#PENDING_ACQUIRE} </li>
	 * </ul>
	 *
	 * @param poolMetrics the {@link reactor.pool.InstrumentedPool.PoolMetrics} to turn into gauges
	 * @param poolName the tag value to use on the gauges to differentiate between pools
	 * @param meterRegistry the registry to use for the gauges
	 * @see PoolGaugesBinder
	 * @see PoolMetersDocumentation
	 * @see #instrumentedPool(PoolBuilder, String, MeterRegistry)
	 */
	public static void gaugesOf(InstrumentedPool.PoolMetrics poolMetrics, String poolName, MeterRegistry meterRegistry) {
		new PoolGaugesBinder(poolMetrics, poolName).bindTo(meterRegistry);
	}

	/**
	 * Create a {@link PoolMetricsRecorder} publishing timers and other meters to a provided {@link MeterRegistry}.
	 * One can differentiate between pools thanks to the provided {@code poolName}, which will be set on all meters
	 * as the value for the {@link PoolMetersDocumentation.CommonTags#POOL_NAME} tag.
	 * <p>
	 * {@link PoolMetersDocumentation} include the recorder-specific meters which are:
	 * <ul>
	 *     <li> {@link PoolMetersDocumentation#ALLOCATION} </li>
	 *     <li> {@link PoolMetersDocumentation#DESTROYED}, </li>
	 *     <li> {@link PoolMetersDocumentation#RECYCLED} </li>
	 *     <li> {@link PoolMetersDocumentation#RESET} </li>
	 *     <li> {@link PoolMetersDocumentation#SUMMARY_IDLENESS} </li>
	 *     <li> {@link PoolMetersDocumentation#SUMMARY_LIFETIME} </li>
	 * </ul>
	 *
	 * @param poolName the tag value to use on the gauges and the recorder's meters to differentiate between pools
	 * @param meterRegistry the registry to use for the recorder's meters
	 * @return a Micrometer {@link PoolMetricsRecorder}
	 * @see PoolMetersDocumentation
	 * @see #instrumentedPool(PoolBuilder, String, MeterRegistry)
	 */
	public static PoolMetricsRecorder recorder(String poolName, MeterRegistry meterRegistry) {
		return new MicrometerMetricsRecorder(poolName, meterRegistry);
	}
}
