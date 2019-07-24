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

import java.util.function.BiPredicate;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * A representation of the common configuration options of a {@link Pool}.
 * For a default implementation that is open for extension, see {@link DefaultPoolConfig}.
 *
 * @author Simon Baslé
 */
public interface PoolConfig<POOLABLE> {

	/**
	 * The asynchronous factory that produces new resources, represented as a {@link Mono}.
	 */
	Mono<POOLABLE> allocator();

	/**
	 * {@link AllocationStrategy} defines a strategy / limit for the number of pooled object to allocate.
	 */
	AllocationStrategy allocationStrategy();

	/**
	 * The maximum number of pending borrowers to enqueue before failing fast. 0 will immediately fail any acquire
	 * when no idle resource is available and the pool cannot grow. Use a negative number to deactivate.
	 */
	int maxPending();

	/**
	 * When a resource is {@link PooledRef#release() released}, defines a mechanism of resetting any lingering state of
	 * the resource in order for it to become usable again. The {@link #evictionPredicate} is applied AFTER this reset.
	 * <p>
	 * For example, a buffer could have a readerIndex and writerIndex that need to be flipped back to zero.
	 */
	Function<POOLABLE, ? extends Publisher<Void>> releaseHandler();

	/**
	 * Defines a mechanism of resource destruction, cleaning up state and OS resources it could maintain (eg. off-heap
	 * objects, file handles, socket connections, etc...).
	 * <p>
	 * For example, a database connection could need to cleanly sever the connection link by sending a message to the database.
	 */
	Function<POOLABLE, ? extends Publisher<Void>> destroyHandler();

	/**
	 * A {@link BiPredicate} that checks if a resource should be destroyed ({@code true}) or is still in a valid state
	 * for recycling. This is primarily applied when a resource is released, to check whether or not it can immediately
	 * be recycled, but could also be applied during an acquire attempt (detecting eg. idle resources) or by a background
	 * reaping process. Both the resource and some {@link PooledRefMetadata metrics} about the resource's life within the pool are provided.
	 */
	BiPredicate<POOLABLE, PooledRefMetadata> evictionPredicate();

	/**
	 * When set, {@link Pool} implementation MAY decide to use the {@link Scheduler}
	 * to publish resources in a more deterministic way: the publishing thread would then
	 * always be the same, independently of which thread called {@link Pool#acquire()} or
	 * {@link PooledRef#release()} or on which thread the {@link #allocator} produced new
	 * resources. Note that not all pool implementations are guaranteed to enforce this,
	 * as they might have their own thread publishing semantics.
	 * <p>
	 * Defaults to {@link Schedulers#immediate()}, which inhibits this behavior.
	 */
	Scheduler acquisitionScheduler();

	/**
	 * The {@link PoolMetricsRecorder} to use to collect instrumentation data of the {@link Pool}
	 * implementations.
	 */
	PoolMetricsRecorder metricsRecorder();

}
