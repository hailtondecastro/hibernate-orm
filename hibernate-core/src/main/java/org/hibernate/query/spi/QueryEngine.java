/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.spi;

import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.Incubating;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.metamodel.model.domain.JpaMetamodel;
import org.hibernate.query.QueryLogger;
import org.hibernate.query.hql.HqlTranslator;
import org.hibernate.query.hql.internal.StandardHqlTranslator;
import org.hibernate.query.hql.spi.SqmCreationOptions;
import org.hibernate.query.internal.QueryInterpretationCacheDisabledImpl;
import org.hibernate.query.internal.QueryInterpretationCacheStandardImpl;
import org.hibernate.query.named.NamedQueryRepository;
import org.hibernate.query.sqm.sql.SqmTranslatorFactory;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.internal.SqmCreationOptionsStandard;
import org.hibernate.query.sqm.internal.SqmCriteriaNodeBuilder;
import org.hibernate.query.sqm.produce.function.SqmFunctionRegistry;
import org.hibernate.query.sqm.spi.SqmCreationContext;
import org.hibernate.query.sqm.sql.SqmSelectToSqlAstConverter;
import org.hibernate.query.sqm.sql.internal.StandardSqmSelectToSqlAstConverter;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ast.spi.SqlAstCreationContext;

/**
 * Aggregation and encapsulation of the components Hibernate uses
 * to execute queries (HQL, Criteria and native)
 *
 * @author Steve Ebersole
 */
@Incubating
public class QueryEngine {
	public static QueryEngine from(
			SessionFactoryImplementor sessionFactory,
			MetadataImplementor metadata) {
		return new QueryEngine(
				sessionFactory.getJpaMetamodel(),
				sessionFactory.getServiceRegistry(),
				sessionFactory.getSessionFactoryOptions(),
				sessionFactory,
				new SqmCreationOptionsStandard( sessionFactory ),
				sessionFactory.getProperties(),
				metadata.buildNamedQueryRepository( sessionFactory )
		);
	}

	private final NamedQueryRepository namedQueryRepository;
	private final SqmCriteriaNodeBuilder criteriaBuilder;
	private final HqlTranslator hqlTranslator;
	private final SqmTranslatorFactory sqmTranslatorFactory;
	private final QueryInterpretationCache interpretationCache;
	private final SqmFunctionRegistry sqmFunctionRegistry;

	public QueryEngine(
			JpaMetamodel domainModel,
			ServiceRegistry serviceRegistry,
			SessionFactoryOptions runtimeOptions,
			SqmCreationContext sqmCreationContext,
			SqmCreationOptions sqmCreationOptions,
			Map properties,
			NamedQueryRepository namedQueryRepository) {
		final JdbcServices jdbcServices = serviceRegistry.getService( JdbcServices.class );
		final JdbcEnvironment jdbcEnvironment = jdbcServices.getJdbcEnvironment();
		final Dialect dialect = jdbcEnvironment.getDialect();

		this.namedQueryRepository = namedQueryRepository;

		this.hqlTranslator = resolveHqlTranslator(
				runtimeOptions,
				dialect,
				sqmCreationContext,
				sqmCreationOptions
		);

		this.sqmTranslatorFactory = resolveSqmTranslatorFactory(
				runtimeOptions,
				dialect,
				sqmCreationContext,
				sqmCreationOptions
		);

		this.criteriaBuilder = new SqmCriteriaNodeBuilder(
				this,
				domainModel,
				serviceRegistry
		);

		this.interpretationCache = buildInterpretationCache( properties );

		this.sqmFunctionRegistry = new SqmFunctionRegistry();
		dialect.initializeFunctionRegistry( this );
		if ( runtimeOptions.getSqmFunctionRegistry() != null ) {
			runtimeOptions.getSqmFunctionRegistry().overlay( sqmFunctionRegistry );
		}
	}

	private static HqlTranslator resolveHqlTranslator(
			SessionFactoryOptions runtimeOptions,
			Dialect dialect,
			SqmCreationContext sqmCreationContext,
			SqmCreationOptions sqmCreationOptions) {
		if ( runtimeOptions.getHqlTranslator() != null ) {
			return runtimeOptions.getHqlTranslator();
		}

		if ( dialect.getHqlTranslator() != null ) {
			return dialect.getHqlTranslator();
		}

		return new StandardHqlTranslator( sqmCreationContext, sqmCreationOptions );
	}

