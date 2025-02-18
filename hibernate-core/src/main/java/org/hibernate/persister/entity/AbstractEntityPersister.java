/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.persister.entity;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.hibernate.AssertionFailure;
import org.hibernate.EntityMode;
import org.hibernate.FetchMode;
import org.hibernate.HibernateException;
import org.hibernate.JDBCException;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.MappingException;
import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.QueryException;
import org.hibernate.Session;
import org.hibernate.StaleObjectStateException;
import org.hibernate.StaleStateException;
import org.hibernate.bytecode.enhance.spi.LazyPropertyInitializer;
import org.hibernate.bytecode.enhance.spi.interceptor.BytecodeLazyAttributeInterceptor;
import org.hibernate.bytecode.enhance.spi.interceptor.EnhancementAsProxyLazinessInterceptor;
import org.hibernate.bytecode.enhance.spi.interceptor.EnhancementHelper;
import org.hibernate.bytecode.enhance.spi.interceptor.LazyAttributeDescriptor;
import org.hibernate.bytecode.enhance.spi.interceptor.LazyAttributeLoadingInterceptor;
import org.hibernate.bytecode.enhance.spi.interceptor.LazyAttributesMetadata;
import org.hibernate.bytecode.spi.BytecodeEnhancementMetadata;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.cache.spi.entry.CacheEntryStructure;
import org.hibernate.cache.spi.entry.ReferenceCacheEntryImpl;
import org.hibernate.cache.spi.entry.StandardCacheEntryImpl;
import org.hibernate.cache.spi.entry.StructuredCacheEntry;
import org.hibernate.cache.spi.entry.UnstructuredCacheEntry;
import org.hibernate.classic.Lifecycle;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.lock.LockingStrategy;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.internal.CacheHelper;
import org.hibernate.engine.internal.ImmutableEntityEntryFactory;
import org.hibernate.engine.internal.MutableEntityEntryFactory;
import org.hibernate.engine.internal.StatefulPersistenceContext;
import org.hibernate.engine.internal.Versioning;
import org.hibernate.engine.jdbc.batch.internal.BasicBatchKey;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.CachedNaturalIdValueSource;
import org.hibernate.engine.spi.CascadeStyle;
import org.hibernate.engine.spi.CascadingActions;
import org.hibernate.engine.spi.CollectionKey;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.EntityEntryFactory;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.ExecuteUpdateResultCheckStyle;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.PersistenceContext.NaturalIdHelper;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.hibernate.engine.spi.PersistentAttributeInterceptor;
import org.hibernate.engine.spi.SelfDirtinessTracker;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.ValueInclusion;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.PostInsertIdentifierGenerator;
import org.hibernate.id.PostInsertIdentityPersister;
import org.hibernate.id.insert.Binder;
import org.hibernate.id.insert.InsertGeneratedIdentifierDelegate;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.FilterHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.jdbc.Expectation;
import org.hibernate.jdbc.Expectations;
import org.hibernate.jdbc.TooManyRowsAffectedException;
import org.hibernate.loader.custom.sql.SQLQueryParser;
import org.hibernate.loader.entity.BatchingEntityLoaderBuilder;
import org.hibernate.loader.entity.CascadeEntityLoader;
import org.hibernate.loader.entity.EntityLoader;
import org.hibernate.loader.entity.UniqueEntityLoader;
import org.hibernate.loader.internal.MultiIdEntityLoaderStandardImpl;
import org.hibernate.loader.internal.NaturalIdLoaderStandardImpl;
import org.hibernate.loader.internal.Preparable;
import org.hibernate.loader.internal.SingleIdEntityLoaderProvidedQueryImpl;
import org.hibernate.loader.internal.SingleIdEntityLoaderStandardImpl;
import org.hibernate.loader.spi.Loader;
import org.hibernate.loader.spi.MultiIdEntityLoader;
import org.hibernate.loader.spi.NaturalIdLoader;
import org.hibernate.loader.spi.SingleIdEntityLoader;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Subclass;
import org.hibernate.mapping.Table;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.metamodel.RepresentationMode;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.AttributeMetadata;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.EntityVersionMapping;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.ManagedMappingType;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.NaturalIdMapping;
import org.hibernate.metamodel.mapping.Queryable;
import org.hibernate.metamodel.mapping.StateArrayContributorMapping;
import org.hibernate.metamodel.mapping.StateArrayContributorMetadata;
import org.hibernate.metamodel.mapping.internal.InFlightEntityMappingType;
import org.hibernate.metamodel.mapping.internal.MappingModelCreationHelper;
import org.hibernate.metamodel.mapping.internal.MappingModelCreationProcess;
import org.hibernate.metamodel.model.domain.NavigableRole;
import org.hibernate.metamodel.spi.EntityRepresentationStrategy;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.spi.PersisterCreationContext;
import org.hibernate.persister.walking.internal.EntityIdentifierDefinitionHelper;
import org.hibernate.persister.walking.spi.AttributeDefinition;
import org.hibernate.persister.walking.spi.EntityIdentifierDefinition;
import org.hibernate.pretty.MessageHelper;
import org.hibernate.property.access.internal.PropertyAccessStrategyBackRefImpl;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.property.access.spi.Setter;
import org.hibernate.query.ComparisonOperator;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.sqm.sql.SqlExpressionResolver;
import org.hibernate.sql.Alias;
import org.hibernate.sql.Delete;
import org.hibernate.sql.Insert;
import org.hibernate.sql.JoinFragment;
import org.hibernate.sql.JoinType;
import org.hibernate.sql.Select;
import org.hibernate.sql.SelectFragment;
import org.hibernate.sql.SimpleSelect;
import org.hibernate.sql.Template;
import org.hibernate.sql.Update;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.spi.SqlAliasBase;
import org.hibernate.sql.ast.spi.SqlAliasBaseGenerator;
import org.hibernate.sql.ast.spi.SqlAliasStemHelper;
import org.hibernate.sql.ast.spi.SqlAstCreationContext;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.from.StandardTableGroup;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.from.TableReferenceJoin;
import org.hibernate.sql.ast.tree.predicate.ComparisonPredicate;
import org.hibernate.sql.ast.tree.predicate.Junction;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.results.internal.domain.entity.EntityResultImpl;
import org.hibernate.sql.results.spi.DomainResult;
import org.hibernate.sql.results.spi.DomainResultCreationState;
import org.hibernate.sql.results.spi.Fetchable;
import org.hibernate.sql.results.spi.FetchableContainer;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.hibernate.tuple.GenerationTiming;
import org.hibernate.tuple.InDatabaseValueGenerationStrategy;
import org.hibernate.tuple.InMemoryValueGenerationStrategy;
import org.hibernate.tuple.NonIdentifierAttribute;
import org.hibernate.tuple.ValueGeneration;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.type.AssociationType;
import org.hibernate.type.BasicType;
import org.hibernate.type.CollectionType;
import org.hibernate.type.ComponentType;
import org.hibernate.type.CompositeType;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;
import org.hibernate.type.TypeHelper;
import org.hibernate.type.VersionType;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * Basic functionality for persisting an entity via JDBC
 * through either generated or custom SQL
 *
 * @author Gavin King
 */
