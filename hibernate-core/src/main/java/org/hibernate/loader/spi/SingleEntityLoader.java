/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.loader.spi;

import org.hibernate.LockOptions;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

/**
 * Loader for loading a single entity by primary or unique key
 *
 * @author Steve Ebersole
 */
public interface SingleEntityLoader<T> extends Loader {
	@Override
	EntityPersister getLoadable();

	/**
	 * Load an entity by a primary or unique key value.
	 */
	T load(Object key, LockOptions lockOptions, SharedSessionContractImplementor session);
}
