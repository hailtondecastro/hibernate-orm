/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.spi;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.SortOrder;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.internal.util.collections.StandardStack;
import org.hibernate.query.QueryLiteralRendering;
import org.hibernate.query.UnaryArithmeticOperator;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.tree.expression.BinaryArithmeticExpression;
import org.hibernate.sql.ast.tree.expression.CaseSearchedExpression;
import org.hibernate.sql.ast.tree.expression.CaseSimpleExpression;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.EntityTypeLiteral;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.QueryLiteral;
import org.hibernate.sql.ast.tree.expression.SelfRenderingExpression;
import org.hibernate.sql.ast.tree.expression.SqlSelectionExpression;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.UnaryOperation;
import org.hibernate.sql.ast.tree.from.FromClause;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.from.TableReferenceJoin;
import org.hibernate.sql.ast.tree.from.VirtualTableGroup;
import org.hibernate.sql.ast.tree.predicate.BetweenPredicate;
import org.hibernate.sql.ast.tree.predicate.ComparisonPredicate;
import org.hibernate.sql.ast.tree.predicate.FilterPredicate;
import org.hibernate.sql.ast.tree.predicate.GroupedPredicate;
import org.hibernate.sql.ast.tree.predicate.InListPredicate;
import org.hibernate.sql.ast.tree.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.tree.predicate.Junction;
import org.hibernate.sql.ast.tree.predicate.LikePredicate;
import org.hibernate.sql.ast.tree.predicate.NegatedPredicate;
import org.hibernate.sql.ast.tree.predicate.NullnessPredicate;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.predicate.SelfRenderingPredicate;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.ast.tree.select.SortSpecification;
import org.hibernate.sql.exec.internal.JdbcParametersImpl;
import org.hibernate.sql.exec.spi.JdbcParameter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.results.internal.EmptySqlSelection;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptorIndicators;
import org.hibernate.type.spi.TypeConfiguration;