public abstract class AbstractEntityPersister
		implements OuterJoinLoadable, Queryable, ClassMetadata, UniqueKeyLoadable,
		SQLLoadable, LazyPropertyInitializer, PostInsertIdentityPersister, Lockable, org.hibernate.persister.entity.Queryable {

	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( AbstractEntityPersister.class );

	public static final String ENTITY_CLASS = "class";


	private final String sqlAliasStem;






	private final NavigableRole navigableRole;

	// moved up from AbstractEntityPersister ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	private final SessionFactoryImplementor factory;
	private final boolean canReadFromCache;
	private final boolean canWriteToCache;
	private final boolean invalidateCache;
	private final EntityDataAccess cacheAccessStrategy;
	private final NaturalIdDataAccess naturalIdRegionAccessStrategy;
	private final boolean isLazyPropertiesCacheable;
	private final CacheEntryHelper cacheEntryHelper;
	private final EntityMetamodel entityMetamodel;
	private final EntityEntryFactory entityEntryFactory;
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private final String[] rootTableKeyColumnNames;
	private final String[] rootTableKeyColumnReaders;
	private final String[] rootTableKeyColumnReaderTemplates;
	private final String[] identifierAliases;
	private final int identifierColumnSpan;
	private final String versionColumnName;
	private final boolean hasFormulaProperties;
	protected final int batchSize;
	private final boolean hasSubselectLoadableCollections;
	protected final String rowIdName;

	// The optional SQL string defined in the where attribute
	private final String sqlWhereString;
	private final String sqlWhereStringTemplate;

	//information about properties of this class,
	//including inherited properties
	//(only really needed for updatable/insertable properties)
	private final int[] propertyColumnSpans;
	private final String[] propertySubclassNames;
	private final String[][] propertyColumnAliases;
	private final String[][] propertyColumnNames;
	private final String[][] propertyColumnFormulaTemplates;
	private final String[][] propertyColumnReaderTemplates;
	private final String[][] propertyColumnWriters;
	private final boolean[][] propertyColumnUpdateable;
	private final boolean[][] propertyColumnInsertable;
	private final boolean[] propertyUniqueness;
	private final boolean[] propertySelectable;

	private final List<Integer> lobProperties = new ArrayList<>();

	//information about lazy properties of this class
	private final String[] lazyPropertyNames;
	private final int[] lazyPropertyNumbers;
	private final Type[] lazyPropertyTypes;
	private final String[][] lazyPropertyColumnAliases;

	//information about all properties in class hierarchy
	private final String[] subclassPropertyNameClosure;
	private final String[] subclassPropertySubclassNameClosure;
	private final Type[] subclassPropertyTypeClosure;
	private final String[][] subclassPropertyFormulaTemplateClosure;
	private final String[][] subclassPropertyColumnNameClosure;
	private final String[][] subclassPropertyColumnReaderClosure;
	private final String[][] subclassPropertyColumnReaderTemplateClosure;
	private final FetchMode[] subclassPropertyFetchModeClosure;
	private final boolean[] subclassPropertyNullabilityClosure;
	private final boolean[] propertyDefinedOnSubclass;
	private final int[][] subclassPropertyColumnNumberClosure;
	private final int[][] subclassPropertyFormulaNumberClosure;
	private final CascadeStyle[] subclassPropertyCascadeStyleClosure;

	//information about all columns/formulas in class hierarchy
	private final String[] subclassColumnClosure;
	private final boolean[] subclassColumnLazyClosure;
	private final String[] subclassColumnAliasClosure;
	private final boolean[] subclassColumnSelectableClosure;
	private final String[] subclassColumnReaderTemplateClosure;
	private final String[] subclassFormulaClosure;
	private final String[] subclassFormulaTemplateClosure;
	private final String[] subclassFormulaAliasClosure;
	private final boolean[] subclassFormulaLazyClosure;

	// dynamic filters attached to the class-level
	private final FilterHelper filterHelper;

	private final Set<String> affectingFetchProfileNames = new HashSet<>();

	private final SingleIdEntityLoader singleIdEntityLoader;
	private final MultiIdEntityLoader multiIdEntityLoader;
	private final NaturalIdLoader naturalIdLoader;

	private final Map uniqueKeyLoaders = new HashMap();
	private final Map lockers = new HashMap();
	private UniqueEntityLoader noneLockLoader;
	private UniqueEntityLoader readLockLoader;
	private final Map<Object, UniqueEntityLoader> loaders = new ConcurrentHashMap<>();

	// SQL strings
	private String sqlVersionSelectString;
	private String sqlSnapshotSelectString;
	private Map<String,String> sqlLazySelectStringsByFetchGroup;

	private String sqlIdentityInsertString;
	private String sqlUpdateByRowIdString;
	private String sqlLazyUpdateByRowIdString;

	private String[] sqlDeleteStrings;
	private String[] sqlInsertStrings;
	private String[] sqlUpdateStrings;
	private String[] sqlLazyUpdateStrings;

	private String sqlInsertGeneratedValuesSelectString;
	private String sqlUpdateGeneratedValuesSelectString;

	//Custom SQL (would be better if these were private)
	protected boolean[] insertCallable;
	protected boolean[] updateCallable;
	protected boolean[] deleteCallable;
	protected String[] customSQLInsert;
	protected String[] customSQLUpdate;
	protected String[] customSQLDelete;
	protected ExecuteUpdateResultCheckStyle[] insertResultCheckStyles;
	protected ExecuteUpdateResultCheckStyle[] updateResultCheckStyles;
	protected ExecuteUpdateResultCheckStyle[] deleteResultCheckStyles;

	private InsertGeneratedIdentifierDelegate identityDelegate;

	private boolean[] tableHasColumns;

	private final Map subclassPropertyAliases = new HashMap();
	private final Map subclassPropertyColumnNames = new HashMap();

	/**
	 * Warning:
	 * When there are duplicated property names in the subclasses
	 * then propertyMapping will only contain one of those properties.
	 * To ensure correct results, propertyMapping should only be used
	 * for the concrete EntityPersister (since the concrete EntityPersister
	 * cannot have duplicated property names).
	 */
	protected final BasicEntityPropertyMapping propertyMapping;

	private final boolean useReferenceCacheEntries;

	protected void addDiscriminatorToInsert(Insert insert) {
	}

	protected void addDiscriminatorToSelect(SelectFragment select, String name, String suffix) {
	}

	protected abstract int[] getSubclassColumnTableNumberClosure();

	protected abstract int[] getSubclassFormulaTableNumberClosure();

	public abstract String getSubclassTableName(int j);

	protected abstract String[] getSubclassTableKeyColumns(int j);

	protected abstract boolean isClassOrSuperclassTable(int j);

	public abstract int getSubclassTableSpan();

	protected abstract int getTableSpan();

	protected abstract boolean isTableCascadeDeleteEnabled(int j);

	protected abstract String getTableName(int j);

	protected abstract String[] getKeyColumns(int j);

	protected abstract boolean isPropertyOfTable(int property, int j);

	protected abstract int[] getPropertyTableNumbersInSelect();

	protected abstract int[] getPropertyTableNumbers();

	protected abstract int getSubclassPropertyTableNumber(int i);

	protected abstract String filterFragment(String alias) throws MappingException;

	protected abstract String filterFragment(String alias, Set<String> treatAsDeclarations);

	private static final String DISCRIMINATOR_ALIAS = "clazz_";

	public String getDiscriminatorColumnName() {
		return DISCRIMINATOR_ALIAS;
	}

	public String getDiscriminatorColumnReaders() {
		return DISCRIMINATOR_ALIAS;
	}

	public String getDiscriminatorColumnReaderTemplate() {
		if ( getEntityMetamodel().getSubclassEntityNames().size() == 1 ) {
			return getDiscriminatorSQLValue();
		}
		else {
			return Template.TEMPLATE + "." + DISCRIMINATOR_ALIAS;
		}
	}

	protected String getDiscriminatorAlias() {
		return DISCRIMINATOR_ALIAS;
	}

	protected String getDiscriminatorFormulaTemplate() {
		return null;
	}

	protected boolean isInverseTable(int j) {
		return false;
	}

	protected boolean isNullableTable(int j) {
		return false;
	}

	protected boolean isNullableSubclassTable(int j) {
		return false;
	}

	protected boolean isInverseSubclassTable(int j) {
		return false;
	}

	public boolean isSubclassEntityName(String entityName) {
		return entityMetamodel.getSubclassEntityNames().contains( entityName );
	}

	private boolean[] getTableHasColumns() {
		return tableHasColumns;
	}

	public String[] getRootTableKeyColumnNames() {
		return rootTableKeyColumnNames;
	}

	protected String[] getSQLUpdateByRowIdStrings() {
		if ( sqlUpdateByRowIdString == null ) {
			throw new AssertionFailure( "no update by row id" );
		}
		String[] result = new String[getTableSpan() + 1];
		result[0] = sqlUpdateByRowIdString;
		System.arraycopy( sqlUpdateStrings, 0, result, 1, getTableSpan() );
		return result;
	}

	protected String[] getSQLLazyUpdateByRowIdStrings() {
		if ( sqlLazyUpdateByRowIdString == null ) {
			throw new AssertionFailure( "no update by row id" );
		}
		String[] result = new String[getTableSpan()];
		result[0] = sqlLazyUpdateByRowIdString;
		System.arraycopy( sqlLazyUpdateStrings, 1, result, 1, getTableSpan() - 1 );
		return result;
	}

	protected String getSQLSnapshotSelectString() {
		return sqlSnapshotSelectString;
	}

	protected String getSQLLazySelectString(String fetchGroup) {
		return sqlLazySelectStringsByFetchGroup.get( fetchGroup );
	}

	protected String[] getSQLDeleteStrings() {
		return sqlDeleteStrings;
	}

	protected String[] getSQLInsertStrings() {
		return sqlInsertStrings;
	}

	protected String[] getSQLUpdateStrings() {
		return sqlUpdateStrings;
	}

	protected String[] getSQLLazyUpdateStrings() {
		return sqlLazyUpdateStrings;
	}

	/**
	 * The query that inserts a row, letting the database generate an id
	 *
	 * @return The IDENTITY-based insertion query.
	 */
	protected String getSQLIdentityInsertString() {
		return sqlIdentityInsertString;
	}

	protected String getVersionSelectString() {
		return sqlVersionSelectString;
	}

	protected boolean isInsertCallable(int j) {
		return insertCallable[j];
	}

	protected boolean isUpdateCallable(int j) {
		return updateCallable[j];
	}

	protected boolean isDeleteCallable(int j) {
		return deleteCallable[j];
	}

	protected boolean isSubclassPropertyDeferred(String propertyName, String entityName) {
		return false;
	}

	protected boolean isSubclassTableSequentialSelect(int j) {
		return false;
	}

	public boolean hasSequentialSelect() {
		return false;
	}

	/**
	 * Decide which tables need to be updated.
	 * <p/>
	 * The return here is an array of boolean values with each index corresponding
	 * to a given table in the scope of this persister.
	 *
	 * @param dirtyProperties The indices of all the entity properties considered dirty.
	 * @param hasDirtyCollection Whether any collections owned by the entity which were considered dirty.
	 *
	 * @return Array of booleans indicating which table require updating.
	 */
	protected boolean[] getTableUpdateNeeded(final int[] dirtyProperties, boolean hasDirtyCollection) {

		if ( dirtyProperties == null ) {
			return getTableHasColumns(); // for objects that came in via update()
		}
		else {
			boolean[] updateability = getPropertyUpdateability();
			int[] propertyTableNumbers = getPropertyTableNumbers();
			boolean[] tableUpdateNeeded = new boolean[getTableSpan()];
			for ( int property : dirtyProperties ) {
				int table = propertyTableNumbers[property];
				tableUpdateNeeded[table] = tableUpdateNeeded[table] ||
						( getPropertyColumnSpan( property ) > 0 && updateability[property] );

				if ( getPropertyColumnSpan( property ) > 0 && !updateability[property] ) {
					LOG.ignoreImmutablePropertyModification( getPropertyNames()[property], getEntityName() );
				}
			}
			if ( isVersioned() ) {
				tableUpdateNeeded[0] = tableUpdateNeeded[0] ||
						Versioning.isVersionIncrementRequired(
								dirtyProperties,
								hasDirtyCollection,
								getPropertyVersionability()
						);
			}
			return tableUpdateNeeded;
		}
	}

	public boolean hasRowId() {
		return rowIdName != null;
	}

	protected boolean[][] getPropertyColumnUpdateable() {
		return propertyColumnUpdateable;
	}

	protected boolean[][] getPropertyColumnInsertable() {
		return propertyColumnInsertable;
	}

	protected boolean[] getPropertySelectable() {
		return propertySelectable;
	}

	public String[] getTableNames() {
		String[] tableNames = new String[getTableSpan()];
		for ( int i = 0; i < tableNames.length; i++ ) {
			tableNames[i] = getTableName( i );
		}
		return tableNames;
	}

	@SuppressWarnings("UnnecessaryBoxing")
	public AbstractEntityPersister(
			final PersistentClass bootDescriptor,
			final EntityDataAccess cacheAccessStrategy,
			final NaturalIdDataAccess naturalIdRegionAccessStrategy,
			final PersisterCreationContext creationContext) throws HibernateException {

		this.factory = creationContext.getSessionFactory();
		this.sqlAliasStem = SqlAliasStemHelper.INSTANCE.generateStemFromEntityName( bootDescriptor.getEntityName() );

		this.navigableRole = new NavigableRole( bootDescriptor.getEntityName() );

		if ( creationContext.getSessionFactory().getSessionFactoryOptions().isSecondLevelCacheEnabled() ) {
			this.canWriteToCache = determineCanWriteToCache( bootDescriptor, cacheAccessStrategy );
			this.canReadFromCache = determineCanReadFromCache( bootDescriptor, cacheAccessStrategy );
			this.cacheAccessStrategy = cacheAccessStrategy;
			this.isLazyPropertiesCacheable = bootDescriptor.getRootClass().isLazyPropertiesCacheable();
			this.naturalIdRegionAccessStrategy = naturalIdRegionAccessStrategy;
		}
		else {
			this.canWriteToCache = false;
			this.canReadFromCache = false;
			this.cacheAccessStrategy = null;
			this.isLazyPropertiesCacheable = true;
			this.naturalIdRegionAccessStrategy = null;
		}

		this.entityMetamodel = new EntityMetamodel( bootDescriptor, this, factory );

		if ( entityMetamodel.isMutable() ) {
			this.entityEntryFactory = MutableEntityEntryFactory.INSTANCE;
		}
		else {
			this.entityEntryFactory = ImmutableEntityEntryFactory.INSTANCE;
		}
		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

		this.representationStrategy = creationContext.getBootstrapContext().getRepresentationStrategySelector()
				.resolveStrategy( bootDescriptor, this, (RuntimeModelCreationContext) creationContext );

		this.javaTypeDescriptor = representationStrategy.getLoadJavaTypeDescriptor();
		assert javaTypeDescriptor != null;


		final JdbcServices jdbcServices = factory.getServiceRegistry().getService( JdbcServices.class );
		final Dialect dialect = jdbcServices.getJdbcEnvironment().getDialect();

		int batch = bootDescriptor.getBatchSize();
		if ( batch == -1 ) {
			batch = factory.getSessionFactoryOptions().getDefaultBatchFetchSize();
		}
		batchSize = batch;
		hasSubselectLoadableCollections = bootDescriptor.hasSubselectLoadableCollections();

		propertyMapping = new BasicEntityPropertyMapping( this );

		// IDENTIFIER

		identifierColumnSpan = bootDescriptor.getIdentifier().getColumnSpan();
		rootTableKeyColumnNames = new String[identifierColumnSpan];
		rootTableKeyColumnReaders = new String[identifierColumnSpan];
		rootTableKeyColumnReaderTemplates = new String[identifierColumnSpan];
		identifierAliases = new String[identifierColumnSpan];

		rowIdName = bootDescriptor.getRootTable().getRowId();

		if ( bootDescriptor.getLoaderName() != null ) {
			singleIdEntityLoader = new SingleIdEntityLoaderProvidedQueryImpl( this, bootDescriptor.getLoaderName() );
		}
		// todo (6.0) : account for batch-size and batch-load strategies
		else {
			singleIdEntityLoader = new SingleIdEntityLoaderStandardImpl( this );
		}

		multiIdEntityLoader = new MultiIdEntityLoaderStandardImpl( this );

		naturalIdLoader = bootDescriptor.hasNaturalId()
				? new NaturalIdLoaderStandardImpl( this )
				: null;

		Iterator iter = bootDescriptor.getIdentifier().getColumnIterator();
		int i = 0;
		while ( iter.hasNext() ) {
			Column col = (Column) iter.next();
			rootTableKeyColumnNames[i] = col.getQuotedName( dialect );
			rootTableKeyColumnReaders[i] = col.getReadExpr( dialect );
			rootTableKeyColumnReaderTemplates[i] = col.getTemplate(
					dialect,
					factory.getSqlFunctionRegistry()
			);
			identifierAliases[i] = col.getAlias( dialect, bootDescriptor.getRootTable() );
			i++;
		}

		// VERSION

		if ( bootDescriptor.isVersioned() ) {
			versionColumnName = ( (Column) bootDescriptor.getVersion().getColumnIterator().next() ).getQuotedName( dialect );
		}
		else {
			versionColumnName = null;
		}

		//WHERE STRING

		sqlWhereString = StringHelper.isNotEmpty( bootDescriptor.getWhere() ) ?
				"( " + bootDescriptor.getWhere() + ") " :
				null;
		sqlWhereStringTemplate = sqlWhereString == null ?
				null :
				Template.renderWhereStringTemplate(
						sqlWhereString,
						dialect,
						factory.getSqlFunctionRegistry()
				);

		// PROPERTIES

		final boolean lazyAvailable = isInstrumented();

		int hydrateSpan = entityMetamodel.getPropertySpan();
		propertyColumnSpans = new int[hydrateSpan];
		propertySubclassNames = new String[hydrateSpan];
		propertyColumnAliases = new String[hydrateSpan][];
		propertyColumnNames = new String[hydrateSpan][];
		propertyColumnFormulaTemplates = new String[hydrateSpan][];
		propertyColumnReaderTemplates = new String[hydrateSpan][];
		propertyColumnWriters = new String[hydrateSpan][];
		propertyUniqueness = new boolean[hydrateSpan];
		propertySelectable = new boolean[hydrateSpan];
		propertyColumnUpdateable = new boolean[hydrateSpan][];
		propertyColumnInsertable = new boolean[hydrateSpan][];
		HashSet thisClassProperties = new HashSet();

		ArrayList lazyNames = new ArrayList();
		ArrayList lazyNumbers = new ArrayList();
		ArrayList lazyTypes = new ArrayList();
		ArrayList lazyColAliases = new ArrayList();

		iter = bootDescriptor.getPropertyClosureIterator();
		i = 0;
		boolean foundFormula = false;
		while ( iter.hasNext() ) {
			Property prop = (Property) iter.next();
			thisClassProperties.add( prop );

			int span = prop.getColumnSpan();
			propertyColumnSpans[i] = span;
			propertySubclassNames[i] = prop.getPersistentClass().getEntityName();
			String[] colNames = new String[span];
			String[] colAliases = new String[span];
			String[] colReaderTemplates = new String[span];
			String[] colWriters = new String[span];
			String[] formulaTemplates = new String[span];
			Iterator colIter = prop.getColumnIterator();
			int k = 0;
			while ( colIter.hasNext() ) {
				Selectable thing = (Selectable) colIter.next();
				colAliases[k] = thing.getAlias( dialect, prop.getValue().getTable() );
				if ( thing.isFormula() ) {
					foundFormula = true;
					( (Formula) thing ).setFormula( substituteBrackets( ( (Formula) thing ).getFormula() ) );
					formulaTemplates[k] = thing.getTemplate( dialect, factory.getSqlFunctionRegistry() );
				}
				else {
					Column col = (Column) thing;
					colNames[k] = col.getQuotedName( dialect );
					colReaderTemplates[k] = col.getTemplate( dialect, factory.getSqlFunctionRegistry() );
					colWriters[k] = col.getWriteExpr();
				}
				k++;
			}
			propertyColumnNames[i] = colNames;
			propertyColumnFormulaTemplates[i] = formulaTemplates;
			propertyColumnReaderTemplates[i] = colReaderTemplates;
			propertyColumnWriters[i] = colWriters;
			propertyColumnAliases[i] = colAliases;

			final boolean lazy = ! EnhancementHelper.includeInBaseFetchGroup(
					prop,
					entityMetamodel.isInstrumented(),
					creationContext.getSessionFactory().getSessionFactoryOptions().isEnhancementAsProxyEnabled()
			);

			if ( lazy ) {
				lazyNames.add( prop.getName() );
				lazyNumbers.add( i );
				lazyTypes.add( prop.getValue().getType() );
				lazyColAliases.add( colAliases );
			}

			propertyColumnUpdateable[i] = prop.getValue().getColumnUpdateability();
			propertyColumnInsertable[i] = prop.getValue().getColumnInsertability();

			propertySelectable[i] = prop.isSelectable();

			propertyUniqueness[i] = prop.getValue().isAlternateUniqueKey();

			if ( prop.isLob() && dialect.forceLobAsLastValue() ) {
				lobProperties.add( i );
			}

			i++;

		}
		hasFormulaProperties = foundFormula;
		lazyPropertyColumnAliases = ArrayHelper.to2DStringArray( lazyColAliases );
		lazyPropertyNames = ArrayHelper.toStringArray( lazyNames );
		lazyPropertyNumbers = ArrayHelper.toIntArray( lazyNumbers );
		lazyPropertyTypes = ArrayHelper.toTypeArray( lazyTypes );

		// SUBCLASS PROPERTY CLOSURE

		ArrayList columns = new ArrayList();
		ArrayList columnsLazy = new ArrayList();
		ArrayList columnReaderTemplates = new ArrayList();
		ArrayList aliases = new ArrayList();
		ArrayList formulas = new ArrayList();
		ArrayList formulaAliases = new ArrayList();
		ArrayList formulaTemplates = new ArrayList();
		ArrayList formulasLazy = new ArrayList();
		ArrayList types = new ArrayList();
		ArrayList names = new ArrayList();
		ArrayList classes = new ArrayList();
		ArrayList templates = new ArrayList();
		ArrayList propColumns = new ArrayList();
		ArrayList propColumnReaders = new ArrayList();
		ArrayList propColumnReaderTemplates = new ArrayList();
		ArrayList joinedFetchesList = new ArrayList();
		ArrayList cascades = new ArrayList();
		ArrayList definedBySubclass = new ArrayList();
		ArrayList propColumnNumbers = new ArrayList();
		ArrayList propFormulaNumbers = new ArrayList();
		ArrayList columnSelectables = new ArrayList();
		ArrayList propNullables = new ArrayList();

		iter = bootDescriptor.getSubclassPropertyClosureIterator();
		while ( iter.hasNext() ) {
			Property prop = (Property) iter.next();
			names.add( prop.getName() );
			classes.add( prop.getPersistentClass().getEntityName() );
			boolean isDefinedBySubclass = !thisClassProperties.contains( prop );
			definedBySubclass.add( Boolean.valueOf( isDefinedBySubclass ) );
			propNullables.add( Boolean.valueOf( prop.isOptional() || isDefinedBySubclass ) ); //TODO: is this completely correct?
			types.add( prop.getType() );

			Iterator colIter = prop.getColumnIterator();
			String[] cols = new String[prop.getColumnSpan()];
			String[] readers = new String[prop.getColumnSpan()];
			String[] readerTemplates = new String[prop.getColumnSpan()];
			String[] forms = new String[prop.getColumnSpan()];
			int[] colnos = new int[prop.getColumnSpan()];
			int[] formnos = new int[prop.getColumnSpan()];
			int l = 0;
			final boolean lazy = ! EnhancementHelper.includeInBaseFetchGroup(
					prop,
					entityMetamodel.isInstrumented(),
					creationContext.getSessionFactory().getSessionFactoryOptions().isEnhancementAsProxyEnabled()
			);
			while ( colIter.hasNext() ) {
				Selectable thing = (Selectable) colIter.next();
				if ( thing.isFormula() ) {
					String template = thing.getTemplate( dialect, factory.getSqlFunctionRegistry() );
					formnos[l] = formulaTemplates.size();
					colnos[l] = -1;
					formulaTemplates.add( template );
					forms[l] = template;
					formulas.add( thing.getText( dialect ) );
					formulaAliases.add( thing.getAlias( dialect ) );
					formulasLazy.add( lazy );
				}
				else {
					Column col = (Column) thing;
					String colName = col.getQuotedName( dialect );
					colnos[l] = columns.size(); //before add :-)
					formnos[l] = -1;
					columns.add( colName );
					cols[l] = colName;
					aliases.add( thing.getAlias( dialect, prop.getValue().getTable() ) );
					columnsLazy.add( lazy );
					columnSelectables.add( Boolean.valueOf( prop.isSelectable() ) );

					readers[l] = col.getReadExpr( dialect );
					String readerTemplate = col.getTemplate( dialect, factory.getSqlFunctionRegistry() );
					readerTemplates[l] = readerTemplate;
					columnReaderTemplates.add( readerTemplate );
				}
				l++;
			}
			propColumns.add( cols );
			propColumnReaders.add( readers );
			propColumnReaderTemplates.add( readerTemplates );
			templates.add( forms );
			propColumnNumbers.add( colnos );
			propFormulaNumbers.add( formnos );

			joinedFetchesList.add( prop.getValue().getFetchMode() );
			cascades.add( prop.getCascadeStyle() );
		}
		subclassColumnClosure = ArrayHelper.toStringArray( columns );
		subclassColumnAliasClosure = ArrayHelper.toStringArray( aliases );
		subclassColumnLazyClosure = ArrayHelper.toBooleanArray( columnsLazy );
		subclassColumnSelectableClosure = ArrayHelper.toBooleanArray( columnSelectables );
		subclassColumnReaderTemplateClosure = ArrayHelper.toStringArray( columnReaderTemplates );

		subclassFormulaClosure = ArrayHelper.toStringArray( formulas );
		subclassFormulaTemplateClosure = ArrayHelper.toStringArray( formulaTemplates );
		subclassFormulaAliasClosure = ArrayHelper.toStringArray( formulaAliases );
		subclassFormulaLazyClosure = ArrayHelper.toBooleanArray( formulasLazy );

		subclassPropertyNameClosure = ArrayHelper.toStringArray( names );
		subclassPropertySubclassNameClosure = ArrayHelper.toStringArray( classes );
		subclassPropertyTypeClosure = ArrayHelper.toTypeArray( types );
		subclassPropertyNullabilityClosure = ArrayHelper.toBooleanArray( propNullables );
		subclassPropertyFormulaTemplateClosure = ArrayHelper.to2DStringArray( templates );
		subclassPropertyColumnNameClosure = ArrayHelper.to2DStringArray( propColumns );
		subclassPropertyColumnReaderClosure = ArrayHelper.to2DStringArray( propColumnReaders );
		subclassPropertyColumnReaderTemplateClosure = ArrayHelper.to2DStringArray( propColumnReaderTemplates );
		subclassPropertyColumnNumberClosure = ArrayHelper.to2DIntArray( propColumnNumbers );
		subclassPropertyFormulaNumberClosure = ArrayHelper.to2DIntArray( propFormulaNumbers );

		subclassPropertyCascadeStyleClosure = new CascadeStyle[cascades.size()];
		iter = cascades.iterator();
		int j = 0;
		while ( iter.hasNext() ) {
			subclassPropertyCascadeStyleClosure[j++] = (CascadeStyle) iter.next();
		}
		subclassPropertyFetchModeClosure = new FetchMode[joinedFetchesList.size()];
		iter = joinedFetchesList.iterator();
		j = 0;
		while ( iter.hasNext() ) {
			subclassPropertyFetchModeClosure[j++] = (FetchMode) iter.next();
		}

		propertyDefinedOnSubclass = new boolean[definedBySubclass.size()];
		iter = definedBySubclass.iterator();
		j = 0;
		while ( iter.hasNext() ) {
			propertyDefinedOnSubclass[j++] = (Boolean) iter.next();
		}

		// Handle any filters applied to the class level
		filterHelper = new FilterHelper( bootDescriptor.getFilters(), factory );

		// Check if we can use Reference Cached entities in 2lc
		// todo : should really validate that the cache access type is read-only
		boolean refCacheEntries = true;
		if ( !factory.getSessionFactoryOptions().isDirectReferenceCacheEntriesEnabled() ) {
			refCacheEntries = false;
		}

		// for now, limit this to just entities that:
		// 		1) are immutable
		if ( entityMetamodel.isMutable() ) {
			refCacheEntries = false;
		}

		//		2)  have no associations.  Eventually we want to be a little more lenient with associations.
		for ( Type type : getSubclassPropertyTypeClosure() ) {
			if ( type.isAssociationType() ) {
				refCacheEntries = false;
			}
		}

		useReferenceCacheEntries = refCacheEntries;

		this.cacheEntryHelper = buildCacheEntryHelper();

		if ( creationContext.getSessionFactory().getSessionFactoryOptions().isSecondLevelCacheEnabled() ) {
			this.invalidateCache = canWriteToCache && determineWhetherToInvalidateCache( bootDescriptor, creationContext );
		}
		else {
			this.invalidateCache = false;
		}

	}

	@SuppressWarnings("RedundantIfStatement")
	private boolean determineWhetherToInvalidateCache(
			PersistentClass persistentClass,
			PersisterCreationContext creationContext) {
		if ( hasFormulaProperties() ) {
			return true;
		}

		if ( isVersioned() ) {
			return false;
		}

		if ( entityMetamodel.isDynamicUpdate() ) {
			return false;
		}

		// We need to check whether the user may have circumvented this logic (JPA TCK)
		final boolean complianceEnabled = creationContext.getSessionFactory()
				.getSessionFactoryOptions()
				.getJpaCompliance()
				.isJpaCacheComplianceEnabled();
		if ( complianceEnabled ) {
			// The JPA TCK (inadvertently, but still...) requires that we cache
			// entities with secondary tables even though Hibernate historically
			// invalidated them
			return false;
		}

		if ( persistentClass.getJoinClosureSpan() >= 1 ) {
			// todo : this should really consider optionality of the secondary tables in count
			//		non-optional tables do not cause this bypass
			return true;
		}

		return false;
	}

	private boolean determineCanWriteToCache(PersistentClass persistentClass, EntityDataAccess cacheAccessStrategy) {
		if ( cacheAccessStrategy == null ) {
			return false;
		}

		return persistentClass.isCached();
	}

	@SuppressWarnings("unchecked")
	private boolean determineCanReadFromCache(PersistentClass persistentClass, EntityDataAccess cacheAccessStrategy) {
		if ( cacheAccessStrategy == null ) {
			return false;
		}

		if ( persistentClass.isCached() ) {
			return true;
		}

		final Iterator<Subclass> subclassIterator = persistentClass.getSubclassIterator();
		while ( subclassIterator.hasNext() ) {
			final Subclass subclass = subclassIterator.next();
			if ( subclass.isCached() ) {
				return true;
			}
		}
		return false;
	}

	protected CacheEntryHelper buildCacheEntryHelper() {
		if ( cacheAccessStrategy == null ) {
			// the entity defined no caching...
			return NoopCacheEntryHelper.INSTANCE;
		}

		if ( canUseReferenceCacheEntries() ) {
			entityMetamodel.setLazy( false );
			// todo : do we also need to unset proxy factory?
			return new ReferenceCacheEntryHelper( this );
		}

		return factory.getSessionFactoryOptions().isStructuredCacheEntriesEnabled()
				? new StructuredCacheEntryHelper( this )
				: new StandardCacheEntryHelper( this );
	}

	public boolean canUseReferenceCacheEntries() {
		return useReferenceCacheEntries;
	}

	protected static String getTemplateFromString(String string, SessionFactoryImplementor factory) {
		return string == null ?
				null :
				Template.renderWhereStringTemplate( string, factory.getDialect(), factory.getSqlFunctionRegistry() );
	}

	protected Map<String,String> generateLazySelectStringsByFetchGroup() {
		final BytecodeEnhancementMetadata enhancementMetadata = entityMetamodel.getBytecodeEnhancementMetadata();
		if ( !enhancementMetadata.isEnhancedForLazyLoading()
				|| !enhancementMetadata.getLazyAttributesMetadata().hasLazyAttributes() ) {
			return Collections.emptyMap();
		}

		Map<String,String> result = new HashMap<>();

		final LazyAttributesMetadata lazyAttributesMetadata = enhancementMetadata.getLazyAttributesMetadata();
		for ( String groupName : lazyAttributesMetadata.getFetchGroupNames() ) {
			HashSet tableNumbers = new HashSet();
			ArrayList columnNumbers = new ArrayList();
			ArrayList formulaNumbers = new ArrayList();

			for ( LazyAttributeDescriptor lazyAttributeDescriptor :
					lazyAttributesMetadata.getFetchGroupAttributeDescriptors( groupName ) ) {
				// all this only really needs to consider properties
				// of this class, not its subclasses, but since we
				// are reusing code used for sequential selects, we
				// use the subclass closure
				int propertyNumber = getSubclassPropertyIndex( lazyAttributeDescriptor.getName() );

				int tableNumber = getSubclassPropertyTableNumber( propertyNumber );
				tableNumbers.add( tableNumber );

				int[] colNumbers = subclassPropertyColumnNumberClosure[propertyNumber];
				for ( int colNumber : colNumbers ) {
					if ( colNumber != -1 ) {
						columnNumbers.add( colNumber );
					}
				}
				int[] formNumbers = subclassPropertyFormulaNumberClosure[propertyNumber];
				for ( int formNumber : formNumbers ) {
					if ( formNumber != -1 ) {
						formulaNumbers.add( formNumber );
					}
				}
			}

			if ( columnNumbers.size() == 0 && formulaNumbers.size() == 0 ) {
				// only one-to-one is lazy fetched
				continue;
			}

			result.put(
					groupName,
					renderSelect(
							ArrayHelper.toIntArray( tableNumbers ),
							ArrayHelper.toIntArray( columnNumbers ),
							ArrayHelper.toIntArray( formulaNumbers )
					)
			);
		}

		return result;
	}

	@Override
	public String getSqlAliasStem() {
		return sqlAliasStem;
	}

	@Override
	public <T> DomainResult<T> createDomainResult(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			String resultVariable,
			DomainResultCreationState creationState) {
		//noinspection unchecked
		return new EntityResultImpl( navigablePath, this, resultVariable, creationState );
	}

	@Override
	public void applySqlSelections(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			DomainResultCreationState creationState) {
		throw new NotYetImplementedFor6Exception( getClass() );
	}

	@Override
	public NaturalIdMapping getNaturalIdMapping() {
		return naturalIdMapping;
	}

	@Override
	public EntityMappingType getEntityMappingType() {
		return this;
	}

	@Override
	public TableGroup createRootTableGroup(
			NavigablePath navigablePath,
			String explicitSourceAlias,
			org.hibernate.sql.ast.JoinType tableReferenceJoinType,
			LockMode lockMode,
			SqlAliasBaseGenerator aliasBaseGenerator,
			SqlExpressionResolver sqlExpressionResolver,
			SqlAstCreationContext creationContext) {
		final SqlAliasBase sqlAliasBase = aliasBaseGenerator.createSqlAliasBase( getSqlAliasStem() );

		final TableReference primaryTableReference = resolvePrimaryTableReference( sqlAliasBase, sqlExpressionResolver );

		final List<TableReferenceJoin> joins = new ArrayList<>(  );
		resolveTableReferenceJoins( primaryTableReference, sqlAliasBase, tableReferenceJoinType, joins::add, sqlExpressionResolver );

		return new StandardTableGroup(
				navigablePath,
				this,
				lockMode,
				primaryTableReference,
				joins,
				sqlAliasBase,
				getFactory()
		);
	}

	protected TableReference resolvePrimaryTableReference(
			SqlAliasBase sqlAliasBase,
			SqlExpressionResolver sqlExpressionResolver) {
		// todo (6.0) : temporary
		return new TableReference(
				getRootTableName(),
				sqlAliasBase.generateNewAlias(),
				false,
				getFactory()
		);
	}

	private void resolveTableReferenceJoins(
			TableReference rootTableReference,
			SqlAliasBase sqlAliasBase,
			org.hibernate.sql.ast.JoinType joinType,
			Consumer<TableReferenceJoin> collector,
			SqlExpressionResolver sqlExpressionResolver) {
		for ( int i = 1; i < getSubclassTableSpan(); i++ ) {
			collector.accept(
					createTableReferenceJoin( i, rootTableReference, joinType, sqlAliasBase, sqlExpressionResolver )
			);
		}
	}

	protected TableReferenceJoin createTableReferenceJoin(
			int subClassTablePosition,
			TableReference rootTableReference,
			org.hibernate.sql.ast.JoinType joinType,
			SqlAliasBase sqlAliasBase,
			SqlExpressionResolver sqlExpressionResolver) {
		final boolean nullable = isNullableSubclassTable( subClassTablePosition );

		final TableReference joinedTableReference = new TableReference(
				getSubclassTableName( subClassTablePosition ),
				sqlAliasBase.generateNewAlias(),
				nullable,
				getFactory()
		);

		return new TableReferenceJoin(
				nullable ? org.hibernate.sql.ast.JoinType.LEFT : joinType,
				joinedTableReference,
				generateJoinPredicate( rootTableReference, joinedTableReference, subClassTablePosition, sqlExpressionResolver )
		);
	}

	private Predicate generateJoinPredicate(
			TableReference rootTableReference,
			TableReference joinedTableReference,
			int subClassTablePosition,
			SqlExpressionResolver sqlExpressionResolver) {
		final EntityIdentifierMapping identifierMapping = getIdentifierMapping();

		final Junction conjunction = new Junction( Junction.Nature.CONJUNCTION );

		final String[] rootPkColumnNames = getKeyColumnNames();
		final String[] fkColumnNames = getSubclassTableKeyColumns( subClassTablePosition );

		assert rootPkColumnNames.length == fkColumnNames.length;
		assert rootPkColumnNames.length == identifierMapping.getJdbcTypeCount( factory.getTypeConfiguration() );

		identifierMapping.visitJdbcTypes(
				new Consumer<JdbcMapping>() {
					private int columnIndex;

					@Override
					public void accept(JdbcMapping jdbcMapping) {
						final String rootPkColumnName = rootPkColumnNames[ columnIndex ];
						final Expression pkColumnExpression = sqlExpressionResolver.resolveSqlExpression(
								SqlExpressionResolver.createColumnReferenceKey(
										rootTableReference.getTableExpression(),
										rootPkColumnName
								),
								sqlAstProcessingState -> new ColumnReference(
										rootPkColumnName,
										rootTableReference.getIdentificationVariable(),
										jdbcMapping,
										getFactory()
								)
						);

						final String fkColumnName = fkColumnNames[ columnIndex ];
						final Expression fkColumnExpression = sqlExpressionResolver.resolveSqlExpression(
								SqlExpressionResolver.createColumnReferenceKey(
										joinedTableReference.getTableExpression(),
										fkColumnName
								),
								sqlAstProcessingState -> new ColumnReference(
										fkColumnName,
										joinedTableReference.getIdentificationVariable(),
										jdbcMapping,
										getFactory()
								)
						);

						conjunction.add( new ComparisonPredicate( pkColumnExpression, ComparisonOperator.EQUAL, fkColumnExpression ) );

						columnIndex++;
					}
				},
				Clause.IRRELEVANT,
				getFactory().getTypeConfiguration()
		);

		return conjunction;
	}

	public Object initializeLazyProperty(String fieldName, Object entity, SharedSessionContractImplementor session) {
		final PersistenceContext persistenceContext = session.getPersistenceContextInternal();
		final EntityEntry entry = persistenceContext.getEntry( entity );
		final PersistentAttributeInterceptor interceptor = ( (PersistentAttributeInterceptable) entity ).$$_hibernate_getInterceptor();
		assert interceptor != null : "Expecting bytecode interceptor to be non-null";

		if ( hasCollections() ) {
			final Type type = getPropertyType( fieldName );
			if ( type.isCollectionType() ) {
				// we have a condition where a collection attribute is being access via enhancement:
				// 		we can circumvent all the rest and just return the PersistentCollection
				final CollectionType collectionType = (CollectionType) type;
				final CollectionPersister persister = factory.getMetamodel().collectionPersister( collectionType.getRole() );

				// Get/create the collection, and make sure it is initialized!  This initialized part is
				// different from proxy-based scenarios where we have to create the PersistentCollection
				// reference "ahead of time" to add as a reference to the proxy.  For bytecode solutions
				// we are not creating the PersistentCollection ahead of time, but instead we are creating
				// it on first request through the enhanced entity.

				// see if there is already a collection instance associated with the session
				// 		NOTE : can this ever happen?
				final Serializable key = getCollectionKey( persister, entity, entry, session );
				PersistentCollection collection = persistenceContext.getCollection( new CollectionKey( persister, key ) );
				if ( collection == null ) {
					collection = collectionType.instantiate( session, persister, key );
					collection.setOwner( entity );
					persistenceContext.addUninitializedCollection( persister, collection, key );
				}

//				// HHH-11161 Initialize, if the collection is not extra lazy
//				if ( !persister.isExtraLazy() ) {
//					session.initializeCollection( collection, false );
//				}
				interceptor.attributeInitialized( fieldName );

				if ( collectionType.isArrayType() ) {
					persistenceContext.addCollectionHolder( collection );
				}

				// update the "state" of the entity's EntityEntry to over-write UNFETCHED_PROPERTY reference
				// for the collection to the just loaded collection
				final EntityEntry ownerEntry = persistenceContext.getEntry( entity );
				if ( ownerEntry == null ) {
					// not good
					throw new AssertionFailure(
							"Could not locate EntityEntry for the collection owner in the PersistenceContext"
					);
				}
				ownerEntry.overwriteLoadedStateCollectionValue( fieldName, collection );

				// EARLY EXIT!!!
				return collection;
			}
		}

		final Serializable id = session.getContextEntityIdentifier( entity );
		if ( entry == null ) {
			throw new HibernateException( "entity is not associated with the session: " + id );
		}

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev(
					"Initializing lazy properties of: {0}, field access: {1}", MessageHelper.infoString(
							this,
							id,
							getFactory()
					), fieldName
			);
		}

		if ( session.getCacheMode().isGetEnabled() && canReadFromCache() && isLazyPropertiesCacheable() ) {
			final EntityDataAccess cacheAccess = getCacheAccessStrategy();
			final Object cacheKey = cacheAccess.generateCacheKey(id, this, session.getFactory(), session.getTenantIdentifier() );
			final Object ce = CacheHelper.fromSharedCache( session, cacheKey, cacheAccess );
			if ( ce != null ) {
				final CacheEntry cacheEntry = (CacheEntry) getCacheEntryStructure().destructure( ce, factory );
				final Object initializedValue = initializeLazyPropertiesFromCache( fieldName, entity, session, entry, cacheEntry );
				if (initializedValue != LazyPropertyInitializer.UNFETCHED_PROPERTY) {
					// The following should be redundant, since the setter should have set this already.
					// interceptor.attributeInitialized(fieldName);

					// NOTE EARLY EXIT!!!
					return initializedValue;
				}
			}
		}

		return initializeLazyPropertiesFromDatastore( fieldName, entity, session, id, entry );

	}

	protected Serializable getCollectionKey(
			CollectionPersister persister,
			Object owner,
			EntityEntry ownerEntry,
			SharedSessionContractImplementor session) {
		final CollectionType collectionType = persister.getCollectionType();

		if ( ownerEntry != null ) {
			// this call only works when the owner is associated with the Session, which is not always the case
			return collectionType.getKeyOfOwner( owner, session );
		}

		if ( collectionType.getLHSPropertyName() == null ) {
			// collection key is defined by the owning entity identifier
			return persister.getOwnerEntityPersister().getIdentifier( owner, session );
		}
		else {
			return (Serializable) persister.getOwnerEntityPersister().getPropertyValue( owner, collectionType.getLHSPropertyName() );
		}
	}

	private Object initializeLazyPropertiesFromDatastore(
			final String fieldName,
			final Object entity,
			final SharedSessionContractImplementor session,
			final Serializable id,
			final EntityEntry entry) {

		if ( !hasLazyProperties() ) {
			throw new AssertionFailure( "no lazy properties" );
		}

		final PersistentAttributeInterceptor interceptor = ( (PersistentAttributeInterceptable) entity ).$$_hibernate_getInterceptor();
		assert interceptor != null : "Expecting bytecode interceptor to be non-null";

		LOG.tracef( "Initializing lazy properties from datastore (triggered for `%s`)", fieldName );

		final String fetchGroup = getEntityMetamodel().getBytecodeEnhancementMetadata()
				.getLazyAttributesMetadata()
				.getFetchGroupName( fieldName );
		final List<LazyAttributeDescriptor> fetchGroupAttributeDescriptors = getEntityMetamodel().getBytecodeEnhancementMetadata()
				.getLazyAttributesMetadata()
				.getFetchGroupAttributeDescriptors( fetchGroup );

		final Set<String> initializedLazyAttributeNames = interceptor.getInitializedLazyAttributeNames();

		final String lazySelect = getSQLLazySelectString( fetchGroup );

		try {
			Object result = null;
			PreparedStatement ps = null;
			try {
				ResultSet rs = null;
				try {
					if ( lazySelect != null ) {
						// null sql means that the only lazy properties
						// are shared PK one-to-one associations which are
						// handled differently in the Type#nullSafeGet code...
						ps = session.getJdbcCoordinator()
								.getStatementPreparer()
								.prepareStatement( lazySelect );
						getIdentifierType().nullSafeSet( ps, id, 1, session );
						rs = session.getJdbcCoordinator().getResultSetReturn().extract( ps );
						rs.next();
					}
					for ( LazyAttributeDescriptor fetchGroupAttributeDescriptor : fetchGroupAttributeDescriptors ) {
						final boolean previousInitialized = initializedLazyAttributeNames.contains( fetchGroupAttributeDescriptor.getName() );

						if ( previousInitialized ) {
							// todo : one thing we should consider here is potentially un-marking an attribute as dirty based on the selected value
							// 		we know the current value - getPropertyValue( entity, fetchGroupAttributeDescriptor.getAttributeIndex() );
							// 		we know the selected value (see selectedValue below)
							//		we can use the attribute Type to tell us if they are the same
							//
							//		assuming entity is a SelfDirtinessTracker we can also know if the attribute is
							//			currently considered dirty, and if really not dirty we would do the un-marking
							//
							//		of course that would mean a new method on SelfDirtinessTracker to allow un-marking

							// its already been initialized (e.g. by a write) so we don't want to overwrite
							continue;
						}


						final Object selectedValue = fetchGroupAttributeDescriptor.getType().nullSafeGet(
								rs,
								lazyPropertyColumnAliases[fetchGroupAttributeDescriptor.getLazyIndex()],
								session,
								entity
						);

						final boolean set = initializeLazyProperty(
								fieldName,
								entity,
								session,
								entry,
								fetchGroupAttributeDescriptor.getLazyIndex(),
								selectedValue
						);
						if ( set ) {
							result = selectedValue;
							interceptor.attributeInitialized( fetchGroupAttributeDescriptor.getName() );
						}

					}
				}
				finally {
					if ( rs != null ) {
						session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( rs, ps );
					}
				}
			}
			finally {
				if ( ps != null ) {
					session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( ps );
					session.getJdbcCoordinator().afterStatementExecution();
				}
			}

			LOG.trace( "Done initializing lazy properties" );

			return result;

		}
		catch (SQLException sqle) {
			throw session.getJdbcServices().getSqlExceptionHelper().convert(
					sqle,
					"could not initialize lazy properties: " + MessageHelper.infoString( this, id, getFactory() ),
					lazySelect
			);
		}
	}

	private Object initializeLazyPropertiesFromCache(
			final String fieldName,
			final Object entity,
			final SharedSessionContractImplementor session,
			final EntityEntry entry,
			final CacheEntry cacheEntry) {

		LOG.trace( "Initializing lazy properties from second-level cache" );

		Object result = null;
		Serializable[] disassembledValues = cacheEntry.getDisassembledState();
		for ( int j = 0; j < lazyPropertyNames.length; j++ ) {
			final Serializable cachedValue = disassembledValues[lazyPropertyNumbers[j]];
			final Type lazyPropertyType = lazyPropertyTypes[j];
			final String propertyName = lazyPropertyNames[j];
			if (cachedValue == LazyPropertyInitializer.UNFETCHED_PROPERTY) {
				if (fieldName.equals(propertyName)) {
					result = LazyPropertyInitializer.UNFETCHED_PROPERTY;
				}
				// don't try to initialize the unfetched property
			}
			else {
				final Object propValue = lazyPropertyType.assemble(
						cachedValue,
						session,
						entity
				);
				if ( initializeLazyProperty( fieldName, entity, session, entry, j, propValue ) ) {
					result = propValue;
				}
			}
		}

		LOG.trace( "Done initializing lazy properties" );

		return result;
	}

	private boolean initializeLazyProperty(
			final String fieldName,
			final Object entity,
			final SharedSessionContractImplementor session,
			final EntityEntry entry,
			final int j,
			final Object propValue) {
		setPropertyValue( entity, lazyPropertyNumbers[j], propValue );
		if ( entry.getLoadedState() != null ) {
			// object have been loaded with setReadOnly(true); HHH-2236
			entry.getLoadedState()[lazyPropertyNumbers[j]] = lazyPropertyTypes[j].deepCopy( propValue, factory );
		}
		// If the entity has deleted state, then update that as well
		if ( entry.getDeletedState() != null ) {
			entry.getDeletedState()[lazyPropertyNumbers[j]] = lazyPropertyTypes[j].deepCopy( propValue, factory );
		}
		return fieldName.equals( lazyPropertyNames[j] );
	}

	public boolean isBatchable() {
		return optimisticLockStyle() == OptimisticLockStyle.NONE
				|| ( !isVersioned() && optimisticLockStyle() == OptimisticLockStyle.VERSION )
				|| getFactory().getSessionFactoryOptions().isJdbcBatchVersionedData();
	}

	@Override
	public NavigableRole getNavigableRole() {
		return navigableRole;
	}

	public Serializable[] getQuerySpaces() {
		return getPropertySpaces();
	}

	public boolean isBatchLoadable() {
		return batchSize > 1;
	}

	public String[] getIdentifierColumnNames() {
		return rootTableKeyColumnNames;
	}

	public String[] getIdentifierColumnReaders() {
		return rootTableKeyColumnReaders;
	}

	public String[] getIdentifierColumnReaderTemplates() {
		return rootTableKeyColumnReaderTemplates;
	}

	protected int getIdentifierColumnSpan() {
		return identifierColumnSpan;
	}

	protected String[] getIdentifierAliases() {
		return identifierAliases;
	}

	public String getVersionColumnName() {
		return versionColumnName;
	}

	protected String getVersionedTableName() {
		return getTableName( 0 );
	}

	protected boolean[] getSubclassColumnLazyiness() {
		return subclassColumnLazyClosure;
	}

	protected boolean[] getSubclassFormulaLazyiness() {
		return subclassFormulaLazyClosure;
	}

	/**
	 * We can't immediately add to the cache if we have formulas
	 * which must be evaluated, or if we have the possibility of
	 * two concurrent updates to the same item being merged on
	 * the database. This can happen if (a) the item is not
	 * versioned and either (b) we have dynamic update enabled
	 * or (c) we have multiple tables holding the state of the
	 * item.
	 */
	public boolean isCacheInvalidationRequired() {
		return invalidateCache;
	}

	public boolean isLazyPropertiesCacheable() {
		return isLazyPropertiesCacheable;
	}

	public String selectFragment(String alias, String suffix) {
		return identifierSelectFragment( alias, suffix ) +
				propertySelectFragment( alias, suffix, false );
	}

	public String[] getIdentifierAliases(String suffix) {
		// NOTE: this assumes something about how propertySelectFragment is implemented by the subclass!
		// was toUnqotedAliasStrings( getIdentifierColumnNames() ) before - now tried
		// to remove that unqoting and missing aliases..
		return new Alias( suffix ).toAliasStrings( getIdentifierAliases() );
	}

	public String[] getPropertyAliases(String suffix, int i) {
		// NOTE: this assumes something about how propertySelectFragment is implemented by the subclass!
		return new Alias( suffix ).toUnquotedAliasStrings( propertyColumnAliases[i] );
	}

	public String getDiscriminatorAlias(String suffix) {
		// NOTE: this assumes something about how propertySelectFragment is implemented by the subclass!
		// was toUnqotedAliasStrings( getdiscriminatorColumnName() ) before - now tried
		// to remove that unqoting and missing aliases..
		return entityMetamodel.hasSubclasses() ?
				new Alias( suffix ).toAliasString( getDiscriminatorAlias() ) :
				null;
	}

	public String identifierSelectFragment(String name, String suffix) {
		return new SelectFragment()
				.setSuffix( suffix )
				.addColumns( name, getIdentifierColumnNames(), getIdentifierAliases() )
				.toFragmentString()
				.substring( 2 ); //strip leading ", "
	}


	public String propertySelectFragment(String tableAlias, String suffix, boolean allProperties) {
		return propertySelectFragmentFragment( tableAlias, suffix, allProperties ).toFragmentString();
	}

	public SelectFragment propertySelectFragmentFragment(
			String tableAlias,
			String suffix,
			boolean allProperties) {
		SelectFragment select = new SelectFragment()
				.setSuffix( suffix )
				.setUsedAliases( getIdentifierAliases() );

		int[] columnTableNumbers = getSubclassColumnTableNumberClosure();
		String[] columnAliases = getSubclassColumnAliasClosure();
		String[] columnReaderTemplates = getSubclassColumnReaderTemplateClosure();
		for ( int i = 0; i < getSubclassColumnClosure().length; i++ ) {
			boolean selectable = ( allProperties || !subclassColumnLazyClosure[i] ) &&
					!isSubclassTableSequentialSelect( columnTableNumbers[i] ) &&
					subclassColumnSelectableClosure[i];
			if ( selectable ) {
				String subalias = generateTableAlias( tableAlias, columnTableNumbers[i] );
				select.addColumnTemplate( subalias, columnReaderTemplates[i], columnAliases[i] );
			}
		}

		int[] formulaTableNumbers = getSubclassFormulaTableNumberClosure();
		String[] formulaTemplates = getSubclassFormulaTemplateClosure();
		String[] formulaAliases = getSubclassFormulaAliasClosure();
		for ( int i = 0; i < getSubclassFormulaTemplateClosure().length; i++ ) {
			boolean selectable = ( allProperties || !subclassFormulaLazyClosure[i] )
					&& !isSubclassTableSequentialSelect( formulaTableNumbers[i] );
			if ( selectable ) {
				String subalias = generateTableAlias( tableAlias, formulaTableNumbers[i] );
				select.addFormula( subalias, formulaTemplates[i], formulaAliases[i] );
			}
		}

		if ( entityMetamodel.hasSubclasses() ) {
			addDiscriminatorToSelect( select, tableAlias, suffix );
		}

		if ( hasRowId() ) {
			select.addColumn( tableAlias, rowIdName, ROWID_ALIAS );
		}

		return select;
	}

	public Object[] getDatabaseSnapshot(Serializable id, SharedSessionContractImplementor session)
			throws HibernateException {

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev(
					"Getting current persistent state for: {0}", MessageHelper.infoString(
							this,
							id,
							getFactory()
					)
			);
		}

		try {
			PreparedStatement ps = session
					.getJdbcCoordinator()
					.getStatementPreparer()
					.prepareStatement( getSQLSnapshotSelectString() );
			try {
				getIdentifierType().nullSafeSet( ps, id, 1, session );
				//if ( isVersioned() ) getVersionType().nullSafeSet( ps, version, getIdentifierColumnSpan()+1, session );
				ResultSet rs = session.getJdbcCoordinator().getResultSetReturn().extract( ps );
				try {
					//if there is no resulting row, return null
					if ( !rs.next() ) {
						return null;
					}
					//otherwise return the "hydrated" state (ie. associations are not resolved)
					Type[] types = getPropertyTypes();
					Object[] values = new Object[types.length];
					boolean[] includeProperty = getPropertyUpdateability();
					for ( int i = 0; i < types.length; i++ ) {
						if ( includeProperty[i] ) {
							values[i] = types[i].hydrate(
									rs,
									getPropertyAliases( "", i ),
									session,
									null
							); //null owner ok??
						}
					}
					return values;
				}
				finally {
					session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( rs, ps );
				}
			}
			finally {
				session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( ps );
				session.getJdbcCoordinator().afterStatementExecution();
			}
		}
		catch (SQLException e) {
			throw session.getJdbcServices().getSqlExceptionHelper().convert(
					e,
					"could not retrieve snapshot: " + MessageHelper.infoString( this, id, getFactory() ),
					getSQLSnapshotSelectString()
			);
		}

	}

	@Override
	public Serializable getIdByUniqueKey(Serializable key, String uniquePropertyName, SharedSessionContractImplementor session)
			throws HibernateException {
		if ( LOG.isTraceEnabled() ) {
			LOG.tracef(
					"resolving unique key [%s] to identifier for entity [%s]",
					key,
					getEntityName()
			);
		}

		int propertyIndex = getSubclassPropertyIndex( uniquePropertyName );
		if ( propertyIndex < 0 ) {
			throw new HibernateException(
					"Could not determine Type for property [" + uniquePropertyName + "] on entity [" + getEntityName() + "]"
			);
		}
		Type propertyType = getSubclassPropertyType( propertyIndex );

		try {
			PreparedStatement ps = session
					.getJdbcCoordinator()
					.getStatementPreparer()
					.prepareStatement( generateIdByUniqueKeySelectString( uniquePropertyName ) );
			try {
				propertyType.nullSafeSet( ps, key, 1, session );
				ResultSet rs = session.getJdbcCoordinator().getResultSetReturn().extract( ps );
				try {
					//if there is no resulting row, return null
					if ( !rs.next() ) {
						return null;
					}
					return (Serializable) getIdentifierType().nullSafeGet( rs, getIdentifierAliases(), session, null );
				}
				finally {
					session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( rs, ps );
				}
			}
			finally {
				session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( ps );
				session.getJdbcCoordinator().afterStatementExecution();
			}
		}
		catch (SQLException e) {
			throw session.getJdbcServices().getSqlExceptionHelper().convert(
					e,
					String.format(
							"could not resolve unique property [%s] to identifier for entity [%s]",
							uniquePropertyName,
							getEntityName()
					),
					getSQLSnapshotSelectString()
			);
		}

	}

	protected String generateIdByUniqueKeySelectString(String uniquePropertyName) {
		Select select = new Select( getFactory().getDialect() );

		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			select.setComment( "resolve id by unique property [" + getEntityName() + "." + uniquePropertyName + "]" );
		}

		final String rooAlias = getRootAlias();

		select.setFromClause( fromTableFragment( rooAlias ) + fromJoinFragment( rooAlias, true, false ) );

		SelectFragment selectFragment = new SelectFragment();
		selectFragment.addColumns( rooAlias, getIdentifierColumnNames(), getIdentifierAliases() );
		select.setSelectClause( selectFragment );

		StringBuilder whereClauseBuffer = new StringBuilder();
		final int uniquePropertyIndex = getSubclassPropertyIndex( uniquePropertyName );
		final String uniquePropertyTableAlias = generateTableAlias(
				rooAlias,
				getSubclassPropertyTableNumber( uniquePropertyIndex )
		);
		String sep = "";
		for ( String columnTemplate : getSubclassPropertyColumnReaderTemplateClosure()[uniquePropertyIndex] ) {
			if ( columnTemplate == null ) {
				continue;
			}
			final String columnReference = StringHelper.replace(
					columnTemplate,
					Template.TEMPLATE,
					uniquePropertyTableAlias
			);
			whereClauseBuffer.append( sep ).append( columnReference ).append( "=?" );
			sep = " and ";
		}
		for ( String formulaTemplate : getSubclassPropertyFormulaTemplateClosure()[uniquePropertyIndex] ) {
			if ( formulaTemplate == null ) {
				continue;
			}
			final String formulaReference = StringHelper.replace(
					formulaTemplate,
					Template.TEMPLATE,
					uniquePropertyTableAlias
			);
			whereClauseBuffer.append( sep ).append( formulaReference ).append( "=?" );
			sep = " and ";
		}
		whereClauseBuffer.append( whereJoinFragment( rooAlias, true, false ) );

		select.setWhereClause( whereClauseBuffer.toString() );

		return select.setOuterJoins( "", "" ).toStatementString();
	}


	/**
	 * Generate the SQL that selects the version number by id
	 */
	protected String generateSelectVersionString() {
		SimpleSelect select = new SimpleSelect( getFactory().getDialect() )
				.setTableName( getVersionedTableName() );
		if ( isVersioned() ) {
			select.addColumn( versionColumnName );
		}
		else {
			select.addColumns( rootTableKeyColumnNames );
		}
		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			select.setComment( "get version " + getEntityName() );
		}
		return select.addCondition( rootTableKeyColumnNames, "=?" ).toStatementString();
	}

	public boolean[] getPropertyUniqueness() {
		return propertyUniqueness;
	}

	protected String generateInsertGeneratedValuesSelectString() {
		return generateGeneratedValuesSelectString( GenerationTiming.INSERT );
	}

	protected String generateUpdateGeneratedValuesSelectString() {
		return generateGeneratedValuesSelectString( GenerationTiming.ALWAYS );
	}

	private String generateGeneratedValuesSelectString(final GenerationTiming generationTimingToMatch) {
		Select select = new Select( getFactory().getDialect() );

		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			select.setComment( "get generated state " + getEntityName() );
		}

		String[] aliasedIdColumns = StringHelper.qualify( getRootAlias(), getIdentifierColumnNames() );

		// Here we render the select column list based on the properties defined as being generated.
		// For partial component generation, we currently just re-select the whole component
		// rather than trying to handle the individual generated portions.
		String selectClause = concretePropertySelectFragment(
				getRootAlias(),
				new InclusionChecker() {
					@Override
					public boolean includeProperty(int propertyNumber) {
						final InDatabaseValueGenerationStrategy generationStrategy
								= entityMetamodel.getInDatabaseValueGenerationStrategies()[propertyNumber];
						return generationStrategy != null
								&& timingsMatch( generationStrategy.getGenerationTiming(), generationTimingToMatch );
					}
				}
		);
		selectClause = selectClause.substring( 2 );

		String fromClause = fromTableFragment( getRootAlias() ) +
				fromJoinFragment( getRootAlias(), true, false );

		String whereClause = new StringBuilder()
				.append( String.join( "=? and ", aliasedIdColumns ) )
				.append( "=?" )
				.append( whereJoinFragment( getRootAlias(), true, false ) )
				.toString();

		return select.setSelectClause( selectClause )
				.setFromClause( fromClause )
				.setOuterJoins( "", "" )
				.setWhereClause( whereClause )
				.toStatementString();
	}

	protected interface InclusionChecker {
		boolean includeProperty(int propertyNumber);
	}

	protected String concretePropertySelectFragment(String alias, final boolean[] includeProperty) {
		return concretePropertySelectFragment(
				alias,
				new InclusionChecker() {
					public boolean includeProperty(int propertyNumber) {
						return includeProperty[propertyNumber];
					}
				}
		);
	}

	protected String concretePropertySelectFragment(String alias, InclusionChecker inclusionChecker) {
		int propertyCount = getPropertyNames().length;
		int[] propertyTableNumbers = getPropertyTableNumbersInSelect();
		SelectFragment frag = new SelectFragment();
		for ( int i = 0; i < propertyCount; i++ ) {
			if ( inclusionChecker.includeProperty( i ) ) {
				frag.addColumnTemplates(
						generateTableAlias( alias, propertyTableNumbers[i] ),
						propertyColumnReaderTemplates[i],
						propertyColumnAliases[i]
				);
				frag.addFormulas(
						generateTableAlias( alias, propertyTableNumbers[i] ),
						propertyColumnFormulaTemplates[i],
						propertyColumnAliases[i]
				);
			}
		}
		return frag.toFragmentString();
	}

	protected String generateSnapshotSelectString() {

		//TODO: should we use SELECT .. FOR UPDATE?

		Select select = new Select( getFactory().getDialect() );

		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			select.setComment( "get current state " + getEntityName() );
		}

		String[] aliasedIdColumns = StringHelper.qualify( getRootAlias(), getIdentifierColumnNames() );
		String selectClause = String.join( ", ", aliasedIdColumns ) +
				concretePropertySelectFragment( getRootAlias(), getPropertyUpdateability() );

		String fromClause = fromTableFragment( getRootAlias() ) +
				fromJoinFragment( getRootAlias(), true, false );

		String whereClause = new StringBuilder()
				.append(
						String.join(
								"=? and ",
								aliasedIdColumns
						)
				)
				.append( "=?" )
				.append( whereJoinFragment( getRootAlias(), true, false ) )
				.toString();

		/*if ( isVersioned() ) {
			where.append(" and ")
				.append( getVersionColumnName() )
				.append("=?");
		}*/

		return select.setSelectClause( selectClause )
				.setFromClause( fromClause )
				.setOuterJoins( "", "" )
				.setWhereClause( whereClause )
				.toStatementString();
	}

	public Object forceVersionIncrement(Serializable id, Object currentVersion, SharedSessionContractImplementor session) {
		if ( !isVersioned() ) {
			throw new AssertionFailure( "cannot force version increment on non-versioned entity" );
		}

		if ( isVersionPropertyGenerated() ) {
			// the difficulty here is exactly what do we update in order to
			// force the version to be incremented in the db...
			throw new HibernateException( "LockMode.FORCE is currently not supported for generated version properties" );
		}

		Object nextVersion = getVersionType().next( currentVersion, session );
		if ( LOG.isTraceEnabled() ) {
			LOG.trace(
					"Forcing version increment [" + MessageHelper.infoString( this, id, getFactory() ) + "; "
							+ getVersionType().toLoggableString( currentVersion, getFactory() ) + " -> "
							+ getVersionType().toLoggableString( nextVersion, getFactory() ) + "]"
			);
		}

		// todo : cache this sql...
		String versionIncrementString = generateVersionIncrementUpdateString();
		PreparedStatement st;
		try {
			st = session
					.getJdbcCoordinator()
					.getStatementPreparer()
					.prepareStatement( versionIncrementString, false );
			try {
				getVersionType().nullSafeSet( st, nextVersion, 1, session );
				getIdentifierType().nullSafeSet( st, id, 2, session );
				getVersionType().nullSafeSet( st, currentVersion, 2 + getIdentifierColumnSpan(), session );
				int rows = session.getJdbcCoordinator().getResultSetReturn().executeUpdate( st );
				if ( rows != 1 ) {
					throw new StaleObjectStateException( getEntityName(), id );
				}
			}
			finally {
				session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( st );
				session.getJdbcCoordinator().afterStatementExecution();
			}
		}
		catch (SQLException sqle) {
			throw session.getJdbcServices().getSqlExceptionHelper().convert(
					sqle,
					"could not retrieve version: " +
							MessageHelper.infoString( this, id, getFactory() ),
					getVersionSelectString()
			);
		}

		return nextVersion;
	}

	private String generateVersionIncrementUpdateString() {
		Update update = new Update( getFactory().getDialect() );
		update.setTableName( getTableName( 0 ) );
		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			update.setComment( "forced version increment" );
		}
		update.addColumn( getVersionColumnName() );
		update.addPrimaryKeyColumns( rootTableKeyColumnNames );
		update.setVersionColumnName( getVersionColumnName() );
		return update.toStatementString();
	}

	/**
	 * Retrieve the version number
	 */
	public Object getCurrentVersion(Serializable id, SharedSessionContractImplementor session) throws HibernateException {

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Getting version: {0}", MessageHelper.infoString( this, id, getFactory() ) );
		}

		try {
			PreparedStatement st = session
					.getJdbcCoordinator()
					.getStatementPreparer()
					.prepareStatement( getVersionSelectString() );
			try {
				getIdentifierType().nullSafeSet( st, id, 1, session );
				ResultSet rs = session.getJdbcCoordinator().getResultSetReturn().extract( st );
				try {
					if ( !rs.next() ) {
						return null;
					}
					if ( !isVersioned() ) {
						return this;
					}
					return getVersionType().nullSafeGet( rs, getVersionColumnName(), session, null );
				}
				finally {
					session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( rs, st );
				}
			}
			finally {
				session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( st );
				session.getJdbcCoordinator().afterStatementExecution();
			}
		}
		catch (SQLException e) {
			throw session.getJdbcServices().getSqlExceptionHelper().convert(
					e,
					"could not retrieve version: " + MessageHelper.infoString( this, id, getFactory() ),
					getVersionSelectString()
			);
		}
	}

	protected void initLockers() {
		lockers.put( LockMode.READ, generateLocker( LockMode.READ ) );
		lockers.put( LockMode.UPGRADE, generateLocker( LockMode.UPGRADE ) );
		lockers.put( LockMode.UPGRADE_NOWAIT, generateLocker( LockMode.UPGRADE_NOWAIT ) );
		lockers.put( LockMode.UPGRADE_SKIPLOCKED, generateLocker( LockMode.UPGRADE_SKIPLOCKED ) );
		lockers.put( LockMode.FORCE, generateLocker( LockMode.FORCE ) );
		lockers.put( LockMode.PESSIMISTIC_READ, generateLocker( LockMode.PESSIMISTIC_READ ) );
		lockers.put( LockMode.PESSIMISTIC_WRITE, generateLocker( LockMode.PESSIMISTIC_WRITE ) );
		lockers.put( LockMode.PESSIMISTIC_FORCE_INCREMENT, generateLocker( LockMode.PESSIMISTIC_FORCE_INCREMENT ) );
		lockers.put( LockMode.OPTIMISTIC, generateLocker( LockMode.OPTIMISTIC ) );
		lockers.put( LockMode.OPTIMISTIC_FORCE_INCREMENT, generateLocker( LockMode.OPTIMISTIC_FORCE_INCREMENT ) );
	}

	protected LockingStrategy generateLocker(LockMode lockMode) {
		return factory.getDialect().getLockingStrategy( this, lockMode );
	}

	private LockingStrategy getLocker(LockMode lockMode) {
		return (LockingStrategy) lockers.get( lockMode );
	}

	public void lock(
			Serializable id,
			Object version,
			Object object,
			LockMode lockMode,
			SharedSessionContractImplementor session) throws HibernateException {
		getLocker( lockMode ).lock( id, version, object, LockOptions.WAIT_FOREVER, session );
	}

	public void lock(
			Serializable id,
			Object version,
			Object object,
			LockOptions lockOptions,
			SharedSessionContractImplementor session) throws HibernateException {
		getLocker( lockOptions.getLockMode() ).lock( id, version, object, lockOptions.getTimeOut(), session );
	}

	public String getRootTableName() {
		return getSubclassTableName( 0 );
	}

	public String getRootTableAlias(String drivingAlias) {
		return drivingAlias;
	}

	public String[] getRootTableIdentifierColumnNames() {
		return getRootTableKeyColumnNames();
	}

	/**
	 * {@inheritDoc}
	 *
	 * Warning:
	 * When there are duplicated property names in the subclasses
	 * then this method may return the wrong results.
	 * To ensure correct results, this method should only be used when
	 * {@literal this} is the concrete EntityPersister (since the
	 * concrete EntityPersister cannot have duplicated property names).
	 */
	@Override
	public String[] toColumns(String alias, String propertyName) throws QueryException {
		return propertyMapping.toColumns( alias, propertyName );
	}

	/**
	 * {@inheritDoc}
	 *
	 * Warning:
	 * When there are duplicated property names in the subclasses
	 * then this method may return the wrong results.
	 * To ensure correct results, this method should only be used when
	 * {@literal this} is the concrete EntityPersister (since the
	 * concrete EntityPersister cannot have duplicated property names).
	 */
	@Override
	public String[] toColumns(String propertyName) throws QueryException {
		return propertyMapping.getColumnNames( propertyName );
	}

	/**
	 * {@inheritDoc}
	 *
	 * Warning:
	 * When there are duplicated property names in the subclasses
	 * then this method may return the wrong results.
	 * To ensure correct results, this method should only be used when
	 * {@literal this} is the concrete EntityPersister (since the
	 * concrete EntityPersister cannot have duplicated property names).
	 */
	@Override
	public Type toType(String propertyName) throws QueryException {
		return propertyMapping.toType( propertyName );
	}

	/**
	 * {@inheritDoc}
	 *
	 * Warning:
	 * When there are duplicated property names in the subclasses
	 * then this method may return the wrong results.
	 * To ensure correct results, this method should only be used when
	 * {@literal this} is the concrete EntityPersister (since the
	 * concrete EntityPersister cannot have duplicated property names).
	 */
	@Override
	public String[] getPropertyColumnNames(String propertyName) {
		return propertyMapping.getColumnNames( propertyName );
	}

	/**
	 * Warning:
	 * When there are duplicated property names in the subclasses
	 * of the class, this method may return the wrong table
	 * number for the duplicated subclass property (note that
	 * SingleTableEntityPersister defines an overloaded form
	 * which takes the entity name.
	 */
	public int getSubclassPropertyTableNumber(String propertyPath) {
		String rootPropertyName = StringHelper.root( propertyPath );
		Type type = propertyMapping.toType( rootPropertyName );
		if ( type.isAssociationType() ) {
			AssociationType assocType = (AssociationType) type;
			if ( assocType.useLHSPrimaryKey() ) {
				// performance op to avoid the array search
				return 0;
			}
			else if ( type.isCollectionType() ) {
				// properly handle property-ref-based associations
				rootPropertyName = assocType.getLHSPropertyName();
			}
		}
		//Enable for HHH-440, which we don't like:
		/*if ( type.isComponentType() && !propertyName.equals(rootPropertyName) ) {
			String unrooted = StringHelper.unroot(propertyName);
			int idx = ArrayHelper.indexOf( getSubclassColumnClosure(), unrooted );
			if ( idx != -1 ) {
				return getSubclassColumnTableNumberClosure()[idx];
			}
		}*/
		int index = ArrayHelper.indexOf(
				getSubclassPropertyNameClosure(),
				rootPropertyName
		); //TODO: optimize this better!
		return index == -1 ? 0 : getSubclassPropertyTableNumber( index );
	}

	public Declarer getSubclassPropertyDeclarer(String propertyPath) {
		int tableIndex = getSubclassPropertyTableNumber( propertyPath );
		if ( tableIndex == 0 ) {
			return Declarer.CLASS;
		}
		else if ( isClassOrSuperclassTable( tableIndex ) ) {
			return Declarer.SUPERCLASS;
		}
		else {
			return Declarer.SUBCLASS;
		}
	}

	private DiscriminatorMetadata discriminatorMetadata;

	public DiscriminatorMetadata getTypeDiscriminatorMetadata() {
		if ( discriminatorMetadata == null ) {
			discriminatorMetadata = buildTypeDiscriminatorMetadata();
		}
		return discriminatorMetadata;
	}

	private DiscriminatorMetadata buildTypeDiscriminatorMetadata() {
		return new DiscriminatorMetadata() {
			public String getSqlFragment(String sqlQualificationAlias) {
				return toColumns( sqlQualificationAlias, ENTITY_CLASS )[0];
			}

			public Type getResolutionType() {
				return new DiscriminatorType( getDiscriminatorType(), AbstractEntityPersister.this );
			}
		};
	}

	public static String generateTableAlias(String rootAlias, int tableNumber) {
		if ( tableNumber == 0 ) {
			return rootAlias;
		}
		StringBuilder buf = new StringBuilder().append( rootAlias );
		if ( !rootAlias.endsWith( "_" ) ) {
			buf.append( '_' );
		}
		return buf.append( tableNumber ).append( '_' ).toString();
	}

	public String[] toColumns(String name, final int i) {
		final String alias = generateTableAlias( name, getSubclassPropertyTableNumber( i ) );
		String[] cols = getSubclassPropertyColumnNames( i );
		String[] templates = getSubclassPropertyFormulaTemplateClosure()[i];
		String[] result = new String[cols.length];
		for ( int j = 0; j < cols.length; j++ ) {
			if ( cols[j] == null ) {
				result[j] = StringHelper.replace( templates[j], Template.TEMPLATE, alias );
			}
			else {
				result[j] = StringHelper.qualify( alias, cols[j] );
			}
		}
		return result;
	}

	private int getSubclassPropertyIndex(String propertyName) {
		return ArrayHelper.indexOf( subclassPropertyNameClosure, propertyName );
	}

	protected String[] getPropertySubclassNames() {
		return propertySubclassNames;
	}

	public String[] getPropertyColumnNames(int i) {
		return propertyColumnNames[i];
	}

	public String[] getPropertyColumnWriters(int i) {
		return propertyColumnWriters[i];
	}

	protected int getPropertyColumnSpan(int i) {
		return propertyColumnSpans[i];
	}

	protected boolean hasFormulaProperties() {
		return hasFormulaProperties;
	}

	public FetchMode getFetchMode(int i) {
		return subclassPropertyFetchModeClosure[i];
	}

	public CascadeStyle getCascadeStyle(int i) {
		return subclassPropertyCascadeStyleClosure[i];
	}

	public Type getSubclassPropertyType(int i) {
		return subclassPropertyTypeClosure[i];
	}

	public String getSubclassPropertyName(int i) {
		return subclassPropertyNameClosure[i];
	}

	public int countSubclassProperties() {
		return subclassPropertyTypeClosure.length;
	}

	public String[] getSubclassPropertyColumnNames(int i) {
		return subclassPropertyColumnNameClosure[i];
	}

	public boolean isDefinedOnSubclass(int i) {
		return propertyDefinedOnSubclass[i];
	}

	@Override
	public String[][] getSubclassPropertyFormulaTemplateClosure() {
		return subclassPropertyFormulaTemplateClosure;
	}

	protected Type[] getSubclassPropertyTypeClosure() {
		return subclassPropertyTypeClosure;
	}

	protected String[][] getSubclassPropertyColumnNameClosure() {
		return subclassPropertyColumnNameClosure;
	}

	public String[][] getSubclassPropertyColumnReaderClosure() {
		return subclassPropertyColumnReaderClosure;
	}

	public String[][] getSubclassPropertyColumnReaderTemplateClosure() {
		return subclassPropertyColumnReaderTemplateClosure;
	}

	protected String[] getSubclassPropertyNameClosure() {
		return subclassPropertyNameClosure;
	}

	@Override
	public int[] resolveAttributeIndexes(String[] attributeNames) {
		if ( attributeNames == null || attributeNames.length == 0 ) {
			return new int[0];
		}
		int[] fields = new int[attributeNames.length];
		int counter = 0;

		// We sort to get rid of duplicates
		Arrays.sort( attributeNames );

		Integer index0 = entityMetamodel.getPropertyIndexOrNull( attributeNames[0] );
		if ( index0 != null ) {
			fields[counter++] = index0;
		}

		for ( int i = 0, j = 1; j < attributeNames.length; ++i, ++j ) {
			if ( !attributeNames[i].equals( attributeNames[j] ) ) {
				Integer index = entityMetamodel.getPropertyIndexOrNull( attributeNames[j] );
				if ( index != null ) {
					fields[counter++] = index;
				}
			}
		}

		return Arrays.copyOf( fields, counter );
	}

	protected String[] getSubclassPropertySubclassNameClosure() {
		return subclassPropertySubclassNameClosure;
	}

	protected String[] getSubclassColumnClosure() {
		return subclassColumnClosure;
	}

	protected String[] getSubclassColumnAliasClosure() {
		return subclassColumnAliasClosure;
	}

	public String[] getSubclassColumnReaderTemplateClosure() {
		return subclassColumnReaderTemplateClosure;
	}

	protected String[] getSubclassFormulaClosure() {
		return subclassFormulaClosure;
	}

	protected String[] getSubclassFormulaTemplateClosure() {
		return subclassFormulaTemplateClosure;
	}

	protected String[] getSubclassFormulaAliasClosure() {
		return subclassFormulaAliasClosure;
	}

	public String[] getSubclassPropertyColumnAliases(String propertyName, String suffix) {
		String[] rawAliases = (String[]) subclassPropertyAliases.get( propertyName );

		if ( rawAliases == null ) {
			return null;
		}

		String[] result = new String[rawAliases.length];
		for ( int i = 0; i < rawAliases.length; i++ ) {
			result[i] = new Alias( suffix ).toUnquotedAliasString( rawAliases[i] );
		}
		return result;
	}

	public String[] getSubclassPropertyColumnNames(String propertyName) {
		//TODO: should we allow suffixes on these ?
		return (String[]) subclassPropertyColumnNames.get( propertyName );
	}


	//This is really ugly, but necessary:

	/**
	 * Must be called by subclasses, at the end of their constructors
	 */
	protected void initSubclassPropertyAliasesMap(PersistentClass model) throws MappingException {

		// ALIASES
		internalInitSubclassPropertyAliasesMap( null, model.getSubclassPropertyClosureIterator() );

		// aliases for identifier ( alias.id ); skip if the entity defines a non-id property named 'id'
		if ( !entityMetamodel.hasNonIdentifierPropertyNamedId() ) {
			subclassPropertyAliases.put( ENTITY_ID, getIdentifierAliases() );
			subclassPropertyColumnNames.put( ENTITY_ID, getIdentifierColumnNames() );
		}

		// aliases named identifier ( alias.idname )
		if ( hasIdentifierProperty() ) {
			subclassPropertyAliases.put( getIdentifierPropertyName(), getIdentifierAliases() );
			subclassPropertyColumnNames.put( getIdentifierPropertyName(), getIdentifierColumnNames() );
		}

		// aliases for composite-id's
		if ( getIdentifierType().isComponentType() ) {
			// Fetch embedded identifiers propertynames from the "virtual" identifier component
			CompositeType componentId = (CompositeType) getIdentifierType();
			String[] idPropertyNames = componentId.getPropertyNames();
			String[] idAliases = getIdentifierAliases();
			String[] idColumnNames = getIdentifierColumnNames();

			for ( int i = 0; i < idPropertyNames.length; i++ ) {
				if ( entityMetamodel.hasNonIdentifierPropertyNamedId() ) {
					subclassPropertyAliases.put(
							ENTITY_ID + "." + idPropertyNames[i],
							new String[] {idAliases[i]}
					);
					subclassPropertyColumnNames.put(
							ENTITY_ID + "." + getIdentifierPropertyName() + "." + idPropertyNames[i],
							new String[] {idColumnNames[i]}
					);
				}
//				if (hasIdentifierProperty() && !ENTITY_ID.equals( getIdentifierPropertyNames() ) ) {
				if ( hasIdentifierProperty() ) {
					subclassPropertyAliases.put(
							getIdentifierPropertyName() + "." + idPropertyNames[i],
							new String[] {idAliases[i]}
					);
					subclassPropertyColumnNames.put(
							getIdentifierPropertyName() + "." + idPropertyNames[i],
							new String[] {idColumnNames[i]}
					);
				}
				else {
					// embedded composite ids ( alias.idname1, alias.idname2 )
					subclassPropertyAliases.put( idPropertyNames[i], new String[] {idAliases[i]} );
					subclassPropertyColumnNames.put( idPropertyNames[i], new String[] {idColumnNames[i]} );
				}
			}
		}

		if ( entityMetamodel.isPolymorphic() ) {
			subclassPropertyAliases.put( ENTITY_CLASS, new String[] {getDiscriminatorAlias()} );
			subclassPropertyColumnNames.put( ENTITY_CLASS, new String[] {getDiscriminatorColumnName()} );
		}

	}

	private void internalInitSubclassPropertyAliasesMap(String path, Iterator propertyIterator) {
		while ( propertyIterator.hasNext() ) {

			Property prop = (Property) propertyIterator.next();
			String propname = path == null ? prop.getName() : path + "." + prop.getName();
			if ( prop.isComposite() ) {
				Component component = (Component) prop.getValue();
				Iterator compProps = component.getPropertyIterator();
				internalInitSubclassPropertyAliasesMap( propname, compProps );
			}
			else {
				String[] aliases = new String[prop.getColumnSpan()];
				String[] cols = new String[prop.getColumnSpan()];
				Iterator colIter = prop.getColumnIterator();
				int l = 0;
				while ( colIter.hasNext() ) {
					Selectable thing = (Selectable) colIter.next();
					aliases[l] = thing.getAlias( getFactory().getDialect(), prop.getValue().getTable() );
					cols[l] = thing.getText( getFactory().getDialect() ); // TODO: skip formulas?
					l++;
				}

				subclassPropertyAliases.put( propname, aliases );
				subclassPropertyColumnNames.put( propname, cols );
			}
		}

	}

	public Object loadByUniqueKey(
			String propertyName,
			Object uniqueKey,
			SharedSessionContractImplementor session) throws HibernateException {
		return getAppropriateUniqueKeyLoader( propertyName, session ).loadByUniqueKey( session, uniqueKey );
	}

	private EntityLoader getAppropriateUniqueKeyLoader(String propertyName, SharedSessionContractImplementor session) {
		final boolean useStaticLoader = !session.getLoadQueryInfluencers().hasEnabledFilters()
				&& !session.getLoadQueryInfluencers().hasEnabledFetchProfiles()
				&& propertyName.indexOf( '.' ) < 0; //ugly little workaround for fact that createUniqueKeyLoaders() does not handle component properties

		if ( useStaticLoader ) {
			return (EntityLoader) uniqueKeyLoaders.get( propertyName );
		}
		else {
			return createUniqueKeyLoader(
					propertyMapping.toType( propertyName ),
					propertyMapping.toColumns( propertyName ),
					session.getLoadQueryInfluencers()
			);
		}
	}

	public int getPropertyIndex(String propertyName) {
		return entityMetamodel.getPropertyIndex( propertyName );
	}

	protected void createUniqueKeyLoaders() throws MappingException {
		Type[] propertyTypes = getPropertyTypes();
		String[] propertyNames = getPropertyNames();
		for ( int i = 0; i < entityMetamodel.getPropertySpan(); i++ ) {
			if ( propertyUniqueness[i] ) {
				//don't need filters for the static loaders
				uniqueKeyLoaders.put(
						propertyNames[i],
						createUniqueKeyLoader(
								propertyTypes[i],
								getPropertyColumnNames( i ),
								LoadQueryInfluencers.NONE
						)
				);
				//TODO: create uk loaders for component properties
			}
		}
	}

	private EntityLoader createUniqueKeyLoader(
			Type uniqueKeyType,
			String[] columns,
			LoadQueryInfluencers loadQueryInfluencers) {
		if ( uniqueKeyType.isEntityType() ) {
			String className = ( (EntityType) uniqueKeyType ).getAssociatedEntityName();
			uniqueKeyType = getFactory().getMetamodel().entityPersister( className ).getIdentifierType();
		}
		return new EntityLoader(
				this,
				columns,
				uniqueKeyType,
				1,
				LockMode.NONE,
				getFactory(),
				loadQueryInfluencers
		);
	}

	protected String getSQLWhereString(String alias) {
		return StringHelper.replace( sqlWhereStringTemplate, Template.TEMPLATE, alias );
	}

	protected boolean hasWhere() {
		return sqlWhereString != null;
	}

	private void initOrdinaryPropertyPaths(Mapping mapping) throws MappingException {
		for ( int i = 0; i < getSubclassPropertyNameClosure().length; i++ ) {
			propertyMapping.initPropertyPaths(
					getSubclassPropertyNameClosure()[i],
					getSubclassPropertyTypeClosure()[i],
					getSubclassPropertyColumnNameClosure()[i],
					getSubclassPropertyColumnReaderClosure()[i],
					getSubclassPropertyColumnReaderTemplateClosure()[i],
					getSubclassPropertyFormulaTemplateClosure()[i],
					mapping
			);
		}
	}

	private void initIdentifierPropertyPaths(Mapping mapping) throws MappingException {
		String idProp = getIdentifierPropertyName();
		if ( idProp != null ) {
			propertyMapping.initPropertyPaths(
					idProp, getIdentifierType(), getIdentifierColumnNames(),
					getIdentifierColumnReaders(), getIdentifierColumnReaderTemplates(), null, mapping
			);
		}
		if ( entityMetamodel.getIdentifierProperty().isEmbedded() ) {
			propertyMapping.initPropertyPaths(
					null, getIdentifierType(), getIdentifierColumnNames(),
					getIdentifierColumnReaders(), getIdentifierColumnReaderTemplates(), null, mapping
			);
		}
		if ( !entityMetamodel.hasNonIdentifierPropertyNamedId() ) {
			propertyMapping.initPropertyPaths(
					ENTITY_ID, getIdentifierType(), getIdentifierColumnNames(),
					getIdentifierColumnReaders(), getIdentifierColumnReaderTemplates(), null, mapping
			);
		}
	}

	private void initDiscriminatorPropertyPath(Mapping mapping) throws MappingException {
		propertyMapping.initPropertyPaths(
				ENTITY_CLASS,
				getDiscriminatorType(),
				new String[] {getDiscriminatorColumnName()},
				new String[] {getDiscriminatorColumnReaders()},
				new String[] {getDiscriminatorColumnReaderTemplate()},
				new String[] {getDiscriminatorFormulaTemplate()},
				getFactory()
		);
	}

	protected void initPropertyPaths(Mapping mapping) throws MappingException {
		initOrdinaryPropertyPaths( mapping );
		initOrdinaryPropertyPaths( mapping ); //do two passes, for collection property-ref!
		initIdentifierPropertyPaths( mapping );
		if ( entityMetamodel.isPolymorphic() ) {
			initDiscriminatorPropertyPath( mapping );
		}
	}

	protected UniqueEntityLoader createEntityLoader(
			LockMode lockMode,
			LoadQueryInfluencers loadQueryInfluencers) throws MappingException {
		//TODO: disable batch loading if lockMode > READ?
		return BatchingEntityLoaderBuilder.getBuilder( getFactory() )
				.buildLoader( this, batchSize, lockMode, getFactory(), loadQueryInfluencers );
	}

	protected UniqueEntityLoader createEntityLoader(
			LockOptions lockOptions,
			LoadQueryInfluencers loadQueryInfluencers) throws MappingException {
		//TODO: disable batch loading if lockMode > READ?
		return BatchingEntityLoaderBuilder.getBuilder( getFactory() )
				.buildLoader( this, batchSize, lockOptions, getFactory(), loadQueryInfluencers );
	}

	/**
	 * Used internally to create static loaders.  These are the default set of loaders used to handle get()/load()
	 * processing.  lock() handling is done by the LockingStrategy instances (see {@link #getLocker})
	 *
	 * @param lockMode The lock mode to apply to the thing being loaded.
	 *
	 * @return
	 *
	 * @throws MappingException
	 */
	protected UniqueEntityLoader createEntityLoader(LockMode lockMode) throws MappingException {
		return createEntityLoader( lockMode, LoadQueryInfluencers.NONE );
	}

	protected boolean check(
			int rows,
			Serializable id,
			int tableNumber,
			Expectation expectation,
			PreparedStatement statement) throws HibernateException {
		try {
			expectation.verifyOutcome( rows, statement, -1 );
		}
		catch (StaleStateException e) {
			if ( !isNullableTable( tableNumber ) ) {
				final StatisticsImplementor statistics = getFactory().getStatistics();
				if ( statistics.isStatisticsEnabled() ) {
					statistics.optimisticFailure( getEntityName() );
				}
				throw new StaleObjectStateException( getEntityName(), id );
			}
			return false;
		}
		catch (TooManyRowsAffectedException e) {
			throw new HibernateException(
					"Duplicate identifier in table for: " +
							MessageHelper.infoString( this, id, getFactory() )
			);
		}
		catch (Throwable t) {
			return false;
		}
		return true;
	}

	protected String generateUpdateString(boolean[] includeProperty, int j, boolean useRowId) {
		return generateUpdateString( includeProperty, j, null, useRowId );
	}

	/**
	 * Generate the SQL that updates a row by id (and version)
	 */
	protected String generateUpdateString(
			final boolean[] includeProperty,
			final int j,
			final Object[] oldFields,
			final boolean useRowId) {

		Update update = new Update( getFactory().getDialect() ).setTableName( getTableName( j ) );

		// select the correct row by either pk or rowid
		if ( useRowId ) {
			update.addPrimaryKeyColumns( new String[] {rowIdName} ); //TODO: eventually, rowIdName[j]
		}
		else {
			update.addPrimaryKeyColumns( getKeyColumns( j ) );
		}

		boolean hasColumns = false;
		for ( int i = 0; i < entityMetamodel.getPropertySpan(); i++ ) {
			if ( includeProperty[i] && isPropertyOfTable( i, j )
					&& !lobProperties.contains( i ) ) {
				// this is a property of the table, which we are updating
				update.addColumns(
						getPropertyColumnNames( i ),
						propertyColumnUpdateable[i], propertyColumnWriters[i]
				);
				hasColumns = hasColumns || getPropertyColumnSpan( i ) > 0;
			}
		}

		// HHH-4635
		// Oracle expects all Lob properties to be last in inserts
		// and updates.  Insert them at the end.
		for ( int i : lobProperties ) {
			if ( includeProperty[i] && isPropertyOfTable( i, j ) ) {
				// this property belongs on the table and is to be inserted
				update.addColumns(
						getPropertyColumnNames( i ),
						propertyColumnUpdateable[i], propertyColumnWriters[i]
				);
				hasColumns = true;
			}
		}

		if ( j == 0 && isVersioned() && entityMetamodel.getOptimisticLockStyle() == OptimisticLockStyle.VERSION ) {
			// this is the root (versioned) table, and we are using version-based
			// optimistic locking;  if we are not updating the version, also don't
			// check it (unless this is a "generated" version column)!
			if ( checkVersion( includeProperty ) ) {
				update.setVersionColumnName( getVersionColumnName() );
				hasColumns = true;
			}
		}
		else if ( isAllOrDirtyOptLocking() && oldFields != null ) {
			// we are using "all" or "dirty" property-based optimistic locking

			boolean[] includeInWhere = entityMetamodel.getOptimisticLockStyle() == OptimisticLockStyle.ALL
					?
					getPropertyUpdateability()
					//optimistic-lock="all", include all updatable properties
					:
					includeProperty;             //optimistic-lock="dirty", include all properties we are updating this time

			boolean[] versionability = getPropertyVersionability();
			Type[] types = getPropertyTypes();
			for ( int i = 0; i < entityMetamodel.getPropertySpan(); i++ ) {
				boolean include = includeInWhere[i] &&
						isPropertyOfTable( i, j ) &&
						versionability[i];
				if ( include ) {
					// this property belongs to the table, and it is not specifically
					// excluded from optimistic locking by optimistic-lock="false"
					String[] propertyColumnNames = getPropertyColumnNames( i );
					String[] propertyColumnWriters = getPropertyColumnWriters( i );
					boolean[] propertyNullness = types[i].toColumnNullness( oldFields[i], getFactory() );
					for ( int k = 0; k < propertyNullness.length; k++ ) {
						if ( propertyNullness[k] ) {
							update.addWhereColumn( propertyColumnNames[k], "=" + propertyColumnWriters[k] );
						}
						else {
							update.addWhereColumn( propertyColumnNames[k], " is null" );
						}
					}
				}
			}

		}

		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			update.setComment( "update " + getEntityName() );
		}

		return hasColumns ? update.toStatementString() : null;
	}

	protected final boolean checkVersion(final boolean[] includeProperty) {
		return includeProperty[getVersionProperty()]
				|| entityMetamodel.isVersionGenerated();
	}

	protected String generateInsertString(boolean[] includeProperty, int j) {
		return generateInsertString( false, includeProperty, j );
	}

	protected String generateInsertString(boolean identityInsert, boolean[] includeProperty) {
		return generateInsertString( identityInsert, includeProperty, 0 );
	}

	/**
	 * Generate the SQL that inserts a row
	 */
	protected String generateInsertString(boolean identityInsert, boolean[] includeProperty, int j) {

		// todo : remove the identityInsert param and variations;
		//   identity-insert strings are now generated from generateIdentityInsertString()

		Insert insert = new Insert( getFactory().getDialect() )
				.setTableName( getTableName( j ) );

		// add normal properties
		for ( int i = 0; i < entityMetamodel.getPropertySpan(); i++ ) {
			// the incoming 'includeProperty' array only accounts for insertable defined at the root level, it
			// does not account for partially generated composites etc.  We also need to account for generation
			// values
			if ( isPropertyOfTable( i, j ) ) {
				if ( !lobProperties.contains( i ) ) {
					final InDatabaseValueGenerationStrategy generationStrategy = entityMetamodel.getInDatabaseValueGenerationStrategies()[i];
					if ( generationStrategy != null && generationStrategy.getGenerationTiming().includesInsert() ) {
						if ( generationStrategy.referenceColumnsInSql() ) {
							final String[] values;
							if ( generationStrategy.getReferencedColumnValues() == null ) {
								values = propertyColumnWriters[i];
							}
							else {
								final int numberOfColumns = propertyColumnWriters[i].length;
								values = new String[numberOfColumns];
								for ( int x = 0; x < numberOfColumns; x++ ) {
									if ( generationStrategy.getReferencedColumnValues()[x] != null ) {
										values[x] = generationStrategy.getReferencedColumnValues()[x];
									}
									else {
										values[x] = propertyColumnWriters[i][x];
									}
								}
							}
							insert.addColumns( getPropertyColumnNames( i ), propertyColumnInsertable[i], values );
						}
					}
					else if ( includeProperty[i] ) {
						insert.addColumns(
								getPropertyColumnNames( i ),
								propertyColumnInsertable[i],
								propertyColumnWriters[i]
						);
					}
				}
			}
		}

		// add the discriminator
		if ( j == 0 ) {
			addDiscriminatorToInsert( insert );
		}

		// add the primary key
		if ( j == 0 && identityInsert ) {
			insert.addIdentityColumn( getKeyColumns( 0 )[0] );
		}
		else {
			insert.addColumns( getKeyColumns( j ) );
		}

		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			insert.setComment( "insert " + getEntityName() );
		}

		// HHH-4635
		// Oracle expects all Lob properties to be last in inserts
		// and updates.  Insert them at the end.
		for ( int i : lobProperties ) {
			if ( includeProperty[i] && isPropertyOfTable( i, j ) ) {
				// this property belongs on the table and is to be inserted
				insert.addColumns(
						getPropertyColumnNames( i ),
						propertyColumnInsertable[i],
						propertyColumnWriters[i]
				);
			}
		}

		String result = insert.toStatementString();

		// append the SQL to return the generated identifier
		if ( j == 0 && identityInsert && useInsertSelectIdentity() ) { //TODO: suck into Insert
			result = getFactory().getDialect().getIdentityColumnSupport().appendIdentitySelectToInsert( result );
		}

		return result;
	}

	/**
	 * Used to generate an insery statement against the root table in the
	 * case of identifier generation strategies where the insert statement
	 * executions actually generates the identifier value.
	 *
	 * @param includeProperty indices of the properties to include in the
	 * insert statement.
	 *
	 * @return The insert SQL statement string
	 */
	protected String generateIdentityInsertString(boolean[] includeProperty) {
		Insert insert = identityDelegate.prepareIdentifierGeneratingInsert();
		insert.setTableName( getTableName( 0 ) );

		// add normal properties except lobs
		for ( int i = 0; i < entityMetamodel.getPropertySpan(); i++ ) {
			if ( isPropertyOfTable( i, 0 ) && !lobProperties.contains( i ) ) {
				final InDatabaseValueGenerationStrategy generationStrategy = entityMetamodel.getInDatabaseValueGenerationStrategies()[i];

				if ( includeProperty[i] ) {
					insert.addColumns(
							getPropertyColumnNames( i ),
							propertyColumnInsertable[i],
							propertyColumnWriters[i]
					);
				}
				else if ( generationStrategy != null &&
						generationStrategy.getGenerationTiming().includesInsert() &&
						generationStrategy.referenceColumnsInSql() ) {

					final String[] values;

					if ( generationStrategy.getReferencedColumnValues() == null ) {
						values = propertyColumnWriters[i];
					}
					else {
						values = new String[propertyColumnWriters[i].length];

						for ( int j = 0; j < values.length; j++ ) {
							values[j] = ( generationStrategy.getReferencedColumnValues()[j] != null ) ?
									generationStrategy.getReferencedColumnValues()[j] :
									propertyColumnWriters[i][j];
						}
					}
					insert.addColumns(
							getPropertyColumnNames( i ),
							propertyColumnInsertable[i],
							values
					);
				}
			}
		}

		// HHH-4635 & HHH-8103
		// Oracle expects all Lob properties to be last in inserts
		// and updates.  Insert them at the end.
		for ( int i : lobProperties ) {
			if ( includeProperty[i] && isPropertyOfTable( i, 0 ) ) {
				insert.addColumns( getPropertyColumnNames( i ), propertyColumnInsertable[i], propertyColumnWriters[i] );
			}
		}

		// add the discriminator
		addDiscriminatorToInsert( insert );

		// delegate already handles PK columns

		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			insert.setComment( "insert " + getEntityName() );
		}

		return insert.toStatementString();
	}

	/**
	 * Generate the SQL that deletes a row by id (and version)
	 */
	protected String generateDeleteString(int j) {
		final Delete delete = new Delete()
				.setTableName( getTableName( j ) )
				.addPrimaryKeyColumns( getKeyColumns( j ) );
		if ( j == 0 ) {
			delete.setVersionColumnName( getVersionColumnName() );
		}
		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			delete.setComment( "delete " + getEntityName() );
		}
		return delete.toStatementString();
	}

	protected int dehydrate(
			Serializable id,
			Object[] fields,
			boolean[] includeProperty,
			boolean[][] includeColumns,
			int j,
			PreparedStatement st,
			SharedSessionContractImplementor session,
			boolean isUpdate) throws HibernateException, SQLException {
		return dehydrate( id, fields, null, includeProperty, includeColumns, j, st, session, 1, isUpdate );
	}

	/**
	 * Marshall the fields of a persistent instance to a prepared statement
	 */
	protected int dehydrate(
			final Serializable id,
			final Object[] fields,
			final Object rowId,
			final boolean[] includeProperty,
			final boolean[][] includeColumns,
			final int j,
			final PreparedStatement ps,
			final SharedSessionContractImplementor session,
			int index,
			boolean isUpdate) throws SQLException, HibernateException {

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Dehydrating entity: {0}", MessageHelper.infoString( this, id, getFactory() ) );
		}

		for ( int i = 0; i < entityMetamodel.getPropertySpan(); i++ ) {
			if ( includeProperty[i] && isPropertyOfTable( i, j )
					&& !lobProperties.contains( i ) ) {
				getPropertyTypes()[i].nullSafeSet( ps, fields[i], index, includeColumns[i], session );
				index += ArrayHelper.countTrue( includeColumns[i] ); //TODO:  this is kinda slow...
			}
		}

		if ( !isUpdate ) {
			index += dehydrateId( id, rowId, ps, session, index );
		}

		// HHH-4635
		// Oracle expects all Lob properties to be last in inserts
		// and updates.  Insert them at the end.
		for ( int i : lobProperties ) {
			if ( includeProperty[i] && isPropertyOfTable( i, j ) ) {
				getPropertyTypes()[i].nullSafeSet( ps, fields[i], index, includeColumns[i], session );
				index += ArrayHelper.countTrue( includeColumns[i] ); //TODO:  this is kinda slow...
			}
		}

		if ( isUpdate ) {
			index += dehydrateId( id, rowId, ps, session, index );
		}

		return index;

	}

	private int dehydrateId(
			final Serializable id,
			final Object rowId,
			final PreparedStatement ps,
			final SharedSessionContractImplementor session,
			int index) throws SQLException {
		if ( rowId != null ) {
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev(
					String.format(
						"binding parameter [%s] as ROWID - [%s]",
						index,
						rowId
					)
				);
			}

			ps.setObject( index, rowId );
			return 1;
		}
		else if ( id != null ) {
			getIdentifierType().nullSafeSet( ps, id, index, session );
			return getIdentifierColumnSpan();
		}
		return 0;
	}

	/**
	 * Unmarshall the fields of a persistent instance from a result set,
	 * without resolving associations or collections. Question: should
	 * this really be here, or should it be sent back to Loader?
	 */
	public Object[] hydrate(
			final ResultSet rs,
			final Serializable id,
			final Object object,
			final Loadable rootLoadable,
			final String[][] suffixedPropertyColumns,
			final boolean allProperties,
			final SharedSessionContractImplementor session) throws SQLException, HibernateException {

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Hydrating entity: {0}", MessageHelper.infoString( this, id, getFactory() ) );
		}

		final AbstractEntityPersister rootPersister = (AbstractEntityPersister) rootLoadable;

		final boolean hasDeferred = rootPersister.hasSequentialSelect();
		PreparedStatement sequentialSelect = null;
		ResultSet sequentialResultSet = null;
		boolean sequentialSelectEmpty = false;
		try {

			if ( hasDeferred ) {
				final String sql = rootPersister.getSequentialSelect( getEntityName() );
				if ( sql != null ) {
					//TODO: I am not so sure about the exception handling in this bit!
					sequentialSelect = session
							.getJdbcCoordinator()
							.getStatementPreparer()
							.prepareStatement( sql );
					rootPersister.getIdentifierType().nullSafeSet( sequentialSelect, id, 1, session );
					sequentialResultSet = session.getJdbcCoordinator().getResultSetReturn().extract( sequentialSelect );
					if ( !sequentialResultSet.next() ) {
						// TODO: Deal with the "optional" attribute in the <join> mapping;
						// this code assumes that optional defaults to "true" because it
						// doesn't actually seem to work in the fetch="join" code
						//
						// Note that actual proper handling of optional-ality here is actually
						// more involved than this patch assumes.  Remember that we might have
						// multiple <join/> mappings associated with a single entity.  Really
						// a couple of things need to happen to properly handle optional here:
						//  1) First and foremost, when handling multiple <join/>s, we really
						//      should be using the entity root table as the driving table;
						//      another option here would be to choose some non-optional joined
						//      table to use as the driving table.  In all likelihood, just using
						//      the root table is much simplier
						//  2) Need to add the FK columns corresponding to each joined table
						//      to the generated select list; these would then be used when
						//      iterating the result set to determine whether all non-optional
						//      data is present
						// My initial thoughts on the best way to deal with this would be
						// to introduce a new SequentialSelect abstraction that actually gets
						// generated in the persisters (ok, SingleTable...) and utilized here.
						// It would encapsulated all this required optional-ality checking...
						sequentialSelectEmpty = true;
					}
				}
			}

			final String[] propNames = getPropertyNames();
			final Type[] types = getPropertyTypes();
			final Object[] values = new Object[types.length];
			final boolean[] laziness = getPropertyLaziness();
			final String[] propSubclassNames = getSubclassPropertySubclassNameClosure();

			for ( int i = 0; i < types.length; i++ ) {
				if ( !propertySelectable[i] ) {
					values[i] = PropertyAccessStrategyBackRefImpl.UNKNOWN;
				}
				else if ( allProperties || !laziness[i] ) {
					//decide which ResultSet to get the property value from:
					final boolean propertyIsDeferred = hasDeferred &&
							rootPersister.isSubclassPropertyDeferred( propNames[i], propSubclassNames[i] );
					if ( propertyIsDeferred && sequentialSelectEmpty ) {
						values[i] = null;
					}
					else {
						final ResultSet propertyResultSet = propertyIsDeferred ? sequentialResultSet : rs;
						final String[] cols = propertyIsDeferred ?
								propertyColumnAliases[i] :
								suffixedPropertyColumns[i];
						values[i] = types[i].hydrate( propertyResultSet, cols, session, object );
					}
				}
				else {
					values[i] = LazyPropertyInitializer.UNFETCHED_PROPERTY;
				}
			}

			if ( sequentialResultSet != null ) {
				session.getJdbcCoordinator().getResourceRegistry().release( sequentialResultSet, sequentialSelect );
			}

			return values;

		}
		finally {
			if ( sequentialSelect != null ) {
				session.getJdbcCoordinator().getResourceRegistry().release( sequentialSelect );
				session.getJdbcCoordinator().afterStatementExecution();
			}
		}
	}

	protected boolean useInsertSelectIdentity() {
		return !useGetGeneratedKeys() && getFactory().getDialect().getIdentityColumnSupport().supportsInsertSelectIdentity();
	}

	protected boolean useGetGeneratedKeys() {
		return getFactory().getSessionFactoryOptions().isGetGeneratedKeysEnabled();
	}

	protected String getSequentialSelect(String entityName) {
		throw new UnsupportedOperationException( "no sequential selects" );
	}

	/**
	 * Perform an SQL INSERT, and then retrieve a generated identifier.
	 * <p/>
	 * This form is used for PostInsertIdentifierGenerator-style ids (IDENTITY,
	 * select, etc).
	 */
	protected Serializable insert(
			final Object[] fields,
			final boolean[] notNull,
			String sql,
			final Object object,
			final SharedSessionContractImplementor session) throws HibernateException {

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Inserting entity: {0} (native id)", getEntityName() );
			if ( isVersioned() ) {
				LOG.tracev( "Version: {0}", Versioning.getVersion( fields, this ) );
			}
		}

		Binder binder = new Binder() {
			public void bindValues(PreparedStatement ps) throws SQLException {
				dehydrate( null, fields, notNull, propertyColumnInsertable, 0, ps, session, false );
			}

			public Object getEntity() {
				return object;
			}
		};

		return identityDelegate.performInsert( sql, session, binder );
	}

	public String getIdentitySelectString() {
		//TODO: cache this in an instvar
		return getFactory().getDialect().getIdentityColumnSupport()
				.getIdentitySelectString(
						getTableName( 0 ),
						getKeyColumns( 0 )[0],
						getIdentifierType().sqlTypes( getFactory() )[0]
				);
	}

	public String getSelectByUniqueKeyString(String propertyName) {
		return new SimpleSelect( getFactory().getDialect() )
				.setTableName( getTableName( 0 ) )
				.addColumns( getKeyColumns( 0 ) )
				.addCondition( getPropertyColumnNames( propertyName ), "=?" )
				.toStatementString();
	}

	private BasicBatchKey inserBatchKey;

	/**
	 * Perform an SQL INSERT.
	 * <p/>
	 * This for is used for all non-root tables as well as the root table
	 * in cases where the identifier value is known before the insert occurs.
	 */
	protected void insert(
			final Serializable id,
			final Object[] fields,
			final boolean[] notNull,
			final int j,
			final String sql,
			final Object object,
			final SharedSessionContractImplementor session) throws HibernateException {

		if ( isInverseTable( j ) ) {
			return;
		}

		//note: it is conceptually possible that a UserType could map null to
		//	  a non-null value, so the following is arguable:
		if ( isNullableTable( j ) && isAllNull( fields, j ) ) {
			return;
		}

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Inserting entity: {0}", MessageHelper.infoString( this, id, getFactory() ) );
			if ( j == 0 && isVersioned() ) {
				LOG.tracev( "Version: {0}", Versioning.getVersion( fields, this ) );
			}
		}

		// TODO : shouldn't inserts be Expectations.NONE?
		final Expectation expectation = Expectations.appropriateExpectation( insertResultCheckStyles[j] );
		final int jdbcBatchSizeToUse = session.getConfiguredJdbcBatchSize();
		final boolean useBatch = expectation.canBeBatched() &&
						jdbcBatchSizeToUse > 1 &&
						getIdentifierGenerator().supportsJdbcBatchInserts();

		if ( useBatch && inserBatchKey == null ) {
			inserBatchKey = new BasicBatchKey(
					getEntityName() + "#INSERT",
					expectation
			);
		}
		final boolean callable = isInsertCallable( j );

		try {
			// Render the SQL query
			final PreparedStatement insert;
			if ( useBatch ) {
				insert = session
						.getJdbcCoordinator()
						.getBatch( inserBatchKey )
						.getBatchStatement( sql, callable );
			}
			else {
				insert = session
						.getJdbcCoordinator()
						.getStatementPreparer()
						.prepareStatement( sql, callable );
			}

			try {
				int index = 1;
				index += expectation.prepare( insert );

				// Write the values of fields onto the prepared statement - we MUST use the state at the time the
				// insert was issued (cos of foreign key constraints). Not necessarily the object's current state

				dehydrate( id, fields, null, notNull, propertyColumnInsertable, j, insert, session, index, false );

				if ( useBatch ) {
					session.getJdbcCoordinator().getBatch( inserBatchKey ).addToBatch();
				}
				else {
					expectation.verifyOutcome(
							session.getJdbcCoordinator()
									.getResultSetReturn()
									.executeUpdate( insert ), insert, -1
					);
				}
			}
			catch (SQLException | JDBCException e) {
				if ( useBatch ) {
					session.getJdbcCoordinator().abortBatch();
				}
				throw e;
			}
			finally {
				if ( !useBatch ) {
					session.getJdbcCoordinator().getResourceRegistry().release( insert );
					session.getJdbcCoordinator().afterStatementExecution();
				}
			}
		}
		catch (SQLException e) {
			throw getFactory().getSQLExceptionHelper().convert(
					e,
					"could not insert: " + MessageHelper.infoString( this ),
					sql
			);
		}

	}

	/**
	 * Perform an SQL UPDATE or SQL INSERT
	 */
	protected void updateOrInsert(
			final Serializable id,
			final Object[] fields,
			final Object[] oldFields,
			final Object rowId,
			final boolean[] includeProperty,
			final int j,
			final Object oldVersion,
			final Object object,
			final String sql,
			final SharedSessionContractImplementor session) throws HibernateException {

		if ( !isInverseTable( j ) ) {

			final boolean isRowToUpdate;
			if ( isNullableTable( j ) && oldFields != null && isAllNull( oldFields, j ) ) {
				//don't bother trying to update, we know there is no row there yet
				isRowToUpdate = false;
			}
			else if ( isNullableTable( j ) && isAllNull( fields, j ) ) {
				//if all fields are null, we might need to delete existing row
				isRowToUpdate = true;
				delete( id, oldVersion, j, object, getSQLDeleteStrings()[j], session, null );
			}
			else {
				//there is probably a row there, so try to update
				//if no rows were updated, we will find out
				isRowToUpdate = update(
						id,
						fields,
						oldFields,
						rowId,
						includeProperty,
						j,
						oldVersion,
						object,
						sql,
						session
				);
			}

			if ( !isRowToUpdate && !isAllNull( fields, j ) ) {
				// assume that the row was not there since it previously had only null
				// values, so do an INSERT instead
				//TODO: does not respect dynamic-insert
				insert( id, fields, getPropertyInsertability(), j, getSQLInsertStrings()[j], object, session );
			}

		}

	}

	private BasicBatchKey updateBatchKey;

	protected boolean update(
			final Serializable id,
			final Object[] fields,
			final Object[] oldFields,
			final Object rowId,
			final boolean[] includeProperty,
			final int j,
			final Object oldVersion,
			final Object object,
			final String sql,
			final SharedSessionContractImplementor session) throws HibernateException {

		final Expectation expectation = Expectations.appropriateExpectation( updateResultCheckStyles[j] );
		final int jdbcBatchSizeToUse = session.getConfiguredJdbcBatchSize();
		final boolean useBatch = expectation.canBeBatched() && isBatchable() && jdbcBatchSizeToUse > 1;
		if ( useBatch && updateBatchKey == null ) {
			updateBatchKey = new BasicBatchKey(
					getEntityName() + "#UPDATE",
					expectation
			);
		}
		final boolean callable = isUpdateCallable( j );
		final boolean useVersion = j == 0 && isVersioned();

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Updating entity: {0}", MessageHelper.infoString( this, id, getFactory() ) );
			if ( useVersion ) {
				LOG.tracev( "Existing version: {0} -> New version:{1}", oldVersion, fields[getVersionProperty()] );
			}
		}

		try {
			int index = 1; // starting index
			final PreparedStatement update;
			if ( useBatch ) {
				update = session
						.getJdbcCoordinator()
						.getBatch( updateBatchKey )
						.getBatchStatement( sql, callable );
			}
			else {
				update = session
						.getJdbcCoordinator()
						.getStatementPreparer()
						.prepareStatement( sql, callable );
			}

			try {
				index += expectation.prepare( update );

				//Now write the values of fields onto the prepared statement
				index = dehydrate(
						id,
						fields,
						rowId,
						includeProperty,
						propertyColumnUpdateable,
						j,
						update,
						session,
						index,
						true
				);

				// Write any appropriate versioning conditional parameters
				if ( useVersion && entityMetamodel.getOptimisticLockStyle() == OptimisticLockStyle.VERSION ) {
					if ( checkVersion( includeProperty ) ) {
						getVersionType().nullSafeSet( update, oldVersion, index, session );
					}
				}
				else if ( isAllOrDirtyOptLocking() && oldFields != null ) {
					boolean[] versionability = getPropertyVersionability(); //TODO: is this really necessary????
					boolean[] includeOldField = entityMetamodel.getOptimisticLockStyle() == OptimisticLockStyle.ALL
							? getPropertyUpdateability()
							: includeProperty;
					Type[] types = getPropertyTypes();
					for ( int i = 0; i < entityMetamodel.getPropertySpan(); i++ ) {
						boolean include = includeOldField[i] &&
								isPropertyOfTable( i, j ) &&
								versionability[i]; //TODO: is this really necessary????
						if ( include ) {
							boolean[] settable = types[i].toColumnNullness( oldFields[i], getFactory() );
							types[i].nullSafeSet(
									update,
									oldFields[i],
									index,
									settable,
									session
							);
							index += ArrayHelper.countTrue( settable );
						}
					}
				}

				if ( useBatch ) {
					session.getJdbcCoordinator().getBatch( updateBatchKey ).addToBatch();
					return true;
				}
				else {
					return check(
							session.getJdbcCoordinator().getResultSetReturn().executeUpdate( update ),
							id,
							j,
							expectation,
							update
					);
				}

			}
			catch (SQLException e) {
				if ( useBatch ) {
					session.getJdbcCoordinator().abortBatch();
				}
				throw e;
			}
			finally {
				if ( !useBatch ) {
					session.getJdbcCoordinator().getResourceRegistry().release( update );
					session.getJdbcCoordinator().afterStatementExecution();
				}
			}

		}
		catch (SQLException e) {
			throw getFactory().getSQLExceptionHelper().convert(
					e,
					"could not update: " + MessageHelper.infoString( this, id, getFactory() ),
					sql
			);
		}
	}

	private BasicBatchKey deleteBatchKey;

	/**
	 * Perform an SQL DELETE
	 */
	protected void delete(
			final Serializable id,
			final Object version,
			final int j,
			final Object object,
			final String sql,
			final SharedSessionContractImplementor session,
			final Object[] loadedState) throws HibernateException {

		if ( isInverseTable( j ) ) {
			return;
		}

		final boolean useVersion = j == 0 && isVersioned();
		final boolean callable = isDeleteCallable( j );
		final Expectation expectation = Expectations.appropriateExpectation( deleteResultCheckStyles[j] );
		final boolean useBatch = j == 0 && isBatchable() && expectation.canBeBatched();
		if ( useBatch && deleteBatchKey == null ) {
			deleteBatchKey = new BasicBatchKey(
					getEntityName() + "#DELETE",
					expectation
			);
		}

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Deleting entity: {0}", MessageHelper.infoString( this, id, getFactory() ) );
			if ( useVersion ) {
				LOG.tracev( "Version: {0}", version );
			}
		}

		if ( isTableCascadeDeleteEnabled( j ) ) {
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Delete handled by foreign key constraint: {0}", getTableName( j ) );
			}
			return; //EARLY EXIT!
		}

		try {
			//Render the SQL query
			PreparedStatement delete;
			int index = 1;
			if ( useBatch ) {
				delete = session
						.getJdbcCoordinator()
						.getBatch( deleteBatchKey )
						.getBatchStatement( sql, callable );
			}
			else {
				delete = session
						.getJdbcCoordinator()
						.getStatementPreparer()
						.prepareStatement( sql, callable );
			}

			try {

				index += expectation.prepare( delete );

				// Do the key. The key is immutable so we can use the _current_ object state - not necessarily
				// the state at the time the delete was issued
				getIdentifierType().nullSafeSet( delete, id, index, session );
				index += getIdentifierColumnSpan();

				// We should use the _current_ object state (ie. after any updates that occurred during flush)

				if ( useVersion ) {
					getVersionType().nullSafeSet( delete, version, index, session );
				}
				else if ( isAllOrDirtyOptLocking() && loadedState != null ) {
					boolean[] versionability = getPropertyVersionability();
					Type[] types = getPropertyTypes();
					for ( int i = 0; i < entityMetamodel.getPropertySpan(); i++ ) {
						if ( isPropertyOfTable( i, j ) && versionability[i] ) {
							// this property belongs to the table and it is not specifically
							// excluded from optimistic locking by optimistic-lock="false"
							boolean[] settable = types[i].toColumnNullness( loadedState[i], getFactory() );
							types[i].nullSafeSet( delete, loadedState[i], index, settable, session );
							index += ArrayHelper.countTrue( settable );
						}
					}
				}

				if ( useBatch ) {
					session.getJdbcCoordinator().getBatch( deleteBatchKey ).addToBatch();
				}
				else {
					check(
							session.getJdbcCoordinator().getResultSetReturn().executeUpdate( delete ),
							id,
							j,
							expectation,
							delete
					);
				}

			}
			catch (SQLException sqle) {
				if ( useBatch ) {
					session.getJdbcCoordinator().abortBatch();
				}
				throw sqle;
			}
			finally {
				if ( !useBatch ) {
					session.getJdbcCoordinator().getResourceRegistry().release( delete );
					session.getJdbcCoordinator().afterStatementExecution();
				}
			}

		}
		catch (SQLException sqle) {
			throw getFactory().getSQLExceptionHelper().convert(
					sqle,
					"could not delete: " +
							MessageHelper.infoString( this, id, getFactory() ),
					sql
			);

		}

	}

	private String[] getUpdateStrings(boolean byRowId, boolean lazy) {
		if ( byRowId ) {
			return lazy ? getSQLLazyUpdateByRowIdStrings() : getSQLUpdateByRowIdStrings();
		}
		else {
			return lazy ? getSQLLazyUpdateStrings() : getSQLUpdateStrings();
		}
	}

	/**
	 * Update an object
	 */
	public void update(
			final Serializable id,
			final Object[] fields,
			int[] dirtyFields,
			final boolean hasDirtyCollection,
			final Object[] oldFields,
			final Object oldVersion,
			final Object object,
			final Object rowId,
			final SharedSessionContractImplementor session) throws HibernateException {

		// apply any pre-update in-memory value generation
		if ( getEntityMetamodel().hasPreUpdateGeneratedValues() ) {
			final InMemoryValueGenerationStrategy[] valueGenerationStrategies = getEntityMetamodel().getInMemoryValueGenerationStrategies();
			int valueGenerationStrategiesSize = valueGenerationStrategies.length;
			if ( valueGenerationStrategiesSize != 0 ) {
				int[] fieldsPreUpdateNeeded = new int[valueGenerationStrategiesSize];
				int count = 0;
				for ( int i = 0; i < valueGenerationStrategiesSize; i++ ) {
					if ( valueGenerationStrategies[i] != null && valueGenerationStrategies[i].getGenerationTiming()
							.includesUpdate() ) {
						fields[i] = valueGenerationStrategies[i].getValueGenerator().generateValue(
								(Session) session,
								object
						);
						setPropertyValue( object, i, fields[i] );
						fieldsPreUpdateNeeded[count++] = i;
					}
				}
//				if ( fieldsPreUpdateNeeded.length != 0 ) {
//					if ( dirtyFields != null ) {
//						dirtyFields = ArrayHelper.join( fieldsPreUpdateNeeded, dirtyFields );
//					}
//					else if ( hasDirtyCollection ) {
//						dirtyFields = fieldsPreUpdateNeeded;
//					}
//					// no dirty fields and no dirty collections so no update needed ???
//				}
				if ( dirtyFields != null ) {
					dirtyFields = ArrayHelper.join( dirtyFields, ArrayHelper.trim( fieldsPreUpdateNeeded, count ) );
				}
			}
		}

		//note: dirtyFields==null means we had no snapshot, and we couldn't get one using select-before-update
		//	  oldFields==null just means we had no snapshot to begin with (we might have used select-before-update to get the dirtyFields)

		final boolean[] tableUpdateNeeded = getTableUpdateNeeded( dirtyFields, hasDirtyCollection );
		final int span = getTableSpan();

		final boolean[] propsToUpdate;
		final String[] updateStrings;
		EntityEntry entry = session.getPersistenceContextInternal().getEntry( object );

		// Ensure that an immutable or non-modifiable entity is not being updated unless it is
		// in the process of being deleted.
		if ( entry == null && !isMutable() ) {
			throw new IllegalStateException( "Updating immutable entity that is not in session yet!" );
		}
		if ( ( entityMetamodel.isDynamicUpdate() && dirtyFields != null ) ) {
			// We need to generate the UPDATE SQL when dynamic-update="true"
			propsToUpdate = getPropertiesToUpdate( dirtyFields, hasDirtyCollection );
			// don't need to check laziness (dirty checking algorithm handles that)
			updateStrings = new String[span];
			for ( int j = 0; j < span; j++ ) {
				updateStrings[j] = tableUpdateNeeded[j] ?
						generateUpdateString( propsToUpdate, j, oldFields, j == 0 && rowId != null ) :
						null;
			}
		}
		else if ( !isModifiableEntity( entry ) ) {
			// We need to generate UPDATE SQL when a non-modifiable entity (e.g., read-only or immutable)
			// needs:
			// - to have references to transient entities set to null before being deleted
			// - to have version incremented do to a "dirty" association
			// If dirtyFields == null, then that means that there are no dirty properties to
			// to be updated; an empty array for the dirty fields needs to be passed to
			// getPropertiesToUpdate() instead of null.
			propsToUpdate = getPropertiesToUpdate(
					( dirtyFields == null ? ArrayHelper.EMPTY_INT_ARRAY : dirtyFields ),
					hasDirtyCollection
			);
			// don't need to check laziness (dirty checking algorithm handles that)
			updateStrings = new String[span];
			for ( int j = 0; j < span; j++ ) {
				updateStrings[j] = tableUpdateNeeded[j] ?
						generateUpdateString( propsToUpdate, j, oldFields, j == 0 && rowId != null ) :
						null;
			}
		}
		else {
			// For the case of dynamic-update="false", or no snapshot, we use the static SQL
			updateStrings = getUpdateStrings(
					rowId != null,
					hasUninitializedLazyProperties( object )
			);
			propsToUpdate = getPropertyUpdateability( object );
		}

		for ( int j = 0; j < span; j++ ) {
			// Now update only the tables with dirty properties (and the table with the version number)
			if ( tableUpdateNeeded[j] ) {
				updateOrInsert(
						id,
						fields,
						oldFields,
						j == 0 ? rowId : null,
						propsToUpdate,
						j,
						oldVersion,
						object,
						updateStrings[j],
						session
				);
			}
		}
	}

	public Serializable insert(Object[] fields, Object object, SharedSessionContractImplementor session)
			throws HibernateException {
		// apply any pre-insert in-memory value generation
		preInsertInMemoryValueGeneration( fields, object, session );

		final int span = getTableSpan();
		final Serializable id;
		if ( entityMetamodel.isDynamicInsert() ) {
			// For the case of dynamic-insert="true", we need to generate the INSERT SQL
			boolean[] notNull = getPropertiesToInsert( fields );
			id = insert( fields, notNull, generateInsertString( true, notNull ), object, session );
			for ( int j = 1; j < span; j++ ) {
				insert( id, fields, notNull, j, generateInsertString( notNull, j ), object, session );
			}
		}
		else {
			// For the case of dynamic-insert="false", use the static SQL
			id = insert( fields, getPropertyInsertability(), getSQLIdentityInsertString(), object, session );
			for ( int j = 1; j < span; j++ ) {
				insert( id, fields, getPropertyInsertability(), j, getSQLInsertStrings()[j], object, session );
			}
		}
		return id;
	}

	public void insert(Serializable id, Object[] fields, Object object, SharedSessionContractImplementor session) {
		// apply any pre-insert in-memory value generation
		preInsertInMemoryValueGeneration( fields, object, session );

		final int span = getTableSpan();
		if ( entityMetamodel.isDynamicInsert() ) {
			// For the case of dynamic-insert="true", we need to generate the INSERT SQL
			boolean[] notNull = getPropertiesToInsert( fields );
			for ( int j = 0; j < span; j++ ) {
				insert( id, fields, notNull, j, generateInsertString( notNull, j ), object, session );
			}
		}
		else {
			// For the case of dynamic-insert="false", use the static SQL
			for ( int j = 0; j < span; j++ ) {
				insert( id, fields, getPropertyInsertability(), j, getSQLInsertStrings()[j], object, session );
			}
		}
	}

	private void preInsertInMemoryValueGeneration(Object[] fields, Object object, SharedSessionContractImplementor session) {
		if ( getEntityMetamodel().hasPreInsertGeneratedValues() ) {
			final InMemoryValueGenerationStrategy[] strategies = getEntityMetamodel().getInMemoryValueGenerationStrategies();
			for ( int i = 0; i < strategies.length; i++ ) {
				if ( strategies[i] != null && strategies[i].getGenerationTiming().includesInsert() ) {
					fields[i] = strategies[i].getValueGenerator().generateValue( (Session) session, object );
					setPropertyValue( object, i, fields[i] );
				}
			}
		}
	}

	/**
	 * Delete an object
	 */
	public void delete(Serializable id, Object version, Object object, SharedSessionContractImplementor session)
			throws HibernateException {
		final int span = getTableSpan();
		boolean isImpliedOptimisticLocking = !entityMetamodel.isVersioned() && isAllOrDirtyOptLocking();
		Object[] loadedState = null;
		if ( isImpliedOptimisticLocking ) {
			// need to treat this as if it where optimistic-lock="all" (dirty does *not* make sense);
			// first we need to locate the "loaded" state
			//
			// Note, it potentially could be a proxy, so doAfterTransactionCompletion the location the safe way...
			final EntityKey key = session.generateEntityKey( id, this );
			final PersistenceContext persistenceContext = session.getPersistenceContextInternal();
			Object entity = persistenceContext.getEntity( key );
			if ( entity != null ) {
				EntityEntry entry = persistenceContext.getEntry( entity );
				loadedState = entry.getLoadedState();
			}
		}

		final String[] deleteStrings;
		if ( isImpliedOptimisticLocking && loadedState != null ) {
			// we need to utilize dynamic delete statements
			deleteStrings = generateSQLDeletStrings( loadedState );
		}
		else {
			// otherwise, utilize the static delete statements
			deleteStrings = getSQLDeleteStrings();
		}

		for ( int j = span - 1; j >= 0; j-- ) {
			delete( id, version, j, object, deleteStrings[j], session, loadedState );
		}

	}

	private boolean isAllOrDirtyOptLocking() {
		return entityMetamodel.getOptimisticLockStyle() == OptimisticLockStyle.DIRTY
				|| entityMetamodel.getOptimisticLockStyle() == OptimisticLockStyle.ALL;
	}

	private String[] generateSQLDeletStrings(Object[] loadedState) {
		int span = getTableSpan();
		String[] deleteStrings = new String[span];
		for ( int j = span - 1; j >= 0; j-- ) {
			Delete delete = new Delete()
					.setTableName( getTableName( j ) )
					.addPrimaryKeyColumns( getKeyColumns( j ) );
			if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
				delete.setComment( "delete " + getEntityName() + " [" + j + "]" );
			}

			boolean[] versionability = getPropertyVersionability();
			Type[] types = getPropertyTypes();
			for ( int i = 0; i < entityMetamodel.getPropertySpan(); i++ ) {
				if ( isPropertyOfTable( i, j ) && versionability[i] ) {
					// this property belongs to the table and it is not specifically
					// excluded from optimistic locking by optimistic-lock="false"
					String[] propertyColumnNames = getPropertyColumnNames( i );
					boolean[] propertyNullness = types[i].toColumnNullness( loadedState[i], getFactory() );
					for ( int k = 0; k < propertyNullness.length; k++ ) {
						if ( propertyNullness[k] ) {
							delete.addWhereFragment( propertyColumnNames[k] + " = ?" );
						}
						else {
							delete.addWhereFragment( propertyColumnNames[k] + " is null" );
						}
					}
				}
			}
			deleteStrings[j] = delete.toStatementString();
		}
		return deleteStrings;
	}

	protected void logStaticSQL() {
		if ( LOG.isDebugEnabled() ) {
			LOG.debugf( "Static SQL for entity: %s", getEntityName() );
			for ( Map.Entry<String, String> entry : sqlLazySelectStringsByFetchGroup.entrySet() ) {
				LOG.debugf( " Lazy select (%s) : %s", entry.getKey(), entry.getValue() );
			}
			if ( sqlVersionSelectString != null ) {
				LOG.debugf( " Version select: %s", sqlVersionSelectString );
			}
			if ( sqlSnapshotSelectString != null ) {
				LOG.debugf( " Snapshot select: %s", sqlSnapshotSelectString );
			}
			for ( int j = 0; j < getTableSpan(); j++ ) {
				LOG.debugf( " Insert %s: %s", j, getSQLInsertStrings()[j] );
				LOG.debugf( " Update %s: %s", j, getSQLUpdateStrings()[j] );
				LOG.debugf( " Delete %s: %s", j, getSQLDeleteStrings()[j] );
			}
			if ( sqlIdentityInsertString != null ) {
				LOG.debugf( " Identity insert: %s", sqlIdentityInsertString );
			}
			if ( sqlUpdateByRowIdString != null ) {
				LOG.debugf( " Update by row id (all fields): %s", sqlUpdateByRowIdString );
			}
			if ( sqlLazyUpdateByRowIdString != null ) {
				LOG.debugf( " Update by row id (non-lazy fields): %s", sqlLazyUpdateByRowIdString );
			}
			if ( sqlInsertGeneratedValuesSelectString != null ) {
				LOG.debugf( " Insert-generated property select: %s", sqlInsertGeneratedValuesSelectString );
			}
			if ( sqlUpdateGeneratedValuesSelectString != null ) {
				LOG.debugf( " Update-generated property select: %s", sqlUpdateGeneratedValuesSelectString );
			}
		}
	}

	@Override
	public String filterFragment(String alias, Map enabledFilters) throws MappingException {
		final StringBuilder sessionFilterFragment = new StringBuilder();
		filterHelper.render( sessionFilterFragment, getFilterAliasGenerator( alias ), enabledFilters );
		return sessionFilterFragment.append( filterFragment( alias ) ).toString();
	}

	@Override
	public String filterFragment(String alias, Map enabledFilters, Set<String> treatAsDeclarations) {
		final StringBuilder sessionFilterFragment = new StringBuilder();
		filterHelper.render( sessionFilterFragment, getFilterAliasGenerator( alias ), enabledFilters );
		return sessionFilterFragment.append( filterFragment( alias, treatAsDeclarations ) ).toString();
	}

	public String generateFilterConditionAlias(String rootAlias) {
		return rootAlias;
	}

	public String oneToManyFilterFragment(String alias) throws MappingException {
		return "";
	}

	@Override
	public String oneToManyFilterFragment(String alias, Set<String> treatAsDeclarations) {
		return oneToManyFilterFragment( alias );
	}

	@Override
	public String fromJoinFragment(String alias, boolean innerJoin, boolean includeSubclasses) {
		// NOTE : Not calling createJoin here is just a performance optimization
		return getSubclassTableSpan() == 1
				? ""
				: createJoin(
				alias,
				innerJoin,
				includeSubclasses,
				Collections.emptySet(),
				null
		).toFromFragmentString();
	}

	@Override
	public String fromJoinFragment(
			String alias,
			boolean innerJoin,
			boolean includeSubclasses,
			Set<String> treatAsDeclarations) {
		// NOTE : Not calling createJoin here is just a performance optimization
		return getSubclassTableSpan() == 1
				? ""
				: createJoin( alias, innerJoin, includeSubclasses, treatAsDeclarations, null ).toFromFragmentString();
	}

	@Override
	public String fromJoinFragment(
			String alias,
			boolean innerJoin,
			boolean includeSubclasses,
			Set<String> treatAsDeclarations,
			Set<String> referencedTables) {
		return getSubclassTableSpan() == 1
				? ""
				: createJoin( alias, innerJoin, includeSubclasses, treatAsDeclarations, referencedTables ).toFromFragmentString();
	}

	@Override
	public String whereJoinFragment(String alias, boolean innerJoin, boolean includeSubclasses) {
		// NOTE : Not calling createJoin here is just a performance optimization
		return getSubclassTableSpan() == 1
				? ""
				: createJoin(
				alias,
				innerJoin,
				includeSubclasses,
				Collections.emptySet(),
				null
		).toWhereFragmentString();
	}

	@Override
	public String whereJoinFragment(
			String alias,
			boolean innerJoin,
			boolean includeSubclasses,
			Set<String> treatAsDeclarations) {
		// NOTE : Not calling createJoin here is just a performance optimization
		return getSubclassTableSpan() == 1
				? ""
				: createJoin( alias, innerJoin, includeSubclasses, treatAsDeclarations, null ).toWhereFragmentString();
	}

	protected boolean isSubclassTableLazy(int j) {
		return false;
	}

	protected JoinFragment createJoin(
			String name,
			boolean innerJoin,
			boolean includeSubclasses,
			Set<String> treatAsDeclarations) {
		return createJoin(name, innerJoin, includeSubclasses, treatAsDeclarations, null);
	}

	protected JoinFragment createJoin(
			String name,
			boolean innerJoin,
			boolean includeSubclasses,
			Set<String> treatAsDeclarations,
			Set<String> referencedTables) {
		// IMPL NOTE : all joins join to the pk of the driving table
		final String[] idCols = StringHelper.qualify( name, getIdentifierColumnNames() );
		final JoinFragment join = getFactory().getDialect().createOuterJoinFragment();
		final int tableSpan = getSubclassTableSpan();
		// IMPL NOTE : notice that we skip the first table; it is the driving table!
		for ( int j = 1; j < tableSpan; j++ ) {
			final JoinType joinType = determineSubclassTableJoinType(
					j,
					innerJoin,
					includeSubclasses,
					treatAsDeclarations,
					referencedTables
			);

			if ( joinType != null && joinType != JoinType.NONE ) {
				join.addJoin(
						getSubclassTableName( j ),
						generateTableAlias( name, j ),
						idCols,
						getSubclassTableKeyColumns( j ),
						joinType
				);
			}
		}
		return join;
	}

	protected JoinType determineSubclassTableJoinType(
			int subclassTableNumber,
			boolean canInnerJoin,
			boolean includeSubclasses,
			Set<String> treatAsDeclarations) {
		return determineSubclassTableJoinType(
				subclassTableNumber,
				canInnerJoin,
				includeSubclasses,
				treatAsDeclarations,
				null
		);
	}

	protected JoinType determineSubclassTableJoinType(
			int subclassTableNumber,
			boolean canInnerJoin,
			boolean includeSubclasses,
			Set<String> treatAsDeclarations,
			Set<String> referencedTables) {

		if ( isClassOrSuperclassTable( subclassTableNumber ) ) {
			String superclassTableName = getSubclassTableName( subclassTableNumber );
			if ( referencedTables != null && canOmitSuperclassTableJoin() && !referencedTables.contains(
					superclassTableName ) ) {
				return JoinType.NONE;
			}
			final boolean shouldInnerJoin = canInnerJoin
					&& !isInverseTable( subclassTableNumber )
					&& !isNullableTable( subclassTableNumber );
			// the table is either this persister's driving table or (one of) its super class persister's driving
			// tables which can be inner joined as long as the `shouldInnerJoin` condition resolves to true
			return shouldInnerJoin ? JoinType.INNER_JOIN : JoinType.LEFT_OUTER_JOIN;
		}

		// otherwise we have a subclass table and need to look a little deeper...

		// IMPL NOTE : By default includeSubclasses indicates that all subclasses should be joined and that each
		// subclass ought to be joined by outer-join.  However, TREAT-AS always requires that an inner-join be used
		// so we give TREAT-AS higher precedence...

		if ( isSubclassTableIndicatedByTreatAsDeclarations( subclassTableNumber, treatAsDeclarations ) ) {
			return JoinType.INNER_JOIN;
		}

		if ( includeSubclasses
				&& !isSubclassTableSequentialSelect( subclassTableNumber )
				&& !isSubclassTableLazy( subclassTableNumber ) ) {
			return JoinType.LEFT_OUTER_JOIN;
		}

		return JoinType.NONE;
	}

	protected boolean isSubclassTableIndicatedByTreatAsDeclarations(
			int subclassTableNumber,
			Set<String> treatAsDeclarations) {
		return false;
	}


	protected JoinFragment createJoin(int[] tableNumbers, String drivingAlias) {
		final String[] keyCols = StringHelper.qualify( drivingAlias, getSubclassTableKeyColumns( tableNumbers[0] ) );
		final JoinFragment jf = getFactory().getDialect().createOuterJoinFragment();
		// IMPL NOTE : notice that we skip the first table; it is the driving table!
		for ( int i = 1; i < tableNumbers.length; i++ ) {
			final int j = tableNumbers[i];
			jf.addJoin(
					getSubclassTableName( j ),
					generateTableAlias( getRootAlias(), j ),
					keyCols,
					getSubclassTableKeyColumns( j ),
					isInverseSubclassTable( j ) || isNullableSubclassTable( j )
							? JoinType.LEFT_OUTER_JOIN
							: JoinType.INNER_JOIN
			);
		}
		return jf;
	}

	protected SelectFragment createSelect(
			final int[] subclassColumnNumbers,
			final int[] subclassFormulaNumbers) {

		SelectFragment selectFragment = new SelectFragment();

		int[] columnTableNumbers = getSubclassColumnTableNumberClosure();
		String[] columnAliases = getSubclassColumnAliasClosure();
		String[] columnReaderTemplates = getSubclassColumnReaderTemplateClosure();
		for ( int i = 0; i < subclassColumnNumbers.length; i++ ) {
			int columnNumber = subclassColumnNumbers[i];
			if ( subclassColumnSelectableClosure[columnNumber] ) {
				final String subalias = generateTableAlias( getRootAlias(), columnTableNumbers[columnNumber] );
				selectFragment.addColumnTemplate(
						subalias,
						columnReaderTemplates[columnNumber],
						columnAliases[columnNumber]
				);
			}
		}

		int[] formulaTableNumbers = getSubclassFormulaTableNumberClosure();
		String[] formulaTemplates = getSubclassFormulaTemplateClosure();
		String[] formulaAliases = getSubclassFormulaAliasClosure();
		for ( int i = 0; i < subclassFormulaNumbers.length; i++ ) {
			int formulaNumber = subclassFormulaNumbers[i];
			final String subalias = generateTableAlias( getRootAlias(), formulaTableNumbers[formulaNumber] );
			selectFragment.addFormula( subalias, formulaTemplates[formulaNumber], formulaAliases[formulaNumber] );
		}

		return selectFragment;
	}

	protected String createFrom(int tableNumber, String alias) {
		return getSubclassTableName( tableNumber ) + ' ' + alias;
	}

	protected String createWhereByKey(int tableNumber, String alias) {
		//TODO: move to .sql package, and refactor with similar things!
		return String.join(
				"=? and ",
				StringHelper.qualify( alias, getSubclassTableKeyColumns( tableNumber ) )
		) + "=?";
	}

	protected String renderSelect(
			final int[] tableNumbers,
			final int[] columnNumbers,
			final int[] formulaNumbers) {

		Arrays.sort( tableNumbers ); //get 'em in the right order (not that it really matters)

		//render the where and from parts
		int drivingTable = tableNumbers[0];
		final String drivingAlias = generateTableAlias(
				getRootAlias(),
				drivingTable
		); //we *could* regerate this inside each called method!
		final String where = createWhereByKey( drivingTable, drivingAlias );
		final String from = createFrom( drivingTable, drivingAlias );

		//now render the joins
		JoinFragment jf = createJoin( tableNumbers, drivingAlias );

		//now render the select clause
		SelectFragment selectFragment = createSelect( columnNumbers, formulaNumbers );

		//now tie it all together
		Select select = new Select( getFactory().getDialect() );
		select.setSelectClause( selectFragment.toFragmentString().substring( 2 ) );
		select.setFromClause( from );
		select.setWhereClause( where );
		select.setOuterJoins( jf.toFromFragmentString(), jf.toWhereFragmentString() );
		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			select.setComment( "sequential select " + getEntityName() );
		}
		return select.toStatementString();
	}

	private String getRootAlias() {
		return StringHelper.generateAlias( getEntityName() );
	}

	/**
	 * Post-construct is a callback for AbstractEntityPersister subclasses to call after they are all done with their
	 * constructor processing.  It allows AbstractEntityPersister to extend its construction after all subclass-specific
	 * details have been handled.
	 *
	 * @param mapping The mapping
	 *
	 * @throws MappingException Indicates a problem accessing the Mapping
	 */
	protected void postConstruct(Mapping mapping) throws MappingException {
		initPropertyPaths( mapping );

		//doLateInit();
		prepareEntityIdentifierDefinition();
	}

	private void doLateInit() {
		//insert/update/delete SQL
		final int joinSpan = getTableSpan();
		sqlDeleteStrings = new String[joinSpan];
		sqlInsertStrings = new String[joinSpan];
		sqlUpdateStrings = new String[joinSpan];
		sqlLazyUpdateStrings = new String[joinSpan];

		sqlUpdateByRowIdString = rowIdName == null ?
				null :
				generateUpdateString( getPropertyUpdateability(), 0, true );
		sqlLazyUpdateByRowIdString = rowIdName == null ?
				null :
				generateUpdateString( getNonLazyPropertyUpdateability(), 0, true );

		for ( int j = 0; j < joinSpan; j++ ) {
			sqlInsertStrings[j] = customSQLInsert[j] == null ?
					generateInsertString( getPropertyInsertability(), j ) :
						substituteBrackets( customSQLInsert[j]);
			sqlUpdateStrings[j] = customSQLUpdate[j] == null ?
					generateUpdateString( getPropertyUpdateability(), j, false ) :
						substituteBrackets( customSQLUpdate[j]);
			sqlLazyUpdateStrings[j] = customSQLUpdate[j] == null ?
					generateUpdateString( getNonLazyPropertyUpdateability(), j, false ) :
						substituteBrackets( customSQLUpdate[j]);
			sqlDeleteStrings[j] = customSQLDelete[j] == null ?
					generateDeleteString( j ) :
						substituteBrackets( customSQLDelete[j]);
		}

		tableHasColumns = new boolean[joinSpan];
		for ( int j = 0; j < joinSpan; j++ ) {
			tableHasColumns[j] = sqlUpdateStrings[j] != null;
		}

		//select SQL
		sqlSnapshotSelectString = generateSnapshotSelectString();
		sqlLazySelectStringsByFetchGroup = generateLazySelectStringsByFetchGroup();
		sqlVersionSelectString = generateSelectVersionString();
		if ( hasInsertGeneratedProperties() ) {
			sqlInsertGeneratedValuesSelectString = generateInsertGeneratedValuesSelectString();
		}
		if ( hasUpdateGeneratedProperties() ) {
			sqlUpdateGeneratedValuesSelectString = generateUpdateGeneratedValuesSelectString();
		}
		if ( isIdentifierAssignedByInsert() ) {
			identityDelegate = ( (PostInsertIdentifierGenerator) getIdentifierGenerator() )
					.getInsertGeneratedIdentifierDelegate( this, getFactory().getDialect(), useGetGeneratedKeys() );
			sqlIdentityInsertString = customSQLInsert[0] == null
					? generateIdentityInsertString( getPropertyInsertability() )
					: substituteBrackets( customSQLInsert[0] );
		}
		else {
			sqlIdentityInsertString = null;
		}

		logStaticSQL();
	}

	private String substituteBrackets(String sql) {
		return new SubstituteBracketSQLQueryParser( sql, getFactory() ).process();
	}

	public final void postInstantiate() throws MappingException {
		doLateInit();

		prepareLoader( singleIdEntityLoader );
		prepareLoader( multiIdEntityLoader );
		prepareLoader( naturalIdLoader );

		// todo (6.0) : the init done in most of these is delayed now
		createLoaders();
		createUniqueKeyLoaders();

		doPostInstantiate();
	}

	private void prepareLoader(Loader loader) {
		if ( loader instanceof Preparable ) {
			( (Preparable) loader ).prepare();
		}
	}

	protected void doPostInstantiate() {
	}

	/**
	 * "Needed" by subclasses to override the createLoader strategy
	 *
	 * @deprecated Because there are better patterns for this
	 */
	@Deprecated
	protected Map getLoaders() {
		return loaders;
	}

	//Relational based Persisters should be content with this implementation
	protected void createLoaders() {
		// We load the entity loaders for the most common lock modes.

		noneLockLoader = createEntityLoader( LockMode.NONE );
		readLockLoader = createEntityLoader( LockMode.READ );


		// The loaders for the other lock modes are lazily loaded and will later be stored in this map,
		//		unless this setting is disabled
		if ( ! factory.getSessionFactoryOptions().isDelayBatchFetchLoaderCreationsEnabled() ) {
			for ( LockMode lockMode : EnumSet.complementOf( EnumSet.of( LockMode.NONE, LockMode.READ, LockMode.WRITE ) ) ) {
				loaders.put( lockMode, createEntityLoader( lockMode ) );
			}
		}


		// And finally, create the internal merge and refresh load plans

		loaders.put(
				"merge",
				new CascadeEntityLoader( this, CascadingActions.MERGE, getFactory() )
		);
		loaders.put(
				"refresh",
				new CascadeEntityLoader( this, CascadingActions.REFRESH, getFactory() )
		);
	}

	protected final UniqueEntityLoader getLoaderByLockMode(LockMode lockMode) {
		if ( LockMode.NONE == lockMode ) {
			return noneLockLoader;
		}
		else if ( LockMode.READ == lockMode ) {
			return readLockLoader;
		}

		return loaders.computeIfAbsent( lockMode, this::generateDelayedEntityLoader );
	}

	private UniqueEntityLoader generateDelayedEntityLoader(Object lockModeObject) {
		// Unfortunately, the loaders map mixes LockModes and Strings as keys so we need to accept an Object.
		// The cast is safe as we will always call this method with a LockMode.
		LockMode lockMode = (LockMode) lockModeObject;

		switch ( lockMode ) {
			case NONE:
			case READ:
			case OPTIMISTIC:
			case OPTIMISTIC_FORCE_INCREMENT: {
				return createEntityLoader( lockMode );
			}
			case UPGRADE:
			case UPGRADE_NOWAIT:
			case UPGRADE_SKIPLOCKED:
			case FORCE:
			case PESSIMISTIC_READ:
			case PESSIMISTIC_WRITE:
			case PESSIMISTIC_FORCE_INCREMENT: {
				//TODO: inexact, what we really need to know is: are any outer joins used?
				boolean disableForUpdate = getSubclassTableSpan() > 1
						&& hasSubclasses()
						&& !getFactory().getDialect().supportsOuterJoinForUpdate();

				return disableForUpdate ? readLockLoader : createEntityLoader( lockMode );
			}
			default: {
				throw new IllegalStateException( String.format( Locale.ROOT, "Lock mode %1$s not supported by entity loaders.", lockMode ) );
			}
		}
	}

	/**
	 * Load an instance using either the <tt>forUpdateLoader</tt> or the outer joining <tt>loader</tt>,
	 * depending upon the value of the <tt>lock</tt> parameter
	 */
	public Object load(Serializable id, Object optionalObject, LockMode lockMode, SharedSessionContractImplementor session) {
		return load( id, optionalObject, new LockOptions().setLockMode( lockMode ), session );
	}

	/**
	 * Load an instance using either the <tt>forUpdateLoader</tt> or the outer joining <tt>loader</tt>,
	 * depending upon the value of the <tt>lock</tt> parameter
	 */
	public Object load(Serializable id, Object optionalObject, LockOptions lockOptions, SharedSessionContractImplementor session)
			throws HibernateException {

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Fetching entity: {0}", MessageHelper.infoString( this, id, getFactory() ) );
		}

		return singleIdEntityLoader.load( id, lockOptions, session );
	}

	@Override
	public Object initializeEnhancedEntityUsedAsProxy(
			Object entity,
			String nameOfAttributeBeingAccessed,
			SharedSessionContractImplementor session) {
		final BytecodeEnhancementMetadata enhancementMetadata = getEntityMetamodel().getBytecodeEnhancementMetadata();
		final BytecodeLazyAttributeInterceptor currentInterceptor = enhancementMetadata.extractLazyInterceptor( entity );
		if ( currentInterceptor instanceof EnhancementAsProxyLazinessInterceptor ) {
			final EnhancementAsProxyLazinessInterceptor proxyInterceptor = (EnhancementAsProxyLazinessInterceptor) currentInterceptor;

			final EntityKey entityKey = proxyInterceptor.getEntityKey();
			final Serializable identifier = entityKey.getIdentifier();
			final Object loaded = readLockLoader.load(
					identifier,
					entity,
					session,
					LockOptions.READ
			);

			if ( loaded == null ) {
				final PersistenceContext persistenceContext = session.getPersistenceContext();
				persistenceContext.removeEntry( entity );
				persistenceContext.removeEntity( entityKey );
				session.getFactory().getEntityNotFoundDelegate().handleEntityNotFound(
						entityKey.getEntityName(),
						identifier
				);
			}

			final LazyAttributeLoadingInterceptor interceptor = enhancementMetadata.injectInterceptor(
					entity,
					identifier,
					session
			);

			final Object value;
			if ( nameOfAttributeBeingAccessed == null ) {
				return null;
			}
			else if ( interceptor.isAttributeLoaded( nameOfAttributeBeingAccessed ) ) {
				value = getEntityTuplizer().getPropertyValue( entity, nameOfAttributeBeingAccessed );
			}
			else {
				value = ( (LazyPropertyInitializer) this ).initializeLazyProperty( nameOfAttributeBeingAccessed, entity, session );
			}

			return interceptor.readObject(
					entity,
					nameOfAttributeBeingAccessed,
					value
			);
		}

		throw new IllegalStateException(  );
	}

	@Override
	public List multiLoad(Serializable[] ids, SharedSessionContractImplementor session, MultiLoadOptions loadOptions) {
		return multiIdEntityLoader.load( ids, loadOptions, session );
	}

	public void registerAffectingFetchProfile(String fetchProfileName) {
		affectingFetchProfileNames.add( fetchProfileName );
	}

	@Override
	public boolean isAffectedByEntityGraph(LoadQueryInfluencers loadQueryInfluencers) {
		return loadQueryInfluencers.getEffectiveEntityGraph().getGraph() != null;
	}

	@Override
	public boolean isAffectedByEnabledFetchProfiles(LoadQueryInfluencers loadQueryInfluencers) {
		for ( String s : loadQueryInfluencers.getEnabledFetchProfileNames() ) {
			if ( affectingFetchProfileNames.contains( s ) ) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isAffectedByEnabledFilters(LoadQueryInfluencers loadQueryInfluencers) {
		return loadQueryInfluencers.hasEnabledFilters()
				&& filterHelper.isAffectedBy( loadQueryInfluencers.getEnabledFilters() );
	}

	protected final boolean isAllNull(Object[] array, int tableNumber) {
		for ( int i = 0; i < array.length; i++ ) {
			if ( isPropertyOfTable( i, tableNumber ) && array[i] != null ) {
				return false;
			}
		}
		return true;
	}

	public boolean isSubclassPropertyNullable(int i) {
		return subclassPropertyNullabilityClosure[i];
	}

	/**
	 * Transform the array of property indexes to an array of booleans,
	 * true when the property is dirty
	 */
	protected final boolean[] getPropertiesToUpdate(final int[] dirtyProperties, final boolean hasDirtyCollection) {
		final boolean[] propsToUpdate = new boolean[entityMetamodel.getPropertySpan()];
		final boolean[] updateability = getPropertyUpdateability(); //no need to check laziness, dirty checking handles that
		for ( int j = 0; j < dirtyProperties.length; j++ ) {
			int property = dirtyProperties[j];
			if ( updateability[property] ) {
				propsToUpdate[property] = true;
			}
		}
		if ( isVersioned() && updateability[getVersionProperty()] ) {
			propsToUpdate[getVersionProperty()] =
					Versioning.isVersionIncrementRequired(
							dirtyProperties,
							hasDirtyCollection,
							getPropertyVersionability()
					);
		}
		return propsToUpdate;
	}

	/**
	 * Transform the array of property indexes to an array of booleans,
	 * true when the property is insertable and non-null
	 */
	protected boolean[] getPropertiesToInsert(Object[] fields) {
		boolean[] notNull = new boolean[fields.length];
		boolean[] insertable = getPropertyInsertability();
		for ( int i = 0; i < fields.length; i++ ) {
			notNull[i] = insertable[i] && fields[i] != null;
		}
		return notNull;
	}

	/**
	 * Locate the property-indices of all properties considered to be dirty.
	 *
	 * @param currentState The current state of the entity (the state to be checked).
	 * @param previousState The previous state of the entity (the state to be checked against).
	 * @param entity The entity for which we are checking state dirtiness.
	 * @param session The session in which the check is occurring.
	 *
	 * @return <tt>null</tt> or the indices of the dirty properties
	 *
	 * @throws HibernateException
	 */
	public int[] findDirty(Object[] currentState, Object[] previousState, Object entity, SharedSessionContractImplementor session)
			throws HibernateException {
		int[] props = TypeHelper.findDirty(
				entityMetamodel.getProperties(),
				currentState,
				previousState,
				propertyColumnUpdateable,
				session
		);
		if ( props == null ) {
			return null;
		}
		else {
			logDirtyProperties( props );
			return props;
		}
	}

	/**
	 * Locate the property-indices of all properties considered to be dirty.
	 *
	 * @param old The old state of the entity.
	 * @param current The current state of the entity.
	 * @param entity The entity for which we are checking state modification.
	 * @param session The session in which the check is occurring.
	 *
	 * @return <tt>null</tt> or the indices of the modified properties
	 *
	 * @throws HibernateException
	 */
	public int[] findModified(Object[] old, Object[] current, Object entity, SharedSessionContractImplementor session)
			throws HibernateException {
		int[] props = TypeHelper.findModified(
				entityMetamodel.getProperties(),
				current,
				old,
				propertyColumnUpdateable,
				getPropertyUpdateability(),
				session
		);
		if ( props == null ) {
			return null;
		}
		else {
			logDirtyProperties( props );
			return props;
		}
	}

	/**
	 * Which properties appear in the SQL update?
	 * (Initialized, updateable ones!)
	 */
	protected boolean[] getPropertyUpdateability(Object entity) {
		return hasUninitializedLazyProperties( entity )
				? getNonLazyPropertyUpdateability()
				: getPropertyUpdateability();
	}

	private void logDirtyProperties(int[] props) {
		if ( LOG.isTraceEnabled() ) {
			for ( int i = 0; i < props.length; i++ ) {
				String propertyName = entityMetamodel.getProperties()[props[i]].getName();
				LOG.trace( StringHelper.qualify( getEntityName(), propertyName ) + " is dirty" );
			}
		}
	}

	public SessionFactoryImplementor getFactory() {
		return factory;
	}

	public EntityMetamodel getEntityMetamodel() {
		return entityMetamodel;
	}

	@Override
	public boolean canReadFromCache() {
		return canReadFromCache;
	}

	@Override
	public boolean canWriteToCache() {
		return canWriteToCache;
	}

	public boolean hasCache() {
		return canWriteToCache;
	}

	public EntityDataAccess getCacheAccessStrategy() {
		return cacheAccessStrategy;
	}

	@Override
	public CacheEntryStructure getCacheEntryStructure() {
		return cacheEntryHelper.getCacheEntryStructure();
	}

	@Override
	public CacheEntry buildCacheEntry(Object entity, Object[] state, Object version, SharedSessionContractImplementor session) {
		return cacheEntryHelper.buildCacheEntry( entity, state, version, session );
	}

	public boolean hasNaturalIdCache() {
		return naturalIdRegionAccessStrategy != null;
	}

	public NaturalIdDataAccess getNaturalIdCacheAccessStrategy() {
		return naturalIdRegionAccessStrategy;
	}

	public Comparator getVersionComparator() {
		return isVersioned() ? getVersionType().getComparator() : null;
	}

	// temporary ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	public final String getEntityName() {
		return entityMetamodel.getName();
	}

	public EntityType getEntityType() {
		return entityMetamodel.getEntityType();
	}

	public boolean isPolymorphic() {
		return entityMetamodel.isPolymorphic();
	}

	public boolean isInherited() {
		return entityMetamodel.isInherited();
	}

	public boolean hasCascades() {
		return entityMetamodel.hasCascades();
	}

	public boolean hasIdentifierProperty() {
		return !entityMetamodel.getIdentifierProperty().isVirtual();
	}

	public VersionType getVersionType() {
		return (VersionType) locateVersionType();
	}

	private Type locateVersionType() {
		return entityMetamodel.getVersionProperty() == null ?
				null :
				entityMetamodel.getVersionProperty().getType();
	}

	public int getVersionProperty() {
		return entityMetamodel.getVersionPropertyIndex();
	}

	public boolean isVersioned() {
		return entityMetamodel.isVersioned();
	}

	public boolean isIdentifierAssignedByInsert() {
		return entityMetamodel.getIdentifierProperty().isIdentifierAssignedByInsert();
	}

	public boolean hasLazyProperties() {
		return entityMetamodel.hasLazyProperties();
	}

//	public boolean hasUninitializedLazyProperties(Object entity) {
//		if ( hasLazyProperties() ) {
//			InterceptFieldCallback callback = ( ( InterceptFieldEnabled ) entity ).getInterceptFieldCallback();
//			return callback != null && !( ( FieldInterceptor ) callback ).isInitialized();
//		}
//		else {
//			return false;
//		}
//	}

	public void afterReassociate(Object entity, SharedSessionContractImplementor session) {
		if ( getEntityMetamodel().getBytecodeEnhancementMetadata().isEnhancedForLazyLoading() ) {
			final BytecodeLazyAttributeInterceptor interceptor = getEntityMetamodel().getBytecodeEnhancementMetadata()
					.extractLazyInterceptor( entity );
			if ( interceptor == null ) {
				getEntityMetamodel().getBytecodeEnhancementMetadata().injectInterceptor(
						entity,
						getIdentifier( entity, session ),
						session
				);
			}
			else {
				interceptor.setSession( session );
			}
		}

		handleNaturalIdReattachment( entity, session );
	}

	private void handleNaturalIdReattachment(Object entity, SharedSessionContractImplementor session) {
		if ( !hasNaturalIdentifier() ) {
			return;
		}

		if ( getEntityMetamodel().hasImmutableNaturalId() ) {
			// we assume there were no changes to natural id during detachment for now, that is validated later
			// during flush.
			return;
		}

		final PersistenceContext persistenceContext = session.getPersistenceContextInternal();
		final NaturalIdHelper naturalIdHelper = persistenceContext.getNaturalIdHelper();
		final Serializable id = getIdentifier( entity, session );

		// for reattachment of mutable natural-ids, we absolutely positively have to grab the snapshot from the
		// database, because we have no other way to know if the state changed while detached.
		final Object[] naturalIdSnapshot;
		final Object[] entitySnapshot = persistenceContext.getDatabaseSnapshot( id, this );
		if ( entitySnapshot == StatefulPersistenceContext.NO_ROW ) {
			naturalIdSnapshot = null;
		}
		else {
			naturalIdSnapshot = naturalIdHelper.extractNaturalIdValues( entitySnapshot, this );
		}

		naturalIdHelper.removeSharedNaturalIdCrossReference( this, id, naturalIdSnapshot );
		naturalIdHelper.manageLocalNaturalIdCrossReference(
				this,
				id,
				naturalIdHelper.extractNaturalIdValues( entity, this ),
				naturalIdSnapshot,
				CachedNaturalIdValueSource.UPDATE
		);
	}

	public Boolean isTransient(Object entity, SharedSessionContractImplementor session) throws HibernateException {
		final Serializable id;
		if ( canExtractIdOutOfEntity() ) {
			id = getIdentifier( entity, session );
		}
		else {
			id = null;
		}
		// we *always* assume an instance with a null
		// identifier or no identifier property is unsaved!
		if ( id == null ) {
			return Boolean.TRUE;
		}

		// check the version unsaved-value, if appropriate
		final Object version = getVersion( entity );
		if ( isVersioned() ) {
			// let this take precedence if defined, since it works for
			// assigned identifiers
			Boolean result = entityMetamodel.getVersionProperty()
					.getUnsavedValue().isUnsaved( version );
			if ( result != null ) {
				return result;
			}
		}

		// check the id unsaved-value
		Boolean result = entityMetamodel.getIdentifierProperty()
				.getUnsavedValue().isUnsaved( id );
		if ( result != null ) {
			return result;
		}

		// check to see if it is in the second-level cache
		if ( session.getCacheMode().isGetEnabled() && canReadFromCache() ) {
			final EntityDataAccess cache = getCacheAccessStrategy();
			final Object ck = cache.generateCacheKey( id, this, session.getFactory(), session.getTenantIdentifier() );
			final Object ce = CacheHelper.fromSharedCache( session, ck, getCacheAccessStrategy() );
			if ( ce != null ) {
				return Boolean.FALSE;
			}
		}

		return null;
	}

	public boolean hasCollections() {
		return entityMetamodel.hasCollections();
	}

	public boolean hasMutableProperties() {
		return entityMetamodel.hasMutableProperties();
	}

	public boolean isMutable() {
		return entityMetamodel.isMutable();
	}

	protected final boolean isModifiableEntity(EntityEntry entry) {
		return ( entry == null ? isMutable() : entry.isModifiableEntity() );
	}

	public boolean isAbstract() {
		return entityMetamodel.isAbstract();
	}

	public boolean hasSubclasses() {
		return entityMetamodel.hasSubclasses();
	}

	public boolean hasProxy() {
		// skip proxy instantiation if entity is bytecode enhanced
		return entityMetamodel.isLazy() && !entityMetamodel.getBytecodeEnhancementMetadata().isEnhancedForLazyLoading();
	}

	public IdentifierGenerator getIdentifierGenerator() throws HibernateException {
		return entityMetamodel.getIdentifierProperty().getIdentifierGenerator();
	}

	public String getRootEntityName() {
		return entityMetamodel.getRootName();
	}

	public ClassMetadata getClassMetadata() {
		return this;
	}

	public String getMappedSuperclass() {
		return entityMetamodel.getSuperclass();
	}

	public boolean isExplicitPolymorphism() {
		return entityMetamodel.isExplicitPolymorphism();
	}

	protected boolean useDynamicUpdate() {
		return entityMetamodel.isDynamicUpdate();
	}

	protected boolean useDynamicInsert() {
		return entityMetamodel.isDynamicInsert();
	}

	protected boolean hasEmbeddedCompositeIdentifier() {
		return entityMetamodel.getIdentifierProperty().isEmbedded();
	}

	public boolean canExtractIdOutOfEntity() {
		return hasIdentifierProperty() || hasEmbeddedCompositeIdentifier() || hasIdentifierMapper();
	}

	private boolean hasIdentifierMapper() {
		return entityMetamodel.getIdentifierProperty().hasIdentifierMapper();
	}

	public String[] getKeyColumnNames() {
		return getIdentifierColumnNames();
	}

	public String getName() {
		return getEntityName();
	}

	public boolean isCollection() {
		return false;
	}

	public boolean consumesEntityAlias() {
		return true;
	}

	public boolean consumesCollectionAlias() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Warning:
	 * When there are duplicated property names in the subclasses
	 * then this method may return the wrong results.
	 * To ensure correct results, this method should only be used when
	 * {@literal this} is the concrete EntityPersister (since the
	 * concrete EntityPersister cannot have duplicated property names).
	 */
	@Override
	public Type getPropertyType(String propertyName) throws MappingException {
		return propertyMapping.toType( propertyName );
	}

	public Type getType() {
		return entityMetamodel.getEntityType();
	}

	public boolean isSelectBeforeUpdateRequired() {
		return entityMetamodel.isSelectBeforeUpdate();
	}

	protected final OptimisticLockStyle optimisticLockStyle() {
		return entityMetamodel.getOptimisticLockStyle();
	}

	public Object createProxy(Serializable id, SharedSessionContractImplementor session) throws HibernateException {
		return representationStrategy.getProxyFactory().getProxy( id, session );
	}

	public String toString() {
		return StringHelper.unqualify( getClass().getName() ) +
				'(' + entityMetamodel.getName() + ')';
	}

	public final String selectFragment(
			Joinable rhs,
			String rhsAlias,
			String lhsAlias,
			String entitySuffix,
			String collectionSuffix,
			boolean includeCollectionColumns) {
		return selectFragment( lhsAlias, entitySuffix );
	}

	public boolean isInstrumented() {
		return entityMetamodel.getBytecodeEnhancementMetadata().isEnhancedForLazyLoading();
	}

	public boolean hasInsertGeneratedProperties() {
		return entityMetamodel.hasInsertGeneratedValues();
	}

	public boolean hasUpdateGeneratedProperties() {
		return entityMetamodel.hasUpdateGeneratedValues();
	}

	public boolean isVersionPropertyGenerated() {
		return isVersioned() && getEntityMetamodel().isVersionGenerated();
	}

	public boolean isVersionPropertyInsertable() {
		return isVersioned() && getPropertyInsertability()[getVersionProperty()];
	}

	@Override
	public void afterInitialize(Object entity, SharedSessionContractImplementor session) {
		if ( entity instanceof PersistentAttributeInterceptable && getRepresentationStrategy().getMode() == RepresentationMode.POJO ) {
			final BytecodeLazyAttributeInterceptor interceptor = getEntityMetamodel().getBytecodeEnhancementMetadata()
					.extractLazyInterceptor( entity );
			if ( interceptor == null || interceptor instanceof EnhancementAsProxyLazinessInterceptor ) {
				getEntityMetamodel().getBytecodeEnhancementMetadata().injectInterceptor(
						entity,
						getIdentifier( entity, session ),
						session
				);
			}
			else {
				if ( interceptor.getLinkedSession() == null ) {
					interceptor.setSession( session );
				}
			}
		}

		// clear the fields that are marked as dirty in the dirtyness tracker
		if ( entity instanceof SelfDirtinessTracker ) {
			( (SelfDirtinessTracker) entity ).$$_hibernate_clearDirtyAttributes();
		}
	}

	public String[] getPropertyNames() {
		return entityMetamodel.getPropertyNames();
	}

	public Type[] getPropertyTypes() {
		return entityMetamodel.getPropertyTypes();
	}

	public boolean[] getPropertyLaziness() {
		return entityMetamodel.getPropertyLaziness();
	}

	public boolean[] getPropertyUpdateability() {
		return entityMetamodel.getPropertyUpdateability();
	}

	public boolean[] getPropertyCheckability() {
		return entityMetamodel.getPropertyCheckability();
	}

	public boolean[] getNonLazyPropertyUpdateability() {
		return entityMetamodel.getNonlazyPropertyUpdateability();
	}

	public boolean[] getPropertyInsertability() {
		return entityMetamodel.getPropertyInsertability();
	}

	/**
	 * @deprecated no simple, direct replacement
	 */
	@Deprecated
	public ValueInclusion[] getPropertyInsertGenerationInclusions() {
		return null;
	}

	/**
	 * @deprecated no simple, direct replacement
	 */
	@Deprecated
	public ValueInclusion[] getPropertyUpdateGenerationInclusions() {
		return null;
	}

	public boolean[] getPropertyNullability() {
		return entityMetamodel.getPropertyNullability();
	}

	public boolean[] getPropertyVersionability() {
		return entityMetamodel.getPropertyVersionability();
	}

	public CascadeStyle[] getPropertyCascadeStyles() {
		return entityMetamodel.getCascadeStyles();
	}

	public final Class getMappedClass() {
		return getMappedJavaTypeDescriptor().getJavaType();
	}

	public boolean implementsLifecycle() {
		return Lifecycle.class.isAssignableFrom( getMappedClass() );
	}

	public Class getConcreteProxyClass() {
		final JavaTypeDescriptor<?> proxyJavaTypeDescriptor = getRepresentationStrategy().getProxyJavaTypeDescriptor();
		return proxyJavaTypeDescriptor != null ? proxyJavaTypeDescriptor.getJavaType() : javaTypeDescriptor.getJavaType();
	}

	public void setPropertyValues(Object object, Object[] values) {
		if ( accessOptimizer != null ) {
			accessOptimizer.setPropertyValues( object, values );
		}
		else {
			int i = 0;
			for ( Map.Entry<String, AttributeMapping> entries : declaredAttributeMappings.entrySet() ) {
				AttributeMapping attributeMapping = entries.getValue();
				final Setter setter = attributeMapping
						.getPropertyAccess()
						.getSetter();
				setter.set( object, values[i], getFactory() );
				i++;
			}
		}
	}

	public void setPropertyValue(Object object, int i, Object value) {
		final String propertyName = getPropertyNames()[i];

		final AttributeMapping attributeMapping = (AttributeMapping) findSubPart( propertyName, this );
		final AttributeMetadata attributeMetadata = attributeMapping.getAttributeMetadataAccess().resolveAttributeMetadata( this );
		attributeMetadata.getPropertyAccess().getSetter().set( object, value, getFactory() );
	}

	public Object[] getPropertyValues(Object object) {
		if ( accessOptimizer != null ) {
			return accessOptimizer.getPropertyValues( object );
		}
		else {
			final List<Object> values = new ArrayList<>();

			visitAttributeMappings(
					attributeMapping -> {
						final AttributeMetadata attributeMetadata = attributeMapping.getAttributeMetadataAccess()
								.resolveAttributeMetadata( this );
						values.add( attributeMetadata.getPropertyAccess().getGetter().get( object ) );
					}
			);
			return values.toArray();
		}
	}

	@Override
	public Object getPropertyValue(Object object, int i) {
		return getEntityTuplizer().getPropertyValue( object, i );
	}

	@Override
	public Object getPropertyValue(Object object, String propertyName) {
		return getEntityTuplizer().getPropertyValue( object, propertyName );
	}

	@Override
	public Serializable getIdentifier(Object object) {
		return getIdentifier( object, null );
	}

	@Override
	public Serializable getIdentifier(Object entity, SharedSessionContractImplementor session) {
		return (Serializable) identifierMapping.getPropertyAccess().getGetter().get( entity );
	}

	@Override
	public void setIdentifier(Object entity, Serializable id, SharedSessionContractImplementor session) {
		identifierMapping.getPropertyAccess().getSetter().set( entity, id, factory );
	}

	@Override
	public Object getVersion(Object object) {
		if ( getVersionMapping() == null ) {
			return null;
		}
		final StateArrayContributorMetadata attrMetadata = getVersionMapping().getAttributeMetadataAccess().resolveAttributeMetadata( this );
		assert attrMetadata != null;
		return attrMetadata.getPropertyAccess().getGetter().get( object );
	}

	@Override
	public Object instantiate(Serializable id, SharedSessionContractImplementor session) {
		Object instance = getRepresentationStrategy().getInstantiator().instantiate( session );
		identifierMapping.getPropertyAccess().getSetter().set( instance, id, factory );

		return instance;
	}

	@Override
	public boolean isInstance(Object object) {
		return getRepresentationStrategy().getInstantiator().isInstance( object, getFactory() );
	}

	@Override
	public boolean hasUninitializedLazyProperties(Object object) {
		return entityMetamodel.getBytecodeEnhancementMetadata().hasUnFetchedAttributes( object );
	}

	@Override
	public void resetIdentifier(
			Object entity,
			Serializable currentId,
			Object currentVersion,
			SharedSessionContractImplementor session) {
		getEntityTuplizer().resetIdentifier( entity, currentId, currentVersion, session );
	}

	@Override
	public EntityPersister getSubclassEntityPersister(Object instance, SessionFactoryImplementor factory) {
		if ( !hasSubclasses() ) {
			return this;
		}
		else {
			final String concreteEntityName = getEntityTuplizer().determineConcreteSubclassEntityName(
					instance,
					factory
			);
			if ( concreteEntityName == null || getEntityName().equals( concreteEntityName ) ) {
				// the contract of EntityTuplizer.determineConcreteSubclassEntityName says that returning null
				// is an indication that the specified entity-name (this.getEntityName) should be used.
				return this;
			}
			else {
				return factory.getEntityPersister( concreteEntityName );
			}
		}
	}

	public boolean isMultiTable() {
		return false;
	}

	protected int getPropertySpan() {
		return entityMetamodel.getPropertySpan();
	}

	public Object[] getPropertyValuesToInsert(Object entity, Map mergeMap, SharedSessionContractImplementor session)
			throws HibernateException {
		if ( shouldGetAllProperties( entity ) && accessOptimizer != null ) {
			return accessOptimizer.getPropertyValues( entity );
		}

		final Object[] result = new Object[declaredAttributeMappings.size()];
		int i = 0;
		for ( Map.Entry<String, AttributeMapping> entries : declaredAttributeMappings.entrySet() ) {
			AttributeMapping attributeMapping = entries.getValue();
			result[i] = attributeMapping.getPropertyAccess().getGetter().getForInsert(
					entity,
					mergeMap,
					session
			);
			i++;
		}
		return result;
	}

	protected boolean shouldGetAllProperties(Object entity) {
		final BytecodeEnhancementMetadata bytecodeEnhancementMetadata = getEntityMetamodel().getBytecodeEnhancementMetadata();
		if ( !bytecodeEnhancementMetadata.isEnhancedForLazyLoading() ) {
			return true;
		}

		return !bytecodeEnhancementMetadata.hasUnFetchedAttributes( entity );
	}

	public void processInsertGeneratedProperties(
			Serializable id,
			Object entity,
			Object[] state,
			SharedSessionContractImplementor session) {
		if ( !hasInsertGeneratedProperties() ) {
			throw new AssertionFailure( "no insert-generated properties" );
		}
		processGeneratedProperties(
				id,
				entity,
				state,
				session,
				sqlInsertGeneratedValuesSelectString,
				GenerationTiming.INSERT
		);
	}

	public void processUpdateGeneratedProperties(
			Serializable id,
			Object entity,
			Object[] state,
			SharedSessionContractImplementor session) {
		if ( !hasUpdateGeneratedProperties() ) {
			throw new AssertionFailure( "no update-generated properties" );
		}
		processGeneratedProperties(
				id,
				entity,
				state,
				session,
				sqlUpdateGeneratedValuesSelectString,
				GenerationTiming.ALWAYS
		);
	}

	private void processGeneratedProperties(
			Serializable id,
			Object entity,
			Object[] state,
			SharedSessionContractImplementor session,
			String selectionSQL,
			GenerationTiming matchTiming) {
		// force immediate execution of the insert batch (if one)
		session.getJdbcCoordinator().executeBatch();

		try {
			PreparedStatement ps = session
					.getJdbcCoordinator()
					.getStatementPreparer()
					.prepareStatement( selectionSQL );
			try {
				getIdentifierType().nullSafeSet( ps, id, 1, session );
				ResultSet rs = session.getJdbcCoordinator().getResultSetReturn().extract( ps );
				try {
					if ( !rs.next() ) {
						throw new HibernateException(
								"Unable to locate row for retrieval of generated properties: " +
										MessageHelper.infoString( this, id, getFactory() )
						);
					}
					int propertyIndex = -1;
					for ( NonIdentifierAttribute attribute : entityMetamodel.getProperties() ) {
						propertyIndex++;
						if ( isValueGenerationRequired( attribute, matchTiming ) ) {
							final Object hydratedState = attribute.getType().hydrate(
									rs, getPropertyAliases(
											"",
											propertyIndex
									), session, entity
							);
							state[propertyIndex] = attribute.getType().resolve( hydratedState, session, entity );
							setPropertyValue( entity, propertyIndex, state[propertyIndex] );
						}
					}

//					for ( int i = 0; i < getPropertySpan(); i++ ) {
//						if ( includeds[i] != ValueInclusion.NONE ) {
//							Object hydratedState = getPropertyTypes()[i].hydrate( rs, getPropertyAliases( "", i ), session, entity );
//							state[i] = getPropertyTypes()[i].resolve( hydratedState, session, entity );
//							setPropertyValue( entity, i, state[i] );
//						}
//					}
				}
				finally {
					if ( rs != null ) {
						session.getJdbcCoordinator().getResourceRegistry().release( rs, ps );
					}
				}
			}
			finally {
				session.getJdbcCoordinator().getResourceRegistry().release( ps );
				session.getJdbcCoordinator().afterStatementExecution();
			}
		}
		catch (SQLException e) {
			throw getFactory().getSQLExceptionHelper().convert(
					e,
					"unable to select generated column values",
					selectionSQL
			);
		}

	}

	private boolean isValueGenerationRequired(NonIdentifierAttribute attribute, GenerationTiming matchTiming) {
		if ( attribute.getType() instanceof ComponentType ) {
			final ComponentType type = (ComponentType) attribute.getType();
			final ValueGeneration[] propertyValueGenerationStrategies = type.getPropertyValueGenerationStrategies();
			for ( ValueGeneration propertyValueGenerationStrategie : propertyValueGenerationStrategies ) {
				if ( isReadRequired( propertyValueGenerationStrategie, matchTiming ) ) {
					return true;
				}
			}
			return false;
		}
		else {
			return isReadRequired( attribute.getValueGenerationStrategy(), matchTiming );
		}
	}

	/**
	 * Whether the given value generation strategy requires to read the value from the database or not.
	 */
	private boolean isReadRequired(ValueGeneration valueGeneration, GenerationTiming matchTiming) {
		return valueGeneration != null &&
				valueGeneration.getValueGenerator() == null &&
				timingsMatch( valueGeneration.getGenerationTiming(), matchTiming );
	}

	private boolean timingsMatch(GenerationTiming timing, GenerationTiming matchTiming) {
		return
				( matchTiming == GenerationTiming.INSERT && timing.includesInsert() ) ||
						( matchTiming == GenerationTiming.ALWAYS && timing.includesUpdate() );
	}

	public String getIdentifierPropertyName() {
		return entityMetamodel.getIdentifierProperty().getName();
	}

	public Type getIdentifierType() {
		return entityMetamodel.getIdentifierProperty().getType();
	}

	public boolean hasSubselectLoadableCollections() {
		return hasSubselectLoadableCollections;
	}

	public int[] getNaturalIdentifierProperties() {
		return entityMetamodel.getNaturalIdentifierProperties();
	}

	public Object[] getNaturalIdentifierSnapshot(Serializable id, SharedSessionContractImplementor session)
			throws HibernateException {
		if ( !hasNaturalIdentifier() ) {
			throw new MappingException(
					"persistent class did not define a natural-id : " + MessageHelper.infoString(
							this
					)
			);
		}
		if ( LOG.isTraceEnabled() ) {
			LOG.tracev(
					"Getting current natural-id snapshot state for: {0}",
					MessageHelper.infoString( this, id, getFactory() )
			);
		}

		int[] naturalIdPropertyIndexes = getNaturalIdentifierProperties();
		int naturalIdPropertyCount = naturalIdPropertyIndexes.length;
		boolean[] naturalIdMarkers = new boolean[getPropertySpan()];
		Type[] extractionTypes = new Type[naturalIdPropertyCount];
		for ( int i = 0; i < naturalIdPropertyCount; i++ ) {
			extractionTypes[i] = getPropertyTypes()[naturalIdPropertyIndexes[i]];
			naturalIdMarkers[naturalIdPropertyIndexes[i]] = true;
		}

		///////////////////////////////////////////////////////////////////////
		// TODO : look at perhaps caching this...
		Select select = new Select( getFactory().getDialect() );
		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			select.setComment( "get current natural-id state " + getEntityName() );
		}
		select.setSelectClause( concretePropertySelectFragmentSansLeadingComma( getRootAlias(), naturalIdMarkers ) );
		select.setFromClause( fromTableFragment( getRootAlias() ) + fromJoinFragment( getRootAlias(), true, false ) );

		String[] aliasedIdColumns = StringHelper.qualify( getRootAlias(), getIdentifierColumnNames() );
		String whereClause = new StringBuilder()
				.append(
						String.join(
								"=? and ",
								aliasedIdColumns
						)
				)
				.append( "=?" )
				.append( whereJoinFragment( getRootAlias(), true, false ) )
				.toString();

		String sql = select.setOuterJoins( "", "" )
				.setWhereClause( whereClause )
				.toStatementString();
		///////////////////////////////////////////////////////////////////////

		Object[] snapshot = new Object[naturalIdPropertyCount];
		try {
			final JdbcCoordinator jdbcCoordinator = session.getJdbcCoordinator();
			PreparedStatement ps = jdbcCoordinator
					.getStatementPreparer()
					.prepareStatement( sql );
			try {
				getIdentifierType().nullSafeSet( ps, id, 1, session );
				ResultSet rs = jdbcCoordinator.getResultSetReturn().extract( ps );
				try {
					//if there is no resulting row, return null
					if ( !rs.next() ) {
						return null;
					}
					final EntityKey key = session.generateEntityKey( id, this );
					Object owner = session.getPersistenceContextInternal().getEntity( key );
					for ( int i = 0; i < naturalIdPropertyCount; i++ ) {
						snapshot[i] = extractionTypes[i].hydrate(
								rs, getPropertyAliases(
										"",
										naturalIdPropertyIndexes[i]
								), session, null
						);
						if ( extractionTypes[i].isEntityType() ) {
							snapshot[i] = extractionTypes[i].resolve( snapshot[i], session, owner );
						}
					}
					return snapshot;
				}
				finally {
					jdbcCoordinator.getResourceRegistry().release( rs, ps );
				}
			}
			finally {
				jdbcCoordinator.getResourceRegistry().release( ps );
				jdbcCoordinator.afterStatementExecution();
			}
		}
		catch (SQLException e) {
			throw getFactory().getSQLExceptionHelper().convert(
					e,
					"could not retrieve snapshot: " + MessageHelper.infoString( this, id, getFactory() ),
					sql
			);
		}
	}

	@Override
	public Serializable loadEntityIdByNaturalId(
			Object[] naturalIdValues,
			LockOptions lockOptions,
			SharedSessionContractImplementor session) {
		if ( LOG.isTraceEnabled() ) {
			LOG.tracef(
					"Resolving natural-id [%s] to id : %s ",
					Arrays.asList( naturalIdValues ),
					MessageHelper.infoString( this )
			);
		}

		final boolean[] valueNullness = determineValueNullness( naturalIdValues );
		final String sqlEntityIdByNaturalIdString = determinePkByNaturalIdQuery( valueNullness );

		try {
			PreparedStatement ps = session
					.getJdbcCoordinator()
					.getStatementPreparer()
					.prepareStatement( sqlEntityIdByNaturalIdString );
			try {
				int positions = 1;
				int loop = 0;
				for ( int idPosition : getNaturalIdentifierProperties() ) {
					final Object naturalIdValue = naturalIdValues[loop++];
					if ( naturalIdValue != null ) {
						final Type type = getPropertyTypes()[idPosition];
						type.nullSafeSet( ps, naturalIdValue, positions, session );
						positions += type.getColumnSpan( session.getFactory() );
					}
				}
				ResultSet rs = session.getJdbcCoordinator().getResultSetReturn().extract( ps );
				try {
					// if there is no resulting row, return null
					if ( !rs.next() ) {
						return null;
					}

					final Object hydratedId = getIdentifierType().hydrate( rs, getIdentifierAliases(), session, null );
					return (Serializable) getIdentifierType().resolve( hydratedId, session, null );
				}
				finally {
					session.getJdbcCoordinator().getResourceRegistry().release( rs, ps );
				}
			}
			finally {
				session.getJdbcCoordinator().getResourceRegistry().release( ps );
				session.getJdbcCoordinator().afterStatementExecution();
			}
		}
		catch (SQLException e) {
			throw getFactory().getSQLExceptionHelper().convert(
					e,
					String.format(
							"could not resolve natural-id [%s] to id : %s",
							Arrays.asList( naturalIdValues ),
							MessageHelper.infoString( this )
					),
					sqlEntityIdByNaturalIdString
			);
		}
	}

	private boolean[] determineValueNullness(Object[] naturalIdValues) {
		boolean[] nullness = new boolean[naturalIdValues.length];
		for ( int i = 0; i < naturalIdValues.length; i++ ) {
			nullness[i] = naturalIdValues[i] == null;
		}
		return nullness;
	}

	private Boolean naturalIdIsNonNullable;
	private String cachedPkByNonNullableNaturalIdQuery;

	private String determinePkByNaturalIdQuery(boolean[] valueNullness) {
		if ( !hasNaturalIdentifier() ) {
			throw new HibernateException(
					"Attempt to build natural-id -> PK resolution query for entity that does not define natural id"
			);
		}

		// performance shortcut for cases where the natural-id is defined as completely non-nullable
		if ( isNaturalIdNonNullable() ) {
			if ( valueNullness != null && !ArrayHelper.isAllFalse( valueNullness ) ) {
				throw new HibernateException( "Null value(s) passed to lookup by non-nullable natural-id" );
			}
			if ( cachedPkByNonNullableNaturalIdQuery == null ) {
				cachedPkByNonNullableNaturalIdQuery = generateEntityIdByNaturalIdSql( null );
			}
			return cachedPkByNonNullableNaturalIdQuery;
		}

		// Otherwise, regenerate it each time
		return generateEntityIdByNaturalIdSql( valueNullness );
	}

	protected boolean isNaturalIdNonNullable() {
		if ( naturalIdIsNonNullable == null ) {
			naturalIdIsNonNullable = determineNaturalIdNullability();
		}
		return naturalIdIsNonNullable;
	}

	private boolean determineNaturalIdNullability() {
		boolean[] nullability = getPropertyNullability();
		for ( int position : getNaturalIdentifierProperties() ) {
			// if any individual property is nullable, return false
			if ( nullability[position] ) {
				return false;
			}
		}
		// return true if we found no individually nullable properties
		return true;
	}

	private String generateEntityIdByNaturalIdSql(boolean[] valueNullness) {
		EntityPersister rootPersister = getFactory().getEntityPersister( getRootEntityName() );
		if ( rootPersister != this ) {
			if ( rootPersister instanceof AbstractEntityPersister ) {
				return ( (AbstractEntityPersister) rootPersister ).generateEntityIdByNaturalIdSql( valueNullness );
			}
		}

		Select select = new Select( getFactory().getDialect() );
		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			select.setComment( "get current natural-id->entity-id state " + getEntityName() );
		}

		final String rootAlias = getRootAlias();

		select.setSelectClause( identifierSelectFragment( rootAlias, "" ) );
		select.setFromClause( fromTableFragment( rootAlias ) + fromJoinFragment( rootAlias, true, false ) );

		final StringBuilder whereClause = new StringBuilder();
		final int[] propertyTableNumbers = getPropertyTableNumbers();
		final int[] naturalIdPropertyIndexes = this.getNaturalIdentifierProperties();
		int valuesIndex = -1;
		for ( int propIdx = 0; propIdx < naturalIdPropertyIndexes.length; propIdx++ ) {
			valuesIndex++;
			if ( propIdx > 0 ) {
				whereClause.append( " and " );
			}

			final int naturalIdIdx = naturalIdPropertyIndexes[propIdx];
			final String tableAlias = generateTableAlias( rootAlias, propertyTableNumbers[naturalIdIdx] );
			final String[] propertyColumnNames = getPropertyColumnNames( naturalIdIdx );
			final String[] aliasedPropertyColumns = StringHelper.qualify( tableAlias, propertyColumnNames );

			if ( valueNullness != null && valueNullness[valuesIndex] ) {
				whereClause.append( String.join( " is null and ", aliasedPropertyColumns ) ).append( " is null" );
			}
			else {
				whereClause.append( String.join( "=? and ", aliasedPropertyColumns ) ).append( "=?" );
			}
		}

		whereClause.append( whereJoinFragment( getRootAlias(), true, false ) );

		return select.setOuterJoins( "", "" ).setWhereClause( whereClause.toString() ).toStatementString();
	}

	protected String concretePropertySelectFragmentSansLeadingComma(String alias, boolean[] include) {
		String concretePropertySelectFragment = concretePropertySelectFragment( alias, include );
		int firstComma = concretePropertySelectFragment.indexOf( ", " );
		if ( firstComma == 0 ) {
			concretePropertySelectFragment = concretePropertySelectFragment.substring( 2 );
		}
		return concretePropertySelectFragment;
	}

	public boolean hasNaturalIdentifier() {
		return entityMetamodel.hasNaturalIdentifier();
	}

	public void setPropertyValue(Object object, String propertyName, Object value) {
		getEntityTuplizer().setPropertyValue( object, propertyName, value );
	}

	public static int getTableId(String tableName, String[] tables) {
		for ( int j = 0; j < tables.length; j++ ) {
			if ( tableName.equalsIgnoreCase( tables[j] ) ) {
				return j;
			}
		}
		throw new AssertionFailure( "Table " + tableName + " not found" );
	}

	@Override
	public EntityRepresentationStrategy getRepresentationStrategy() {
		return representationStrategy;
	}

	@Override
	public EntityMode getEntityMode() {
		return getRepresentationStrategy().getMode().getLegacyEntityMode();
	}

	@Override
	public EntityTuplizer getEntityTuplizer() {
		return null;
	}

	@Override
	public BytecodeEnhancementMetadata getInstrumentationMetadata() {
		return getBytecodeEnhancementMetadata();
	}

	@Override
	public BytecodeEnhancementMetadata getBytecodeEnhancementMetadata() {
		return entityMetamodel.getBytecodeEnhancementMetadata();
	}

	@Override
	public String getTableAliasForColumn(String columnName, String rootAlias) {
		return generateTableAlias( rootAlias, determineTableNumberForColumn( columnName ) );
	}

	public int determineTableNumberForColumn(String columnName) {
		return 0;
	}

	protected String determineTableName(Table table, JdbcEnvironment jdbcEnvironment) {
		if ( table.getSubselect() != null ) {
			return "( " + table.getSubselect() + " )";
		}

		return jdbcEnvironment.getQualifiedObjectNameFormatter().format(
				table.getQualifiedTableName(),
				jdbcEnvironment.getDialect()
		);
	}

	@Override
	public EntityEntryFactory getEntityEntryFactory() {
		return this.entityEntryFactory;
	}

	/**
	 * Consolidated these onto a single helper because the 2 pieces work in tandem.
	 */
	public interface CacheEntryHelper {
		CacheEntryStructure getCacheEntryStructure();

		CacheEntry buildCacheEntry(Object entity, Object[] state, Object version, SharedSessionContractImplementor session);
	}

	private static class StandardCacheEntryHelper implements CacheEntryHelper {
		private final EntityPersister persister;

		private StandardCacheEntryHelper(EntityPersister persister) {
			this.persister = persister;
		}

		@Override
		public CacheEntryStructure getCacheEntryStructure() {
			return UnstructuredCacheEntry.INSTANCE;
		}

		@Override
		public CacheEntry buildCacheEntry(Object entity, Object[] state, Object version, SharedSessionContractImplementor session) {
			return new StandardCacheEntryImpl(
					state,
					persister,
					version,
					session,
					entity
			);
		}
	}

	private static class ReferenceCacheEntryHelper implements CacheEntryHelper {
		private final EntityPersister persister;

		private ReferenceCacheEntryHelper(EntityPersister persister) {
			this.persister = persister;
		}

		@Override
		public CacheEntryStructure getCacheEntryStructure() {
			return UnstructuredCacheEntry.INSTANCE;
		}

		@Override
		public CacheEntry buildCacheEntry(Object entity, Object[] state, Object version, SharedSessionContractImplementor session) {
			return new ReferenceCacheEntryImpl( entity, persister );
		}
	}

	private static class StructuredCacheEntryHelper implements CacheEntryHelper {
		private final EntityPersister persister;
		private final StructuredCacheEntry structure;

		private StructuredCacheEntryHelper(EntityPersister persister) {
			this.persister = persister;
			this.structure = new StructuredCacheEntry( persister );
		}

		@Override
		public CacheEntryStructure getCacheEntryStructure() {
			return structure;
		}

		@Override
		public CacheEntry buildCacheEntry(Object entity, Object[] state, Object version, SharedSessionContractImplementor session) {
			return new StandardCacheEntryImpl(
					state,
					persister,
					version,
					session,
					entity
			);
		}
	}

	private static class NoopCacheEntryHelper implements CacheEntryHelper {
		public static final NoopCacheEntryHelper INSTANCE = new NoopCacheEntryHelper();

		@Override
		public CacheEntryStructure getCacheEntryStructure() {
			return UnstructuredCacheEntry.INSTANCE;
		}

		@Override
		public CacheEntry buildCacheEntry(Object entity, Object[] state, Object version, SharedSessionContractImplementor session) {
			throw new HibernateException( "Illegal attempt to build cache entry for non-cached entity" );
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// org.hibernate.metamodel.mapping.EntityMappingType

	private JavaTypeDescriptor javaTypeDescriptor;
	private EntityRepresentationStrategy representationStrategy;

	private EntityMappingType superMappingType;
	private SortedMap<String, EntityMappingType> subclassMappingTypes;

	private EntityIdentifierMapping identifierMapping;
	private NaturalIdMapping naturalIdMapping;
	private EntityVersionMapping versionMapping;

	private SortedMap<String, AttributeMapping> declaredAttributeMappings = new TreeMap<>();

	private ReflectionOptimizer.AccessOptimizer accessOptimizer;

	@Override
	public void prepareMappingModel(MappingModelCreationProcess creationProcess) {
		if ( identifierMapping != null ) {
			return;
		}

		final RuntimeModelCreationContext creationContext = creationProcess.getCreationContext();

		final PersistentClass bootEntityDescriptor = creationContext
				.getBootModel()
				.getEntityBinding( getEntityName() );

		// todo (6.0) : should we create these only on root?

		identifierMapping = creationProcess.processSubPart(
				EntityIdentifierMapping.ROLE_LOCAL_NAME,
				(role, creationProcess1) -> generateIdentifierMapping( creationProcess )
		);

		naturalIdMapping = null;

		if ( getVersionType() == null ) {
			versionMapping = null;
		}
		else {
			final int versionPropertyIndex = getVersionProperty();
			final String versionPropertyName = getPropertyNames()[ versionPropertyIndex ];

			versionMapping = creationProcess.processSubPart(
					versionPropertyName,
					(role, creationProcess1) -> generateVersionMapping( this, creationProcess )
			);
		}

		final EntityMetamodel currentEntityMetamodel = this.getEntityMetamodel();

		int stateArrayPosition = superMappingType == null ? 0 : superMappingType.getAttributeMappings().size();

		for ( int i = 0; i < currentEntityMetamodel.getPropertySpan(); i++ ) {
			final NonIdentifierAttribute runtimeAttrDefinition = currentEntityMetamodel.getProperties()[i];
			final Property bootProperty = bootEntityDescriptor.getProperty( runtimeAttrDefinition.getName() );

			declaredAttributeMappings.put(
					runtimeAttrDefinition.getName(),
					generateNonIdAttributeMapping(
							runtimeAttrDefinition,
							bootProperty,
							stateArrayPosition++,
							this,
							creationProcess
					)
			);
		}

		final ReflectionOptimizer reflectionOptimizer = representationStrategy.getReflectionOptimizer();

		if ( reflectionOptimizer != null ) {
			accessOptimizer = reflectionOptimizer.getAccessOptimizer();
		}
		else {
			accessOptimizer = null;
		}
	}

	@Override
	public void linkWithSuperType(MappingModelCreationProcess creationProcess) {
		if ( getMappedSuperclass() == null ) {
			return;
		}

		this.superMappingType = creationProcess.getEntityPersister( getMappedSuperclass() );
		( (InFlightEntityMappingType) superMappingType ).linkWithSubType( this, creationProcess );
	}

	@Override
	public void linkWithSubType(EntityMappingType sub, MappingModelCreationProcess creationProcess) {
		if ( subclassMappingTypes == null ) {
			//noinspection unchecked
			subclassMappingTypes = new TreeMap();
		}
		subclassMappingTypes.put( sub.getEntityName(), sub );
	}

	@Override
	public boolean isTypeOrSuperType(EntityMappingType targetType) {
		if ( targetType == null ) {
			// todo (6.0) : need to think through what this ought to indicate (if we allow it at all)
			//		- see `org.hibernate.metamodel.mapping.internal.AbstractManagedMappingType#isTypeOrSuperType`
			return true;
		}

		if ( targetType == this ) {
			return true;
		}

		if ( superMappingType != null ) {
			return superMappingType.isTypeOrSuperType( targetType );
		}

		return false;
	}


	private EntityIdentifierMapping generateIdentifierMapping(MappingModelCreationProcess creationProcess) {
		final Type idType = getIdentifierType();

		if ( idType instanceof CompositeType ) {
			final CompositeType cidType = (CompositeType) idType;
			if ( ! cidType.isEmbedded() ) {
				return MappingModelCreationHelper.buildEncapsulatedCompositeIdentifierMapping(
						this,
						getRootTableName(),
						rootTableKeyColumnNames,
						cidType,
						creationProcess
				);
			}

			return MappingModelCreationHelper.buildNonEncapsulatedCompositeIdentifierMapping(
					this,
					getRootTableName(),
					rootTableKeyColumnNames,
					cidType,
					creationProcess
			);
		}

		return MappingModelCreationHelper.buildSimpleIdentifierMapping(
				this,
				getRootTableName(),
				rootTableKeyColumnNames[0],
				(BasicType) idType,
				creationProcess
		);
	}

	private static EntityVersionMapping generateVersionMapping(
			EntityPersister entityPersister,
			MappingModelCreationProcess creationProcess) {
		throw new NotYetImplementedFor6Exception( AbstractEntityPersister.class );
	}

	private AttributeMapping generateNonIdAttributeMapping(
			NonIdentifierAttribute tupleAttrDefinition,
			Property bootProperty,
			int stateArrayPosition,
			ManagedMappingType declaringType,
			MappingModelCreationProcess creationProcess) {

		final String attrName = tupleAttrDefinition.getName();
		final Type attrType = tupleAttrDefinition.getType();

		final int propertyIndex = getPropertyIndex( bootProperty.getName() );

		if ( propertyIndex == getVersionProperty() ) {
			return getVersionMapping();
		}

		final String tableExpression = getPropertyTableName( attrName );
		final String[] attrColumnNames = getPropertyColumnNames( propertyIndex );

		final PropertyAccess propertyAccess = getRepresentationStrategy().resolvePropertyAccess( bootProperty );

		if ( attrType instanceof BasicType ) {
			return MappingModelCreationHelper.buildBasicAttributeMapping(
					attrName,
					stateArrayPosition,
					bootProperty,
					declaringType,
					(BasicType) attrType,
					tableExpression,
					attrColumnNames[0],
					propertyAccess,
					tupleAttrDefinition.getCascadeStyle(),
					creationProcess
			);
		}
		else if ( attrType instanceof CompositeType ) {
			return MappingModelCreationHelper.buildEmbeddedAttributeMapping(
					attrName,
					stateArrayPosition,
					bootProperty,
					declaringType,
					(CompositeType) attrType,
					tableExpression,
					attrColumnNames,
					propertyAccess,
					tupleAttrDefinition.getCascadeStyle(),
					creationProcess
			);
		}

		// todo (6.0) : for now ignore any non basic-typed attributes

		return null;
	}

	@Override
	public JavaTypeDescriptor getMappedJavaTypeDescriptor() {
		return javaTypeDescriptor;
	}

	@Override
	public EntityPersister getEntityPersister() {
		return this;
	}

	@Override
	public EntityIdentifierMapping getIdentifierMapping() {
		return identifierMapping;
	}

	@Override
	public EntityVersionMapping getVersionMapping() {
		return versionMapping;
	}

	private Collection<AttributeMapping> attributeMappings;

	@Override
	public Collection<AttributeMapping> getAttributeMappings() {
		if ( attributeMappings == null ) {
			attributeMappings = new ArrayList<>();

			if ( superMappingType != null ) {
				superMappingType.visitAttributeMappings(
						attributeMappings::add,
						// only walk up the hierarchy
						superMappingType
				);
			}

			attributeMappings.addAll( declaredAttributeMappings.values() );

			// subclasses?  it depends on the usage
		}

		return attributeMappings;
	}

	@Override
	public ModelPart findSubPart(String name, EntityMappingType treatTargetType) {
		LOG.tracef( "#findSubPart(`%s`)", name );

		final AttributeMapping declaredAttribute = declaredAttributeMappings.get( name );
		if ( declaredAttribute != null ) {
			return declaredAttribute;
		}

		if ( superMappingType != null ) {
			final ModelPart superDefinedAttribute = superMappingType.findSubPart( name, superMappingType );
			if ( superDefinedAttribute != null ) {
				return superDefinedAttribute;
			}
		}

		if ( subclassMappingTypes != null && ! subclassMappingTypes.isEmpty() ) {
			for ( EntityMappingType subMappingType : subclassMappingTypes.values() ) {
				final ModelPart subDefinedAttribute = subMappingType.findSubPart( name, treatTargetType );
				if ( subDefinedAttribute != null ) {
					return subDefinedAttribute;
				}
			}

		}

		return null;
	}

	@Override
	public void visitSubParts(
			Consumer<ModelPart> consumer,
			EntityMappingType treatTargetType) {
		consumer.accept( identifierMapping );

		declaredAttributeMappings.values().forEach( consumer );
	}

	@Override
	public void visitKeyFetchables(
			Consumer<Fetchable> fetchableConsumer,
			EntityMappingType treatTargetType) {
		if ( getIdentifierMapping() instanceof FetchableContainer ) {
			// essentially means the entity has a composite id - ask the embeddable to visit its fetchables
			//		- todo (6.0) : determine whether this should call `#visitFetchables` or `#visitKeyFetchables`
			( (FetchableContainer) getIdentifierMapping() ).visitFetchables( fetchableConsumer, treatTargetType );
		}

		// otherwise, nothing to do
	}

	@Override
	public void visitFetchables(
			Consumer<Fetchable> fetchableConsumer,
			EntityMappingType treatTargetType) {
		visitStateArrayContributors(
				mapping -> {
// treat limits are already handled in `#visitAttributeMappings` (called from `#visitStateArrayContributors`)
//					if ( mapping.isDeclaredOnTypeOrSuperType( treatTargetType ) ) {
						fetchableConsumer.accept( mapping );
//					}
				},
				treatTargetType
		);
	}

	@Override
	public void visitStateArrayContributors(
			Consumer<StateArrayContributorMapping> mappingConsumer,
			EntityMappingType targetType) {
		visitAttributeMappings(
				attributeMapping -> {
					if ( attributeMapping instanceof StateArrayContributorMapping ) {
						mappingConsumer.accept( (StateArrayContributorMapping) attributeMapping );
					}
				},
				targetType
		);
	}

	@Override
	public void visitAttributeMappings(
			Consumer<AttributeMapping> action,
			EntityMappingType targetType) {
		visitSuperTypeAttributeMappings( action );

		declaredAttributeMappings.values().forEach( action );

		if ( targetType == null || targetType.isTypeOrSuperType( this ) ) {
			visitSubTypeAttributeMappings( action );
		}
	}

	@Override
	public void visitSuperTypeAttributeMappings(Consumer<AttributeMapping> action) {
		if ( superMappingType != null ) {
			superMappingType.visitSuperTypeAttributeMappings( action );
		}
	}

	@Override
	public void visitSubTypeAttributeMappings(Consumer<AttributeMapping> action) {
		if ( subclassMappingTypes != null ) {
			subclassMappingTypes.values().forEach(
					subclassMappingTypes -> subclassMappingTypes.visitSubTypeAttributeMappings( action )
			);
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// EntityDefinition impl (walking model - deprecated)

	private EntityIdentifierDefinition entityIdentifierDefinition;
	private SortedSet<AttributeDefinition> attributeDefinitions = new TreeSet<>(
			Comparator.comparing( AttributeDefinition::getName )
	);

	@Override
	public void generateEntityDefinition() {
		prepareEntityIdentifierDefinition();
		collectAttributeDefinitions();
	}



	@Override
	public EntityIdentifierDefinition getEntityKeyDefinition() {
		return entityIdentifierDefinition;
	}

	@Override
	public Iterable<AttributeDefinition> getAttributes() {
		return attributeDefinitions;
	}

	public String[][] getPolymorphicJoinColumns(String lhsTableAlias, String propertyPath) {
		Set<String> subclassEntityNames = getEntityMetamodel().getSubclassEntityNames();
		// We will collect all the join columns from the LHS subtypes here
		List<String[]> polymorphicJoinColumns = new ArrayList<>( subclassEntityNames.size() );

		String[] joinColumns;

		OUTER:
		for ( String subclassEntityName : subclassEntityNames ) {
			AbstractEntityPersister subclassPersister = (AbstractEntityPersister) getFactory()
					.getMetamodel()
					.entityPersister( subclassEntityName );
			joinColumns = subclassPersister.toColumns( lhsTableAlias, propertyPath );

			if ( joinColumns.length == 0 ) {
				// The subtype does not have a "concrete" mapping for the property path
				continue;
			}

			// Check for duplicates like this since we will mostly have just a few candidates
			for ( String[] existingColumns : polymorphicJoinColumns ) {
				if ( Arrays.deepEquals( existingColumns, joinColumns ) ) {
					continue OUTER;
				}
			}
			polymorphicJoinColumns.add( joinColumns );
		}

		return ArrayHelper.to2DStringArray( polymorphicJoinColumns );
	}

	/**
	 * If true, persister can omit superclass tables during joining if they are not needed in the query.
	 *
	 * @return true if the persister can do it
	 */
	public boolean canOmitSuperclassTableJoin() {
		return false;
	}

	private void prepareEntityIdentifierDefinition() {
		if ( entityIdentifierDefinition != null ) {
			return;
		}
		final Type idType = getIdentifierType();

		if ( !idType.isComponentType() ) {
			entityIdentifierDefinition =
					EntityIdentifierDefinitionHelper.buildSimpleEncapsulatedIdentifierDefinition( this );
			return;
		}

		final CompositeType cidType = (CompositeType) idType;
		if ( !cidType.isEmbedded() ) {
			entityIdentifierDefinition =
					EntityIdentifierDefinitionHelper.buildEncapsulatedCompositeIdentifierDefinition( this );
			return;
		}

		entityIdentifierDefinition =
				EntityIdentifierDefinitionHelper.buildNonEncapsulatedCompositeIdentifierDefinition( this );
	}

	private void collectAttributeDefinitions(
			Map<String, AttributeDefinition> attributeDefinitionsByName,
			EntityMetamodel metamodel) {
		for ( int i = 0; i < metamodel.getPropertySpan(); i++ ) {
			final AttributeDefinition attributeDefinition = metamodel.getProperties()[i];
			// Don't replace an attribute definition if it is already in attributeDefinitionsByName
			// because the new value will be from a subclass.
			final AttributeDefinition oldAttributeDefinition = attributeDefinitionsByName.get(
					attributeDefinition.getName()
			);
			if ( oldAttributeDefinition != null ) {
				if ( LOG.isTraceEnabled() ) {
					LOG.tracef(
							"Ignoring subclass attribute definition [%s.%s] because it is defined in a superclass ",
							entityMetamodel.getName(),
							attributeDefinition.getName()
					);
				}
			}
			else {
				attributeDefinitionsByName.put( attributeDefinition.getName(), attributeDefinition );
			}
		}

		// see if there are any subclass persisters...
		final Set<String> subClassEntityNames = metamodel.getSubclassEntityNames();
		if ( subClassEntityNames == null ) {
			return;
		}

		// see if we can find the persisters...
		for ( String subClassEntityName : subClassEntityNames ) {
			if ( metamodel.getName().equals( subClassEntityName ) ) {
				// skip it
				continue;
			}
			try {
				final EntityPersister subClassEntityPersister = factory.getMetamodel().getEntityDescriptor( subClassEntityName );
				collectAttributeDefinitions( attributeDefinitionsByName, subClassEntityPersister.getEntityMetamodel() );
			}
			catch (MappingException e) {
				throw new IllegalStateException(
						String.format(
								"Could not locate subclass EntityPersister [%s] while processing EntityPersister [%s]",
								subClassEntityName,
								metamodel.getName()
						),
						e
				);
			}
		}
	}

	private void collectAttributeDefinitions() {
		// todo : I think this works purely based on luck atm
		// 		specifically in terms of the sub/super class entity persister(s) being available.  Bit of chicken-egg
		// 		problem there:
		//			* If I do this during postConstruct (as it is now), it works as long as the
		//			super entity persister is already registered, but I don't think that is necessarily true.
		//			* If I do this during postInstantiate then lots of stuff in postConstruct breaks if we want
		//			to try and drive SQL generation on these (which we do ultimately).  A possible solution there
		//			would be to delay all SQL generation until postInstantiate

		Map<String, AttributeDefinition> attributeDefinitionsByName = new LinkedHashMap<>();
		collectAttributeDefinitions( attributeDefinitionsByName, getEntityMetamodel() );


//		EntityMetamodel currentEntityMetamodel = this.getEntityMetamodel();
//		while ( currentEntityMetamodel != null ) {
//			for ( int i = 0; i < currentEntityMetamodel.getPropertySpan(); i++ ) {
//				attributeDefinitions.add( currentEntityMetamodel.getProperties()[i] );
//			}
//			// see if there is a super class EntityMetamodel
//			final String superEntityName = currentEntityMetamodel.getSuperclass();
//			if ( superEntityName != null ) {
//				currentEntityMetamodel = factory.getEntityPersister( superEntityName ).getEntityMetamodel();
//			}
//			else {
//				currentEntityMetamodel = null;
//			}
//		}

//		// todo : leverage the attribute definitions housed on EntityMetamodel
//		// 		for that to work, we'd have to be able to walk our super entity persister(s)
//		this.attributeDefinitions = new Iterable<AttributeDefinition>() {
//			@Override
//			public Iterator<AttributeDefinition> iterator() {
//				return new Iterator<AttributeDefinition>() {
////					private final int numberOfAttributes = countSubclassProperties();
////					private final int numberOfAttributes = entityMetamodel.getPropertySpan();
//
//					EntityMetamodel currentEntityMetamodel = entityMetamodel;
//					int numberOfAttributesInCurrentEntityMetamodel = currentEntityMetamodel.getPropertySpan();
//
//					private int currentAttributeNumber;
//
//					@Override
//					public boolean hasNext() {
//						return currentEntityMetamodel != null
//								&& currentAttributeNumber < numberOfAttributesInCurrentEntityMetamodel;
//					}
//
//					@Override
//					public AttributeDefinition next() {
//						final int attributeNumber = currentAttributeNumber;
//						currentAttributeNumber++;
//						final AttributeDefinition next = currentEntityMetamodel.getProperties()[ attributeNumber ];
//
//						if ( currentAttributeNumber >= numberOfAttributesInCurrentEntityMetamodel ) {
//							// see if there is a super class EntityMetamodel
//							final String superEntityName = currentEntityMetamodel.getSuperclass();
//							if ( superEntityName != null ) {
//								currentEntityMetamodel = factory.getEntityPersister( superEntityName ).getEntityMetamodel();
//								if ( currentEntityMetamodel != null ) {
//									numberOfAttributesInCurrentEntityMetamodel = currentEntityMetamodel.getPropertySpan();
//									currentAttributeNumber = 0;
//								}
//							}
//						}
//
//						return next;
//					}
//
//					@Override
//					public void remove() {
//						throw new UnsupportedOperationException( "Remove operation not supported here" );
//					}
//				};
//			}
//		};
	}

	private static class SubstituteBracketSQLQueryParser extends SQLQueryParser {

		SubstituteBracketSQLQueryParser(String queryString, SessionFactoryImplementor factory) {
			super( queryString, null, factory );
		}

		@Override
		public String process() {
			return substituteBrackets( getOriginalQueryString() );
		}
	}
}