	private SqmTranslatorFactory resolveSqmTranslatorFactory(
			SessionFactoryOptions runtimeOptions,
			Dialect dialect,
			SqmCreationContext sqmCreationContext,
			SqmCreationOptions sqmCreationOptions) {
		if ( runtimeOptions.getSqmTranslatorFactory() != null ) {
			return runtimeOptions.getSqmTranslatorFactory();
		}

		if ( dialect.getSqmTranslatorFactory() != null ) {
			return dialect.getSqmTranslatorFactory();
		}

		//noinspection Convert2Lambda
		return new SqmTranslatorFactory() {
			@Override
			public SqmSelectToSqlAstConverter createSelectConverter(
					QueryOptions queryOptions,
					DomainParameterXref domainParameterXref,
					QueryParameterBindings domainParameterBindings,
					LoadQueryInfluencers influencers,
					SqlAstCreationContext creationContext) {
				return new StandardSqmSelectToSqlAstConverter(
						queryOptions,
						domainParameterXref,
						domainParameterBindings,
						influencers,
						creationContext
				);
			}
		};
	}

	private static QueryInterpretationCache buildInterpretationCache(Map properties) {
		final boolean explicitUseCache = ConfigurationHelper.getBoolean(
				AvailableSettings.QUERY_PLAN_CACHE_ENABLED,
				properties,
				false
		);

		final Integer explicitMaxPlanCount = ConfigurationHelper.getInteger(
				AvailableSettings.QUERY_PLAN_CACHE_MAX_SIZE,
				properties
		);

		if ( explicitUseCache || ( explicitMaxPlanCount != null && explicitMaxPlanCount > 0 ) ) {
			return new QueryInterpretationCacheStandardImpl(
					explicitMaxPlanCount != null
							? explicitMaxPlanCount
							: QueryInterpretationCacheStandardImpl.DEFAULT_QUERY_PLAN_MAX_COUNT
			);
		}
		else {
			// disabled
			return QueryInterpretationCacheDisabledImpl.INSTANCE;
		}
	}

	public void prepare(SessionFactoryImplementor sessionFactory) {
		//checking for named queries
		if ( sessionFactory.getSessionFactoryOptions().isNamedQueryStartupCheckingEnabled() ) {
			final Map<String, HibernateException> errors = namedQueryRepository.checkNamedQueries( this );

			if ( !errors.isEmpty() ) {
				StringBuilder failingQueries = new StringBuilder( "Errors in named queries: " );
				String sep = "";
				for ( Map.Entry<String, HibernateException> entry : errors.entrySet() ) {
					QueryLogger.QUERY_LOGGER.namedQueryError( entry.getKey(), entry.getValue() );
					failingQueries.append( sep ).append( entry.getKey() );
					sep = ", ";
				}
				throw new HibernateException( failingQueries.toString() );
			}
		}
	}

	public NamedQueryRepository getNamedQueryRepository() {
		return namedQueryRepository;
	}

	public SqmCriteriaNodeBuilder getCriteriaBuilder() {
		return criteriaBuilder;
	}

	public HqlTranslator getHqlTranslator() {
		return hqlTranslator;
	}

	public SqmTranslatorFactory getSqmTranslatorFactory() {
		return sqmTranslatorFactory;
	}

	public QueryInterpretationCache getInterpretationCache() {
		return interpretationCache;
	}

	public SqmFunctionRegistry getSqmFunctionRegistry() {
		return sqmFunctionRegistry;
	}

	public void close() {
		if ( namedQueryRepository != null ) {
			namedQueryRepository.close();
		}

		if ( criteriaBuilder != null ) {
			criteriaBuilder.close();
		}

		if ( hqlTranslator != null ) {
			hqlTranslator.close();
		}

		if ( interpretationCache != null ) {
			interpretationCache.close();
		}

		if ( sqmFunctionRegistry != null ) {
			sqmFunctionRegistry.close();
		}
	}
}
