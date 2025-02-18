/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableMutationStrategy;

/**
 * An SQL dialect for the SAP HANA row store.
 * <p>
 * For more information on interacting with the SAP HANA database, refer to the
 * <a href="https://help.sap.com/viewer/4fe29514fd584807ac9f2a04f6754767/">SAP HANA SQL and System Views Reference</a>
 * and the <a href=
 * "https://help.sap.com/viewer/0eec0d68141541d1b07893a39944924e/latest/en-US/434e2962074540e18c802fd478de86d6.html">SAP
 * HANA Client Interface Programming Reference</a>.
 * <p>
 * Row tables are created by this dialect when using the auto-ddl feature.
 *
 * @author Andrew Clemons <andrew.clemons@sap.com>
 * @author Jonathan Bregler <jonathan.bregler@sap.com>
 */
public class HANARowStoreDialect extends AbstractHANADialect {

	public HANARowStoreDialect() {
		super();
	}

	@Override
	public String getCreateTableString() {
		return "create row table";
	}

	@Override
	public SqmMultiTableMutationStrategy getFallbackSqmMutationStrategy(EntityPersister runtimeRootEntityDescriptor) {
		throw new NotYetImplementedFor6Exception( getClass() );

//		return new GlobalTemporaryTableBulkIdStrategy( new IdTableSupportStandardImpl() {
//
//			@Override
//			public String getCreateIdTableCommand() {
//				return "create global temporary row table";
//			}
//		}, AfterUseAction.CLEAN );
	}
}
