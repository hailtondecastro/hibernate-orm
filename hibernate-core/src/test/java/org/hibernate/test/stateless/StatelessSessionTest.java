/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.stateless;

import java.util.Date;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;

import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;

import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

/**
 * @author Gavin King
 */
public class StatelessSessionTest extends BaseCoreFunctionalTestCase {
	public String[] getMappings() {
		return new String[] { "stateless/Document.hbm.xml" };
	}

	@Test
	public void testCreateUpdateReadDelete() {
		try (StatelessSession ss = sessionFactory().openStatelessSession()) {
			try {
				Transaction tx = ss.beginTransaction();
				Document doc = new Document( "blah blah blah", "Blahs" );
				ss.insert( doc );
				assertNotNull( doc.getName() );
				Date initVersion = doc.getLastModified();
				assertNotNull( initVersion );
				tx.commit();

				tx = ss.beginTransaction();
				doc.setText( "blah blah blah .... blah" );
				ss.update( doc );
				assertNotNull( doc.getLastModified() );
				assertNotSame( doc.getLastModified(), initVersion );
				tx.commit();

				tx = ss.beginTransaction();
				doc.setText( "blah blah blah .... blah blay" );
				ss.update( doc );
				tx.commit();

				Document doc2 = (Document) ss.get( Document.class.getName(), "Blahs" );
				assertEquals( "Blahs", doc2.getName() );
				assertEquals( doc.getText(), doc2.getText() );

				doc2 = (Document) ss.createQuery( "from Document where text is not null" ).uniqueResult();
				assertEquals( "Blahs", doc2.getName() );
				assertEquals( doc.getText(), doc2.getText() );

				ScrollableResults sr = ss.createQuery( "from Document where text is not null" )
						.scroll( ScrollMode.FORWARD_ONLY );
				sr.next();
				doc2 = (Document) sr.get();
				sr.close();
				assertEquals( "Blahs", doc2.getName() );
				assertEquals( doc.getText(), doc2.getText() );

				doc2 = (Document) ss.createNativeQuery( "select * from Document" )
						.addEntity( Document.class )
						.uniqueResult();
				assertEquals( "Blahs", doc2.getName() );
				assertEquals( doc.getText(), doc2.getText() );


				CriteriaBuilder criteriaBuilder = ss.getCriteriaBuilder();
				CriteriaQuery<Document> criteria = criteriaBuilder.createQuery( Document.class );
				criteria.from( Document.class );
				doc2 = ss.createQuery( criteria ).uniqueResult();
				assertEquals( "Blahs", doc2.getName() );
				assertEquals( doc.getText(), doc2.getText() );

				criteria = criteriaBuilder.createQuery( Document.class );
				criteria.from( Document.class );

				sr = ss.createQuery( criteria ).scroll( ScrollMode.FORWARD_ONLY );
				sr.next();
				doc2 = (Document) sr.get();
				sr.close();
				assertEquals( "Blahs", doc2.getName() );
				assertEquals( doc.getText(), doc2.getText() );

				tx = ss.beginTransaction();
				ss.delete( doc );
				tx.commit();
			}
			catch (Exception e) {
				if ( ss.getTransaction().isActive() ) {
					ss.getTransaction().rollback();
				}
				throw e;
			}
		}
	}

	@Test
	public void testHqlBulk() {
		StatelessSession ss = sessionFactory().openStatelessSession();
		Transaction tx = ss.beginTransaction();
		Document doc = new Document("blah blah blah", "Blahs");
		ss.insert(doc);
		Paper paper = new Paper();
		paper.setColor( "White" );
		ss.insert(paper);
		tx.commit();

		tx = ss.beginTransaction();
		int count = ss.createQuery( "update Document set name = :newName where name = :oldName" )
				.setParameter( "newName", "Foos" )
				.setParameter( "oldName", "Blahs" )
				.executeUpdate();
		assertEquals( "hql-update on stateless session", 1, count );
		count = ss.createQuery( "update Paper set color = :newColor" )
				.setParameter( "newColor", "Goldenrod" )
				.executeUpdate();
		assertEquals( "hql-update on stateless session", 1, count );
		tx.commit();

		tx = ss.beginTransaction();
		count = ss.createQuery( "delete Document" ).executeUpdate();
		assertEquals( "hql-delete on stateless session", 1, count );
		count = ss.createQuery( "delete Paper" ).executeUpdate();
		assertEquals( "hql-delete on stateless session", 1, count );
		tx.commit();
		ss.close();
	}

	@Test
	public void testInitId() {
		StatelessSession ss = sessionFactory().openStatelessSession();
		Transaction tx = ss.beginTransaction();
		Paper paper = new Paper();
		paper.setColor( "White" );
		ss.insert(paper);
		assertNotNull( paper.getId() );
		tx.commit();

		tx = ss.beginTransaction();
		ss.delete( ss.get( Paper.class, paper.getId() ) );
		tx.commit();
		ss.close();
	}

	@Test
	public void testRefresh() {
		StatelessSession ss = sessionFactory().openStatelessSession();
		Transaction tx = ss.beginTransaction();
		Paper paper = new Paper();
		paper.setColor( "whtie" );
		ss.insert( paper );
		tx.commit();
		ss.close();

		ss = sessionFactory().openStatelessSession();
		tx = ss.beginTransaction();
		Paper p2 = ( Paper ) ss.get( Paper.class, paper.getId() );
		p2.setColor( "White" );
		ss.update( p2 );
		tx.commit();
		ss.close();

		ss = sessionFactory().openStatelessSession();
		tx = ss.beginTransaction();
		assertEquals( "whtie", paper.getColor() );
		ss.refresh( paper );
		assertEquals( "White", paper.getColor() );
		ss.delete( paper );
		tx.commit();
		ss.close();
	}
}

