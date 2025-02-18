/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import org.hibernate.orm.test.query.sqm.BaseSqmUnitTest;
import org.hibernate.orm.test.query.sqm.domain.Person;

/**
 * @author Steve Ebersole
 */
public class FunctionTests extends BaseSqmUnitTest {
	@Override
	protected Class[] getAnnotatedClasses() {
		return new Class[] { Person.class };
	}


}
