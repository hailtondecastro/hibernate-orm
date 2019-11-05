package org.hibernate.orm.test.metamodel.mapping.components.relations.hhhyyyyy;

import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.testing.TestForIssue;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;

/**
 * This template demonstrates how to develop a standalone test case for Hibernate ORM.  Although this is perfectly
 * acceptable as a reproducer, usage of ORMUnitTestCase is preferred!
 */
@TestForIssue(jiraKey = "HHH-yyyyy")
@Tags({
	@Tag("RunnableIdeTest"),
})
public class ORMStandaloneTestCase {

	private SessionFactory sf;

	@Before
	public void setup() {
		StandardServiceRegistryBuilder srb = new StandardServiceRegistryBuilder()
			// Add in any settings that are specific to your test. See resources/hibernate.properties for the defaults.
			.applySetting( "hibernate.show_sql", "true" )
			.applySetting( "hibernate.format_sql", "true" )
			.applySetting( "hibernate.hbm2ddl.auto", "update" );

		Metadata metadata = new MetadataSources( srb.build() )
		// Add your entities here.
		//	.addAnnotatedClass( Foo.class )
			.buildMetadata();

		sf = metadata.buildSessionFactory();
	}

	// Add your tests, using standard JUnit.

	@Test
	public void hhh123Test() throws Exception {

	}
}
