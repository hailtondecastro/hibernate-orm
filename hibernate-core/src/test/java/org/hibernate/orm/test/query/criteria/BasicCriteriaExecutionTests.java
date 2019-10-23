/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.criteria;

import java.util.List;
import java.util.Map;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Root;

import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.spi.QueryParameterImplementor;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.sql.exec.spi.JdbcParameter;

import org.hibernate.testing.junit4.BaseNonConfigCoreFunctionalTestCase;
import org.junit.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;

/**
 * @author Steve Ebersole
 */
@Tags({
	@Tag("RunnableIdeTest"),
})
public class BasicCriteriaExecutionTests extends BaseNonConfigCoreFunctionalTestCase {

	@Override
	protected Class[] getAnnotatedClasses() {
		return new Class[] { BasicEntity.class };
	}

	@Test
	public void testExecutingBasicCriteriaQuery() {
		final CriteriaBuilder criteriaBuilder = sessionFactory().getCriteriaBuilder();

		final CriteriaQuery<Object> criteria = criteriaBuilder.createQuery();

		final Root<BasicEntity> root = criteria.from( BasicEntity.class );

		criteria.select( root );

		inSession(
				session -> session.createQuery( criteria ).list()
		);
	}

	@Test
	public void testExecutingBasicCriteriaQueryInStatelessSession() {
		final CriteriaBuilder criteriaBuilder = sessionFactory().getCriteriaBuilder();

		final CriteriaQuery<Object> criteria = criteriaBuilder.createQuery();

		final Root<BasicEntity> root = criteria.from( BasicEntity.class );

		criteria.select( root );

		inStatelessSession(
				session -> session.createQuery( criteria ).list()
		);
	}

	@Test
	public void testExecutingBasicCriteriaQueryLiteralPredicate() {
		final CriteriaBuilder criteriaBuilder = sessionFactory().getCriteriaBuilder();

		final CriteriaQuery<Object> criteria = criteriaBuilder.createQuery();

		final Root<BasicEntity> root = criteria.from( BasicEntity.class );

		criteria.select( root );

		criteria.where( criteriaBuilder.equal( criteriaBuilder.literal( 1 ), criteriaBuilder.literal( 1 ) ) );

		inSession(
				session -> session.createQuery( criteria ).list()
		);
	}

	@Test
	public void testExecutingBasicCriteriaQueryLiteralPredicateInStatelessSession() {
		final CriteriaBuilder criteriaBuilder = sessionFactory().getCriteriaBuilder();

		final CriteriaQuery<Object> criteria = criteriaBuilder.createQuery();

		final Root<BasicEntity> root = criteria.from( BasicEntity.class );

		criteria.select( root );

		criteria.where( criteriaBuilder.equal( criteriaBuilder.literal( 1 ), criteriaBuilder.literal( 1 ) ) );

		inStatelessSession(
				session -> session.createQuery( criteria ).list()
		);
	}

	@Test
	public void testExecutingBasicCriteriaQueryParameterPredicate() {
		final CriteriaBuilder criteriaBuilder = sessionFactory().getCriteriaBuilder();

		final CriteriaQuery<Object> criteria = criteriaBuilder.createQuery();

		final Root<BasicEntity> root = criteria.from( BasicEntity.class );

		criteria.select( root );

		Map<QueryParameterImplementor,Map<SqmParameter, List<JdbcParameter>>> parameterResolutionMap;

		final ParameterExpression<Integer> param = criteriaBuilder.parameter( Integer.class );

		criteria.where( criteriaBuilder.equal( param, param ) );

		inSession(
				session -> session.createQuery( criteria ).setParameter( param, 1 ).list()
		);
	}

	@Test
	public void testExecutingBasicCriteriaQueryParameterPredicateInStatelessSession() {
		final CriteriaBuilder criteriaBuilder = sessionFactory().getCriteriaBuilder();

		final CriteriaQuery<Object> criteria = criteriaBuilder.createQuery();

		final Root<BasicEntity> root = criteria.from( BasicEntity.class );

		criteria.select( root );

		final ParameterExpression<Integer> param = criteriaBuilder.parameter( Integer.class );

		criteria.where( criteriaBuilder.equal( param, param ) );

		inStatelessSession(
				session -> session.createQuery( criteria ).setParameter( param, 1 ).list()
		);
	}

	@Test
	public void testCriteriaEntityJoin() {
		final HibernateCriteriaBuilder criteriaBuilder = sessionFactory().getCriteriaBuilder();

		final JpaCriteriaQuery<Object> criteria = criteriaBuilder.createQuery();

		final JpaRoot<BasicEntity> root = criteria.from( BasicEntity.class );
		root.join( BasicEntity.class );

		criteria.select( root );

		inSession(
				session -> session.createQuery( criteria ).list()
		);
	}

	@Entity(name = "BasicEntity")
	public static class BasicEntity {

		@Id
		@GeneratedValue
		private Integer id;

	}
}
