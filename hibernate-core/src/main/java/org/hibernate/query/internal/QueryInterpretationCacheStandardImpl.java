/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.internal;

import java.util.function.Function;

import org.hibernate.internal.util.collections.BoundedConcurrentHashMap;
import org.hibernate.query.QueryLogger;
import org.hibernate.query.spi.NonSelectQueryPlan;
import org.hibernate.query.spi.QueryInterpretationCache;
import org.hibernate.query.spi.QueryPlan;
import org.hibernate.query.spi.SelectQueryPlan;
import org.hibernate.query.sql.spi.ParameterInterpretation;
import org.hibernate.query.sqm.tree.SqmStatement;

import org.jboss.logging.Logger;

/**
 * Standard QueryPlanCache implementation
 *
 * @author Steve Ebersole
 */
public class QueryInterpretationCacheStandardImpl implements QueryInterpretationCache {
	private static final Logger log = QueryLogger.subLogger( "plan.cache" );

	/**
	 * The default strong reference count.
	 */
	public static final int DEFAULT_PARAMETER_METADATA_MAX_COUNT = 128;
	/**
	 * The default soft reference count.
	 */
	public static final int DEFAULT_QUERY_PLAN_MAX_COUNT = 2048;

	/**
	 * the cache of the actual plans...
	 */
	private final BoundedConcurrentHashMap<Key, QueryPlan> queryPlanCache;

	private final BoundedConcurrentHashMap<String, SqmStatement<?>> sqmStatementCache;
	private final BoundedConcurrentHashMap<String, ParameterInterpretation> nativeQueryParamCache;

	public QueryInterpretationCacheStandardImpl(int maxQueryPlanCount) {
		log.debugf( "Starting QueryPlanCache(%s)", maxQueryPlanCount );

		queryPlanCache = new BoundedConcurrentHashMap<>( maxQueryPlanCount, 20, BoundedConcurrentHashMap.Eviction.LIRS );
		sqmStatementCache = new BoundedConcurrentHashMap<>( maxQueryPlanCount, 20, BoundedConcurrentHashMap.Eviction.LIRS );
		nativeQueryParamCache = new BoundedConcurrentHashMap<>( maxQueryPlanCount, 20, BoundedConcurrentHashMap.Eviction.LIRS );
	}

	@Override
	public SelectQueryPlan getSelectQueryPlan(Key key) {
		log.tracef( "QueryPlan#getSelectQueryPlan(%s)", key );
		return (SelectQueryPlan) queryPlanCache.get( key );
	}

	@Override
	public void cacheSelectQueryPlan(Key key, SelectQueryPlan plan) {
		log.tracef( "QueryPlan#cacheSelectQueryPlan(%s)", key );
		queryPlanCache.putIfAbsent( key, plan );
	}

	@Override
	public NonSelectQueryPlan getNonSelectQueryPlan(Key key) {
		log.tracef( "QueryPlan#getNonSelectQueryPlan(%s)", key );
		return null;
	}

	@Override
	public void cacheNonSelectQueryPlan(Key key, NonSelectQueryPlan plan) {
		log.tracef( "QueryPlan#cacheNonSelectQueryPlan(%s)", key );
	}

	@Override
	public SqmStatement resolveSqmStatement(
			String queryString,
			Function<String, SqmStatement<?>> creator) {
		log.tracef( "QueryPlan#resolveSqmStatement(%s)", queryString );
		return sqmStatementCache.computeIfAbsent(
				queryString,
				s -> {
					log.debugf( "Creating and caching SqmStatement - %s", queryString );
					return creator.apply( queryString );
				}
		);
	}

	@Override
	public ParameterInterpretation resolveNativeQueryParameters(
			String queryString,
			Function<String, ParameterInterpretation> creator) {
		log.tracef( "QueryPlan#resolveNativeQueryParameters(%s)", queryString );
		return nativeQueryParamCache.computeIfAbsent(
				queryString,
				s -> {
					log.debugf( "Creating and caching SqmStatement - %s", queryString );
					return creator.apply( queryString );
				}
		);
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public void close() {
		// todo (6.0) : clear maps/caches and LOG
		queryPlanCache.clear();
	}
}
