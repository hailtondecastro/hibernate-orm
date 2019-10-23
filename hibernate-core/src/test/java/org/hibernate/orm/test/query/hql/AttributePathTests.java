/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import java.util.List;

import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.orm.test.query.sqm.BaseSqmUnitTest;
import org.hibernate.orm.test.query.sqm.domain.Person;
import org.hibernate.query.sqm.tree.domain.SqmEntityValuedSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.query.sqm.tree.from.SqmRoot;
import org.hibernate.query.sqm.tree.predicate.SqmComparisonPredicate;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hibernate.query.sqm.tree.select.SqmSelectableNode;
import org.hibernate.query.sqm.tree.select.SqmSelection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Steve Ebersole
 */
@SuppressWarnings("WeakerAccess")
@Tags({
	@Tag("RunnableIdeTest"),
})
public class AttributePathTests extends BaseSqmUnitTest {

	@Override
	protected Class[] getAnnotatedClasses() {
		return new Class[] { Person.class };
	}

	@Test
	public void testImplicitJoinReuse() {
		final SqmSelectStatement statement = interpretSelect( "select s.mate.dob, s.mate.numberOfToes from Person s" );

		assertThat( statement.getQuerySpec().getFromClause().getRoots().size(), is(1) );
		final SqmRoot sqmRoot = statement.getQuerySpec().getFromClause().getRoots().get( 0 );

		assertThat( sqmRoot.getJoins().size(), is(0) );
		assertThat( sqmRoot.getImplicitJoinPaths().size(), is(1) );

		// from-clause paths
//		assertPropertyPath( space.getRoot(), "com.acme.Something(s)" );
//		assertPropertyPath( space.getJoins().get( 0 ), "com.acme.Something(s).entity" );

		final List<SqmSelection> selections = statement.getQuerySpec().getSelectClause().getSelections();
		assertThat( selections.size(), is(2) );

		// expression paths
		assertPropertyPath( (SqmExpression) selections.get( 0 ).getSelectableNode(), Person.class.getName() + "(s).mate.dob" );
		assertPropertyPath( (SqmExpression) selections.get( 1 ).getSelectableNode(), Person.class.getName() + "(s).mate.numberOfToes" );
	}

	private void assertPropertyPath(SqmExpression expression, String expectedFullPath) {
		assertThat( expression, instanceOf( SqmSimplePath.class ) );
		final SqmSimplePath domainReferenceBinding = (SqmSimplePath) expression;
		assertThat( domainReferenceBinding.getNavigablePath().getFullPath(), is( expectedFullPath) );
	}

	@Test
	public void testImplicitJoinReuse2() {
		final SqmSelectStatement statement = interpretSelect( "select s.mate from Person s where s.mate.dob = ?1" );

		assertThat( statement.getQuerySpec().getFromClause().getRoots().size(), is(1) );
		final SqmRoot sqmRoot = statement.getQuerySpec().getFromClause().getRoots().get( 0 );

		assertThat( sqmRoot.getJoins().size(), is(0) );
		assertThat( sqmRoot.getImplicitJoinPaths().size(), is(1) );

		final SqmSelection selection = statement.getQuerySpec().getSelectClause().getSelections().get( 0 );
		assertThat( selection.getSelectableNode(), instanceOf( SqmEntityValuedSimplePath.class ) );

		final SqmPath selectExpression = (SqmPath) selection.getSelectableNode();
		assertThat( selectExpression.getReferencedPathSource().getSqmPathType(), instanceOf( EntityDomainType.class ) );

		final SqmComparisonPredicate predicate = (SqmComparisonPredicate) statement.getQuerySpec().getWhereClause().getPredicate();
		final SqmPath predicateLhs = (SqmPath) predicate.getLeftHandExpression();
		assertThat( predicateLhs.getLhs(), notNullValue() );


		// from-clause paths
//		assertPropertyPath( space.getRoot(), "com.acme.Something(s)" );
//		assertPropertyPath( space.getJoins().get( 0 ), "com.acme.Something(s).entity" );

		// expression paths
		assertPropertyPath( (SqmExpression) selection.getSelectableNode(), Person.class.getName() + "(s).mate" );
		assertPropertyPath( predicateLhs, Person.class.getName() + "(s).mate.dob" );
	}

	@Test
	public void testEntityIdReferences() {
		interpretSelect( "select s.mate from Person s where s.id = ?1" );
		interpretSelect( "select s.mate from Person s where s.{id} = ?1" );
		interpretSelect( "select s.mate from Person s where s.pk = ?1" );
	}

	@Test
	public void testManyToOneReference() {
		final SqmSelectStatement sqm = interpretSelect( "select s.mate from Person s" );
		final List<SqmSelection> selections = sqm.getQuerySpec().getSelectClause().getSelections();
		assertThat( selections.size(), is( 1 ) );
		final SqmSelection selection = selections.get( 0 );
		final SqmSelectableNode selectableNode = selection.getSelectableNode();
		assert Person.class.equals( selectableNode.getJavaTypeDescriptor().getJavaType() );
	}

}
