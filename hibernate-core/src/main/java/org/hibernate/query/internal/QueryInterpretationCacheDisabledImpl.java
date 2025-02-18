/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.internal;

import java.util.function.Function;

import org.hibernate.query.ParameterMetadata;
import org.hibernate.query.spi.NonSelectQueryPlan;
import org.hibernate.query.spi.QueryInterpretationCache;
import org.hibernate.query.spi.SelectQueryPlan;
import org.hibernate.query.sql.spi.ParameterInterpretation;
import org.hibernate.query.sqm.tree.SqmStatement;

/**
 * @author Steve Ebersole
 */
public class QueryInterpretationCacheDisabledImpl implements QueryInterpretationCache {
	/**
	 * Singleton access
	 */
	public static final QueryInterpretationCacheDisabledImpl INSTANCE = new QueryInterpretationCacheDisabledImpl();

	@Override
	public SelectQueryPlan getSelectQueryPlan(Key key) {
		return null;
	}

	@Override
	public void cacheSelectQueryPlan(Key key, SelectQueryPlan plan) {
	}

	@Override
	public NonSelectQueryPlan getNonSelectQueryPlan(Key key) {
		return null;
	}

	@Override
	public void cacheNonSelectQueryPlan(Key key, NonSelectQueryPlan plan) {
	}

	@Override
	public SqmStatement resolveSqmStatement(String queryString, Function<String, SqmStatement<?>> creator) {
		return creator.apply( queryString );
	}

	@Override
	public ParameterInterpretation resolveNativeQueryParameters(
			String queryString,
			Function<String, ParameterInterpretation> creator) {
		return creator.apply( queryString );
	}

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public void close() {
	}
}
