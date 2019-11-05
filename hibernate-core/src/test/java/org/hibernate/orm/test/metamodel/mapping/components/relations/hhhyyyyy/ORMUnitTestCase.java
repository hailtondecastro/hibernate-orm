/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hibernate.orm.test.metamodel.mapping.components.relations.hhhyyyyy;

import static org.hibernate.testing.transaction.TransactionUtil.doInHibernate;

import java.util.HashSet;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.test.formulajoin.Master;
import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.hibernate.testing.junit4.BaseNonConfigCoreFunctionalTestCase;
import org.hibernate.testing.transaction.TransactionUtil;
import org.junit.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM, using its built-in unit test framework.
 * Although ORMStandaloneTestCase is perfectly acceptable as a reproducer, usage of this class is much preferred.
 * Since we nearly always include a regression test with bug fixes, providing your reproducer using this method
 * simplifies the process.
 *
 * What's even better?  Fork hibernate-orm itself, add your test case directly to a module's unit tests, then
 * submit it as a PR!
 */
@TestForIssue(jiraKey = "HHH-yyyyy")
@Tags({
	@Tag("RunnableIdeTest"),
})
public class ORMUnitTestCase extends BaseCoreFunctionalTestCase {

	// Add your entities here.
	@Override
	protected Class[] getAnnotatedClasses() {
		return new Class[] {
				MasterEnt.class,
				DetailEnt.class
		};
	}

	// If you use *.hbm.xml mappings, instead of annotations, add the mappings here.
	@Override
	protected String[] getMappings() {
		return new String[] {
//				"Foo.hbm.xml",
//				"Bar.hbm.xml"
		};
	}
	// If those mappings reside somewhere other than resources/org/hibernate/test, change this.
	@Override
	protected String getBaseForMappings() {
		return this.getClass().getPackage().getName().replace('.', '/');
	}

	// Add in any settings that are specific to your test.  See resources/hibernate.properties for the defaults.
	@Override
	protected void configure(Configuration configuration) {
		super.configure( configuration );

		configuration.setProperty( AvailableSettings.SHOW_SQL, Boolean.TRUE.toString() );
		configuration.setProperty( AvailableSettings.FORMAT_SQL, Boolean.FALSE.toString() );
		//configuration.setProperty( AvailableSettings.GENERATE_STATISTICS, "true" );
	}
	
	@Override
	protected void prepareTest() throws Exception {
		// TODO Auto-generated method stub
		super.prepareTest();
		inTransaction( session -> {
            for ( int i = 0; i < 1; i++ ) {
            	MasterEnt masterEnt = new MasterEnt();
            	masterEnt.setMtId( i );
            	masterEnt.setVcharA( "masterEnt_vcharA_" + i );
            	masterEnt.setMasterComp( new MasterComp() );
            	masterEnt.getMasterComp().setDetailEntCol( new HashSet<DetailEnt>() );
            	for ( int j = 0; j < 5; j++ ) {
					DetailEnt detailEnt = new DetailEnt();
					detailEnt.setDtId( (i * 1000) + j );
					detailEnt.setVcharA( "masterEnt_"+i+"_detailEnt_vcharA_" + detailEnt.getDtId());
					masterEnt.getMasterComp().getDetailEntCol().add( detailEnt );
					session.save(detailEnt);
				}
                session.save(masterEnt);
            }
		});
	}

	// Add your tests, using standard JUnit.
	@Test
	public void hhhYYYYYTest() throws Exception {
		inSession( session -> {
			//Transaction tx = session.beginTransaction();
			@SuppressWarnings("unused")
			MasterEnt master = ((EntityManager)session).find( MasterEnt.class, 0 );
//			tx.commit();
//			s.close();
		});
	}
}
