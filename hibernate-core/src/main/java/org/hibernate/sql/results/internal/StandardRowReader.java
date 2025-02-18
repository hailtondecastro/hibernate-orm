/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.internal;

import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.named.RowReaderMemento;
import org.hibernate.sql.exec.spi.Callback;
import org.hibernate.sql.results.spi.DomainResultAssembler;
import org.hibernate.sql.results.spi.EntityInitializer;
import org.hibernate.sql.results.spi.Initializer;
import org.hibernate.sql.results.spi.JdbcValuesSourceProcessingOptions;
import org.hibernate.sql.results.spi.JdbcValuesSourceProcessingState;
import org.hibernate.sql.results.spi.RowProcessingState;
import org.hibernate.sql.results.spi.RowReader;
import org.hibernate.sql.results.spi.RowTransformer;

import org.jboss.logging.Logger;

/**
 * @author Steve Ebersole
 */
public class StandardRowReader<T> implements RowReader<T> {
	private static final Logger LOG = Logger.getLogger( StandardRowReader.class );

	private final List<DomainResultAssembler> resultAssemblers;
	private final List<Initializer> initializers;
	private final RowTransformer<T> rowTransformer;

	private final int assemblerCount;
	private final Callback callback;

	public StandardRowReader(
			List<DomainResultAssembler> resultAssemblers,
			List<Initializer> initializers,
			RowTransformer<T> rowTransformer,
			Callback callback) {
		this.resultAssemblers = resultAssemblers;
		this.initializers = initializers;
		this.rowTransformer = rowTransformer;

		this.assemblerCount = resultAssemblers.size();
		this.callback = callback;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Class<T> getResultJavaType() {
		if ( resultAssemblers.size() == 1 ) {
			return resultAssemblers.get( 0 ).getAssembledJavaTypeDescriptor().getJavaType();
		}

		return (Class<T>) Object[].class;
	}

	@Override
	public List<Initializer> getInitializers() {
		return initializers;
	}

	@Override
	public int getNumberOfResults() {
		return rowTransformer.determineNumberOfResultElements( assemblerCount );
	}

	@Override
	public T readRow(RowProcessingState rowProcessingState, JdbcValuesSourceProcessingOptions options) {
		LOG.info( "---Processing Row---" );
		coordinateInitializers( rowProcessingState, options );

		// finally assemble the results

		final Object[] result = new Object[assemblerCount];
		for ( int i = 0; i < assemblerCount; i++ ) {
			result[i] = resultAssemblers.get( i ).assemble( rowProcessingState, options );
		}

		afterRow( rowProcessingState, options );

		return rowTransformer.transformRow( result );
	}

	private void afterRow(RowProcessingState rowProcessingState, JdbcValuesSourceProcessingOptions options) {
		// todo : add AfterLoadActions handling here via Callback

		for ( Initializer initializer : initializers ) {
			initializer.finishUpRow( rowProcessingState );
		}
	}

	private void coordinateInitializers(
			RowProcessingState rowProcessingState,
			JdbcValuesSourceProcessingOptions options) {

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// todo (6.0) : we may want to split handling of initializers into specific sub-type handling
		//		- meaning we'd have something like:

//		for ( EntityInitializer initializer : entityInitializers ) {
//			initializer.resolveKey( rowProcessingState );
//		}
//
//		for ( EntityInitializer initializer : collectionInitializers ) {
//			initializer.resolveKey( rowProcessingState );
//		}
//
//		for ( Initializer initializer : entityInitializers ) {
//			initializer.resolveInstance( rowProcessingState );
//		}
//
//		for ( EntityInitializer initializer : collectionInitializers ) {
//			initializer.resolveInstance( rowProcessingState );
//		}


		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// old

		for ( Initializer initializer : initializers ) {
			initializer.resolveKey( rowProcessingState );
		}

		for ( Initializer initializer : initializers ) {
			initializer.resolveInstance( rowProcessingState );
		}

		for ( Initializer initializer : initializers ) {
			initializer.initializeInstance( rowProcessingState );
		}
	}

	@Override
	public void finishUp(JdbcValuesSourceProcessingState processingState) {
		for ( Initializer initializer : initializers ) {
			initializer.endLoading( processingState.getExecutionContext() );
		}

		// todo : use Callback to execute AfterLoadActions
		// todo : another option is to use Callback to execute the AfterLoadActions after each row
	}

	@Override
	public RowReaderMemento toMemento(SessionFactoryImplementor factory) {
		return new RowReaderMemento() {
			@Override
			public Class[] getResultClasses() {
				return new Class[0];
			}

			@Override
			public String[] getResultMappingNames() {
				return new String[0];
			}
		};
	}
}
