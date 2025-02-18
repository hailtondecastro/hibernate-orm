/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql;

import org.hibernate.sql.ast.tree.select.QuerySpec;

/**
 * @author Steve Ebersole
 */
public interface SqlAstQuerySpecProcessingState extends SqlAstProcessingState {
	/**
	 * Get the QuerySpec being processed as part of this state.  It is
	 * considered in-flight as it is probably still being built.
	 */
	QuerySpec getInflightQuerySpec();
}