import static org.hibernate.sql.ast.spi.SqlAppender.*;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractSqlAstWalker
		implements SqlAstWalker, SqlTypeDescriptorIndicators {

	// pre-req state
	private final SessionFactoryImplementor sessionFactory;
	private final SqlAppender sqlAppender = this::appendSql;

	// In-flight state
	private final StringBuilder sqlBuffer = new StringBuilder();
	private final List<JdbcParameterBinder> parameterBinders = new ArrayList<>();

	private final JdbcParametersImpl jdbcParameters = new JdbcParametersImpl();

	private final Stack<Clause> clauseStack = new StandardStack<>();

	@SuppressWarnings("WeakerAccess")
	protected AbstractSqlAstWalker(SessionFactoryImplementor sessionFactory) {
		this.sessionFactory = sessionFactory;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// for tests, for now
	public String getSql() {
		return sqlBuffer.toString();
	}

	@SuppressWarnings("WeakerAccess")
	public List<JdbcParameterBinder> getParameterBinders() {
		return parameterBinders;
	}
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


	@SuppressWarnings("unused")
	protected SqlAppender getSqlAppender() {
		return sqlAppender;
	}

	@SuppressWarnings("WeakerAccess")
	protected void appendSql(String fragment) {
		sqlBuffer.append( fragment );
	}

	@SuppressWarnings("WeakerAccess")
	protected void appendSql(char fragment) {
		sqlBuffer.append( fragment );
	}

	protected JdbcServices getJdbcServices() {
		return getSessionFactory().getJdbcServices();
	}

	protected boolean isCurrentlyInPredicate() {
		return clauseStack.getCurrent() == Clause.WHERE
				|| clauseStack.getCurrent() == Clause.HAVING;
	}

	protected Stack<Clause> getClauseStack() {
		return clauseStack;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// QuerySpec

	@Override
	public void visitQuerySpec(QuerySpec querySpec) {
		if ( !querySpec.isRoot() ) {
			appendSql( EMPTY_STRING + OPEN_PARENTHESIS );
		}

		visitSelectClause( querySpec.getSelectClause() );
		visitFromClause( querySpec.getFromClause() );

		if ( querySpec.getWhereClauseRestrictions() != null && !querySpec.getWhereClauseRestrictions().isEmpty() ) {
			appendSql( EMPTY_STRING + WHERE_KEYWORD + EMPTY_STRING );

			clauseStack.push( Clause.WHERE );
			try {
				querySpec.getWhereClauseRestrictions().accept( this );
			}
			finally {
				clauseStack.pop();
			}
		}

		final List<SortSpecification> sortSpecifications = querySpec.getSortSpecifications();
		if ( sortSpecifications != null && !sortSpecifications.isEmpty() ) {
			appendSql( EMPTY_STRING + ORDER_BY_KEYWORD + EMPTY_STRING );

			String separator = NO_SEPARATOR;
			for (SortSpecification sortSpecification : sortSpecifications ) {
				appendSql( separator );
				visitSortSpecification( sortSpecification );
				separator = COMA_SEPARATOR;
			}
		}

		visitLimitOffsetClause( querySpec );

		if ( !querySpec.isRoot() ) {
			appendSql( COLLATE_KEYWORD + EMPTY_STRING );
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ORDER BY clause

	@Override
	public void visitSortSpecification(SortSpecification sortSpecification) {
		sortSpecification.getSortExpression().accept( this );

		final String collation = sortSpecification.getCollation();
		if ( collation != null ) {
			appendSql( EMPTY_STRING + COLLATE_KEYWORD + EMPTY_STRING );
			appendSql( collation );
		}

		final SortOrder sortOrder = sortSpecification.getSortOrder();
		if ( sortOrder == SortOrder.ASCENDING ) {
			appendSql( EMPTY_STRING + ASC_KEYWORD );
		}
		else if ( sortOrder == SortOrder.DESCENDING ) {
			appendSql( EMPTY_STRING + DESC_KEYWORD );
		}

		// TODO: null precedence handling
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// LIMIT/OFFSET clause

	@Override
	public void visitLimitOffsetClause(QuerySpec querySpec) {
		if ( querySpec.getOffsetClauseExpression() != null ) {
			renderOffset( querySpec );
		}

		if ( querySpec.getLimitClauseExpression() != null ) {
			renderLimit( querySpec );
		}
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderOffset(QuerySpec querySpec) {
		appendSql( " offset " );
		querySpec.getOffsetClauseExpression().accept( this );
		appendSql( " rows" );
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderLimit(QuerySpec querySpec) {
		appendSql( " fetch first " );
		querySpec.getLimitClauseExpression().accept( this );
		appendSql( " rows only" );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SELECT clause

	@Override
	public void visitSelectClause(SelectClause selectClause) {
		clauseStack.push( Clause.SELECT );

		try {
			appendSql( SELECT_KEYWORD + EMPTY_STRING );
			if ( selectClause.isDistinct() ) {
				appendSql( DISTINCT_KEYWORD + EMPTY_STRING );
			}

			String separator = NO_SEPARATOR;
			for ( SqlSelection sqlSelection : selectClause.getSqlSelections() ) {
				if ( sqlSelection instanceof EmptySqlSelection ) {
					continue;
				}
				appendSql( separator );
				sqlSelection.accept( this );
				separator = COMA_SEPARATOR;
			}
		}
		finally {
			clauseStack.pop();
		}
	}

	@Override
	public void visitSqlSelection(SqlSelection sqlSelection) {
		// do nothing... this is handled #visitSelectClause
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// FROM clause

	@Override
	public void visitFromClause(FromClause fromClause) {
		appendSql( EMPTY_STRING + FROM_KEYWORD + EMPTY_STRING );

		String separator = NO_SEPARATOR;
		for ( TableGroup root : fromClause.getRoots() ) {
			appendSql( separator );
			renderTableGroup( root );
			separator = COMA_SEPARATOR;
		}
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderTableGroup(TableGroup tableGroup) {
		// NOTE : commented out blocks render the TableGroup as a CTE

//		if ( tableGroup.getGroupAlias() !=  null ) {
//			sqlAppender.appendSql( OPEN_PARENTHESIS );
//		}

		renderTableReference( tableGroup.getPrimaryTableReference() );
		renderTableReferenceJoins( tableGroup );

//		if ( tableGroup.getGroupAlias() !=  null ) {
//			sqlAppender.appendSql( CLOSE_PARENTHESIS );
//			sqlAppender.appendSql( AS_KEYWORD );
//			sqlAppender.appendSql( tableGroup.getGroupAlias() );
//		}

		processTableGroupJoins( tableGroup );
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderTableReference(TableReference tableReference) {
		sqlAppender.appendSql( tableReference.getTableExpression() );

		final String identificationVariable = tableReference.getIdentificationVariable();
		if ( identificationVariable != null ) {
			sqlAppender.appendSql( EMPTY_STRING + AS_KEYWORD + EMPTY_STRING + identificationVariable );
		}
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderTableReferenceJoins(TableGroup tableGroup) {
		final List<TableReferenceJoin> joins = tableGroup.getTableReferenceJoins();
		if ( joins == null || joins.isEmpty() ) {
			return;
		}

		for ( TableReferenceJoin tableJoin : joins ) {
			sqlAppender.appendSql( EMPTY_STRING );
			sqlAppender.appendSql( tableJoin.getJoinType().getText() );
			sqlAppender.appendSql( EMPTY_STRING + JOIN_KEYWORD + EMPTY_STRING);

			renderTableReference( tableJoin.getJoinedTableReference() );

			if ( tableJoin.getJoinPredicate() != null && !tableJoin.getJoinPredicate().isEmpty() ) {
				sqlAppender.appendSql( EMPTY_STRING + ON_KEYWORD + EMPTY_STRING );
				tableJoin.getJoinPredicate().accept( this );
			}
		}
	}

	@SuppressWarnings("WeakerAccess")
	protected void processTableGroupJoins(TableGroup source) {
		source.visitTableGroupJoins( this::processTableGroupJoin );
	}

	@SuppressWarnings("WeakerAccess")
	protected void processTableGroupJoin(TableGroupJoin tableGroupJoin) {
		final TableGroup joinedGroup = tableGroupJoin.getJoinedGroup();

		//noinspection StatementWithEmptyBody
		if ( joinedGroup instanceof VirtualTableGroup ) {
			// nothing to do
			// todo (6.0) : join predicates?
		}
		else {
			appendSql( EMPTY_STRING );
			appendSql( tableGroupJoin.getJoinType().getText() );
			appendSql( EMPTY_STRING + JOIN_KEYWORD + EMPTY_STRING);

			renderTableGroup( joinedGroup );

			clauseStack.push( Clause.WHERE );
			try {
				if ( tableGroupJoin.getPredicate() != null && !tableGroupJoin.getPredicate().isEmpty() ) {
					appendSql( EMPTY_STRING + ON_KEYWORD + EMPTY_STRING );
					tableGroupJoin.getPredicate().accept( this );
				}
			}
			finally {
				clauseStack.pop();
			}
		}
	}

	@Override
	public void visitTableGroup(TableGroup tableGroup) {
		// TableGroup and TableGroup handling should be performed as part of `#visitFromClause`...

		// todo (6.0) : what is the correct behavior here?
	}

	@Override
	public void visitTableGroupJoin(TableGroupJoin tableGroupJoin) {
		// TableGroup and TableGroupJoin handling should be performed as part of `#visitFromClause`...

		// todo (6.0) : what is the correct behavior here?
	}

	@Override
	public void visitTableReference(TableReference tableReference) {
		// nothing to do... handled via TableGroup#render
	}

	@Override
	public void visitTableReferenceJoin(TableReferenceJoin tableReferenceJoin) {
		// nothing to do... handled within TableGroup#render
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Expressions

	@Override
	public void visitColumnReference(ColumnReference columnReference) {
		appendSql( columnReference.renderSqlFragment( getSessionFactory() ) );
	}

	@Override
	public void visitParameter(JdbcParameter jdbcParameter) {
		appendSql( PARAM_MARKER );

		parameterBinders.add( jdbcParameter.getParameterBinder() );
		jdbcParameters.addParameter( jdbcParameter );
	}

	@Override
	public void visitTuple(SqlTuple tuple) {
		String separator = NO_SEPARATOR;

		boolean isCurrentWhereClause = clauseStack.getCurrent() == Clause.WHERE;
		if ( isCurrentWhereClause ) {
			appendSql( OPEN_PARENTHESIS );
		}

		for ( Expression expression : tuple.getExpressions() ) {
			appendSql( separator );
			expression.accept( this );
			separator = COMA_SEPARATOR;
		}

		if ( isCurrentWhereClause ) {
			appendSql( CLOSE_PARENTHESIS );
		}
	}

	@Override
	public void visitSqlSelectionExpression(SqlSelectionExpression expression) {
		final boolean useSelectionPosition = getSessionFactory().getJdbcServices()
				.getDialect()
				.replaceResultVariableInOrderByClauseWithPosition();

		if ( useSelectionPosition ) {
			appendSql( Integer.toString( expression.getSelection().getJdbcResultSetIndex() ) );
		}
		else {
			expression.getExpression().accept( this );
		}
	}


//	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//	// Expression : Function : Non-Standard
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitNonStandardFunctionExpression(NonStandardFunction function) {
//		appendSql( function.getFunctionName() );
//		if ( !function.getArguments().isEmpty() ) {
//			appendSql( OPEN_PARENTHESIS );
//			String separator = NO_SEPARATOR;
//			for ( Expression argumentExpression : function.getArguments() ) {
//				appendSql( separator );
//				argumentExpression.accept( this );
//				separator = COMA_SEPARATOR;
//			}
//			appendSql( CLOSE_PARENTHESIS );
//		}
//	}
//
//
//	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//	// Expression : Function : Standard
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitAbsFunction(AbsFunction function) {
//		appendSql( "abs(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitAvgFunction(AvgFunction function) {
//		appendSql( "avg(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitBitLengthFunction(BitLengthFunction function) {
//		appendSql( "bit_length(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitCastFunction(CastFunction function) {
//		sqlAppender.appendSql( "cast(" );
//		function.getExpressionToCast().accept( this );
//		sqlAppender.appendSql( AS_KEYWORD );
//		sqlAppender.appendSql( determineCastTargetTypeSqlExpression( function ) );
//		sqlAppender.appendSql( CLOSE_PARENTHESIS );
//	}
//
//	private String determineCastTargetTypeSqlExpression(CastFunction castFunction) {
//		if ( castFunction.getExplicitCastTargetTypeSqlExpression() != null ) {
//			return castFunction.getExplicitCastTargetTypeSqlExpression();
//		}
//
//		final SqlExpressableType castResultType = castFunction.getCastResultType();
//
//		if ( castResultType == null ) {
//			throw new SqlTreeException(
//					"CastFunction did not define an explicit cast target SQL expression and its return type was null"
//			);
//		}
//
//		final BasicJavaDescriptor javaTypeDescriptor = castResultType.getJavaTypeDescriptor();
//		return getJdbcServices()
//				.getDialect()
//				.getCastTypeName( javaTypeDescriptor.getJdbcRecommendedSqlType( this ).getJdbcTypeCode() );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitConcatFunction(ConcatFunction function) {
//		appendSql( "concat(" );
//
//		boolean firstPass = true;
//		for ( Expression expression : function.getExpressions() ) {
//			if ( ! firstPass ) {
//				appendSql( COMA_SEPARATOR );
//			}
//			expression.accept( this );
//			firstPass = false;
//		}
//
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitSubstrFunction(SubstrFunction function) {
//		appendSql( "substr(" );
//
//		boolean firstPass = true;
//		for ( Expression expression : function.getExpressions() ) {
//			if ( ! firstPass ) {
//				appendSql( COMA_SEPARATOR );
//			}
//			expression.accept( this );
//			firstPass = false;
//		}
//
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitCountFunction(CountFunction function) {
//		appendSql( "count(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	public void visitCountStarFunction(CountStarFunction function) {
//		appendSql( "count(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		appendSql( "*)" );
//	}
//
//	@Override
//	public void visitCurrentDateFunction(CurrentDateFunction function) {
//		appendSql( "current_date" );
//	}
//
//	@Override
//	public void visitCurrentTimeFunction(CurrentTimeFunction function) {
//		appendSql( "current_time" );
//	}
//
//	@Override
//	public void visitCurrentTimestampFunction(CurrentTimestampFunction function) {
//		appendSql( "current_timestamp" );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitExtractFunction(ExtractFunction extractFunction) {
//		appendSql( "extract(" );
//		extractFunction.getUnitToExtract().accept( this );
//		appendSql( FROM_KEYWORD );
//		extractFunction.getExtractionSource().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitLengthFunction(LengthFunction function) {
//		sqlAppender.appendSql( "length(" );
//		function.getArgument().accept( this );
//		sqlAppender.appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitLocateFunction(LocateFunction function) {
//		appendSql( "locate(" );
//		function.getPatternString().accept( this );
//		appendSql( COMA_SEPARATOR );
//		function.getStringToSearch().accept( this );
//		if ( function.getStartPosition() != null ) {
//			appendSql( COMA_SEPARATOR );
//			function.getStartPosition().accept( this );
//		}
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitLowerFunction(LowerFunction function) {
//		appendSql( "lower(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitMaxFunction(MaxFunction function) {
//		appendSql( "max(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitMinFunction(MinFunction function) {
//		appendSql( "min(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitModFunction(ModFunction function) {
//		sqlAppender.appendSql( "mod(" );
//		function.getDividend().accept( this );
//		sqlAppender.appendSql( COMA_SEPARATOR );
//		function.getDivisor().accept( this );
//		sqlAppender.appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitSqrtFunction(SqrtFunction function) {
//		appendSql( "sqrt(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitSumFunction(SumFunction function) {
//		appendSql( "sum(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitTrimFunction(TrimFunction function) {
//		sqlAppender.appendSql( "trim(" );
//		sqlAppender.appendSql( function.getSpecification().toSqlText() );
//		sqlAppender.appendSql( EMPTY_STRING_SEPARATOR );
//		function.getTrimCharacter().accept( this );
//		sqlAppender.appendSql( FROM_KEYWORD );
//		function.getSource().accept( this );
//		sqlAppender.appendSql( CLOSE_PARENTHESIS );
//
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitUpperFunction(UpperFunction function) {
//		appendSql( "upper(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	public void visitCoalesceFunction(CoalesceFunction coalesceExpression) {
//		appendSql( "coalesce(" );
//		String separator = NO_SEPARATOR;
//		for ( Expression expression : coalesceExpression.getValues() ) {
//			appendSql( separator );
//			expression.accept( this );
//			separator = COMA_SEPARATOR;
//		}
//
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	public void visitNullifFunction(NullifFunction function) {
//		appendSql( "nullif(" );
//		function.getFirstArgument().accept( this );
//		appendSql( COMA_SEPARATOR );
//		function.getSecondArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}


	@Override
	public void visitEntityTypeLiteral(EntityTypeLiteral expression) {
		throw new NotYetImplementedFor6Exception( "Mapping model subclass support not yet implemented" );
//		final EntityPersister entityTypeDescriptor = expression.getEntityTypeDescriptor();
//		final DiscriminatorDescriptor<?> discriminatorDescriptor = expression.getDiscriminatorDescriptor();
//
//		final Object discriminatorValue = discriminatorDescriptor.getDiscriminatorMappings()
//				.entityNameToDiscriminatorValue( entityTypeDescriptor.getEntityName() );
//
//		appendSql( discriminatorValue.toString() );
	}

	@Override
	public void visitBinaryArithmeticExpression(BinaryArithmeticExpression arithmeticExpression) {
		arithmeticExpression.getLeftHandOperand().accept( this );
		appendSql( arithmeticExpression.getOperator().getOperatorSqlTextString() );
		arithmeticExpression.getRightHandOperand().accept( this );
	}

	@Override
	public void visitCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression) {
		appendSql( CASE_KEYWORD + EMPTY_STRING );
		for ( CaseSearchedExpression.WhenFragment whenFragment : caseSearchedExpression.getWhenFragments() ) {
			appendSql( EMPTY_STRING + WHEN_KEYWORD + EMPTY_STRING );
			whenFragment.getPredicate().accept( this );
			appendSql( EMPTY_STRING + THEN_KEYWORD + EMPTY_STRING );
			whenFragment.getResult().accept( this );
		}
		appendSql( ELSE_KEYWORD );

		caseSearchedExpression.getOtherwise().accept( this );
		appendSql( EMPTY_STRING + END_KEYWORD );
	}

	@Override
	public void visitCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression) {
		appendSql( CASE_KEYWORD + EMPTY_STRING );
		caseSimpleExpression.getFixture().accept( this );
		for ( CaseSimpleExpression.WhenFragment whenFragment : caseSimpleExpression.getWhenFragments() ) {
			appendSql( EMPTY_STRING + WHEN_KEYWORD + EMPTY_STRING );
			whenFragment.getCheckValue().accept( this );
			appendSql( EMPTY_STRING + THEN_KEYWORD + EMPTY_STRING );
			whenFragment.getResult().accept( this );
		}
		appendSql( EMPTY_STRING + ELSE_KEYWORD + EMPTY_STRING );

		caseSimpleExpression.getOtherwise().accept( this );
		appendSql( EMPTY_STRING + END_KEYWORD );
	}


//	@Override
//	public void visitGenericParameter(GenericParameter parameter) {
//		visitJdbcParameterBinder( parameter.getParameterBinder() );
//
//		if ( parameter instanceof JdbcParameter ) {
//			jdbcParameters.addParameter( (JdbcParameter) parameter );
//		}
//	}

	protected void visitJdbcParameterBinder(JdbcParameterBinder jdbcParameterBinder) {
		parameterBinders.add( jdbcParameterBinder );

		// todo (6.0) : ? wrap in cast function call if the literal occurs in SELECT (?based on Dialect?)

		appendSql( "?" );
	}

//	@Override
//	public void visitNamedParameter(NamedParameter namedParameter) {
//		visitJdbcParameterBinder( namedParameter );
//	}
//
//	@Override
//	public void visitPositionalParameter(PositionalParameter positionalParameter) {
//		visitJdbcParameterBinder( positionalParameter );
//	}

	@Override
	public void visitQueryLiteral(QueryLiteral queryLiteral) {
		final QueryLiteralRendering queryLiteralRendering = getSessionFactory().getSessionFactoryOptions().getQueryLiteralRenderingMode();

		switch( queryLiteralRendering ) {
			case AS_LITERAL: {
				renderAsLiteral( queryLiteral );
				break;
			}
			case AS_PARAM: {
				visitJdbcParameterBinder( queryLiteral );
				break;
			}
			case AUTO:
			case AS_PARAM_OUTSIDE_SELECT: {
				if ( queryLiteral.isInSelect() ) {
					renderAsLiteral( queryLiteral );
				}
				else {
					visitJdbcParameterBinder( queryLiteral );
				}
				break;
			}
			default: {
				throw new IllegalArgumentException(
						"Unrecognized QueryLiteralRendering : " + queryLiteralRendering
				);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void renderAsLiteral(QueryLiteral<?> queryLiteral) {
		if ( queryLiteral.getValue() == null ) {
			// todo : not sure we allow this "higher up"
			appendSql( SqlAppender.NULL_KEYWORD );
		}
		else {
			assert queryLiteral.getExpressionType().getJdbcTypeCount( getTypeConfiguration() ) == 1;
			queryLiteral.visitJdbcTypes(
					jdbcMapping -> {
						final JdbcLiteralFormatter literalFormatter = jdbcMapping.getSqlTypeDescriptor().getJdbcLiteralFormatter( jdbcMapping.getJavaTypeDescriptor() );
						appendSql(
								literalFormatter.toJdbcLiteral(
										queryLiteral.getValue(),
										sessionFactory.getJdbcServices().getJdbcEnvironment().getDialect(),
										null
								)
						);
					},
					getTypeConfiguration()
			);
		}
	}

	@Override
	public void visitUnaryOperationExpression(UnaryOperation unaryOperationExpression) {
		if ( unaryOperationExpression.getOperator() == UnaryArithmeticOperator.UNARY_PLUS ) {
			appendSql( UnaryArithmeticOperator.UNARY_PLUS.getOperatorChar() );
		}
		else {
			appendSql( UnaryArithmeticOperator.UNARY_MINUS.getOperatorChar() );
		}

		unaryOperationExpression.getOperand().accept( this );
	}

	@Override
	public void visitSelfRenderingPredicate(SelfRenderingPredicate selfRenderingPredicate) {
		selfRenderingPredicate.getSelfRenderingExpression().accept( this );
	}

	@Override
	public void visitSelfRenderingExpression(SelfRenderingExpression expression) {
		expression.renderToSql( sqlAppender, this, getSessionFactory() );
	}

//	@Override
//	public void visitPluralAttribute(PluralAttributeReference pluralAttributeReference) {
//		// todo (6.0) - is this valid in the general sense?  Or specific to things like order-by rendering?
//		//		long story short... what should we do here?
//	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Predicates

	@Override
	public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {
		betweenPredicate.getExpression().accept( this );
		if ( betweenPredicate.isNegated() ) {
			appendSql( EMPTY_STRING + NOT_KEYWORD );
		}
		appendSql( EMPTY_STRING + BETWEEN_KEYWORD + EMPTY_STRING );
		betweenPredicate.getLowerBound().accept( this );
		appendSql( EMPTY_STRING + AND_KEYWORD + EMPTY_STRING );
		betweenPredicate.getUpperBound().accept( this );
	}

	@Override
	public void visitFilterPredicate(FilterPredicate filterPredicate) {
		throw new NotYetImplementedFor6Exception();
	}

	@Override
	public void visitGroupedPredicate(GroupedPredicate groupedPredicate) {
		if ( groupedPredicate.isEmpty() ) {
			return;
		}

		appendSql( OPEN_PARENTHESIS );
		groupedPredicate.getSubPredicate().accept( this );
		appendSql( CLOSE_PARENTHESIS );
	}

	@Override
	public void visitInListPredicate(InListPredicate inListPredicate) {
		inListPredicate.getTestExpression().accept( this );
		if ( inListPredicate.isNegated() ) {
			appendSql( NOT_KEYWORD );
		}
		appendSql( IN_KEYWORD + ' ' + OPEN_PARENTHESIS );
		if ( inListPredicate.getListExpressions().isEmpty() ) {
			appendSql( NULL_KEYWORD );
		}
		else {
			String separator = NO_SEPARATOR;
			for ( Expression expression : inListPredicate.getListExpressions() ) {
				appendSql( separator );
				expression.accept( this );
				separator = COMA_SEPARATOR;
			}
		}
		appendSql( CLOSE_PARENTHESIS );
	}

	@Override
	public void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate) {
		inSubQueryPredicate.getTestExpression().accept( this );
		if ( inSubQueryPredicate.isNegated() ) {
			appendSql( ' ' + NOT_KEYWORD );
		}
		appendSql( ' ' + IN_KEYWORD + ' ' );
		visitQuerySpec( inSubQueryPredicate.getSubQuery() );
	}

	@Override
	public void visitJunction(Junction junction) {
		if ( junction.isEmpty() ) {
			return;
		}

		String separator = NO_SEPARATOR;
		for ( Predicate predicate : junction.getPredicates() ) {
			appendSql( separator );
			predicate.accept( this );
			separator = junction.getNature() == Junction.Nature.CONJUNCTION
					? EMPTY_STRING + AND_KEYWORD + EMPTY_STRING
					: EMPTY_STRING + OR_KEYWORD + EMPTY_STRING;
		}
	}

	@Override
	public void visitLikePredicate(LikePredicate likePredicate) {
		likePredicate.getMatchExpression().accept( this );
		if ( likePredicate.isNegated() ) {
			appendSql( EMPTY_STRING + NOT_KEYWORD );
		}
		appendSql( EMPTY_STRING + LIKE_KEYWORD + EMPTY_STRING );
		likePredicate.getPattern().accept( this );
		if ( likePredicate.getEscapeCharacter() != null ) {
			appendSql( EMPTY_STRING + ESCAPE_KEYWORD + EMPTY_STRING );
			likePredicate.getEscapeCharacter().accept( this );
		}
	}

	@Override
	public void visitNegatedPredicate(NegatedPredicate negatedPredicate) {
		if ( negatedPredicate.isEmpty() ) {
			return;
		}

		appendSql( NOT_KEYWORD + EMPTY_STRING + OPEN_PARENTHESIS );
		negatedPredicate.getPredicate().accept( this );
		appendSql( CLOSE_PARENTHESIS );
	}

	@Override
	public void visitNullnessPredicate(NullnessPredicate nullnessPredicate) {
		nullnessPredicate.getExpression().accept( this );
		if ( nullnessPredicate.isNegated() ) {
			appendSql( IS_NOT_NULL_FRAGMENT );
		}
		else {
			appendSql( IS_NULL_FRAGMENT );
		}
	}

	@Override
	public void visitRelationalPredicate(ComparisonPredicate comparisonPredicate) {
		// todo (6.0) : do we want to allow multi-valued parameters in a relational predicate?
		//		yes means we'd have to support dynamically converting this predicate into
		//		an IN predicate or an OR predicate
		//
		//		NOTE: JPA does not define support for multi-valued parameters here.
		//
		// If we decide to support that ^^  we should validate that *both* sides of the
		//		predicate are multi-valued parameters.  because...
		//		well... its stupid :)
//		if ( relationalPredicate.getLeftHandExpression() instanceof GenericParameter ) {
//			final GenericParameter lhs =
//			// transform this into a
//		}
//
		comparisonPredicate.getLeftHandExpression().accept( this );
		appendSql( comparisonPredicate.getOperator().sqlText() );
		comparisonPredicate.getRightHandExpression().accept( this );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// JdbcRecommendedSqlTypeMappingContext

	@Override
	public boolean isNationalized() {
		return false;
	}

	@Override
	public boolean isLob() {
		return false;
	}

	@Override
	public TypeConfiguration getTypeConfiguration() {
		return getSessionFactory().getTypeConfiguration();
	}

	@Override
	public SessionFactoryImplementor getSessionFactory() {
		return sessionFactory;
	}
}
