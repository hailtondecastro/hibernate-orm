/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.internal.domain;

import java.util.Collections;
import java.util.List;

import org.hibernate.metamodel.mapping.ManagedMappingType;
import org.hibernate.query.NavigablePath;
import org.hibernate.sql.results.spi.DomainResultCreationState;
import org.hibernate.sql.results.spi.Fetch;
import org.hibernate.sql.results.spi.FetchParent;
import org.hibernate.sql.results.spi.FetchableContainer;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractFetchParent implements FetchParent {
	private final FetchableContainer fetchContainer;
	private final NavigablePath navigablePath;

	private List<Fetch> fetches;

	public AbstractFetchParent(FetchableContainer fetchContainer, NavigablePath navigablePath) {
		this.fetchContainer = fetchContainer;
		this.navigablePath = navigablePath;
	}

	protected void afterInitialize(DomainResultCreationState creationState) {
		this.fetches = creationState.visitFetches( this );
	}

	public FetchableContainer getFetchContainer() {
		return fetchContainer;
	}

	@Override
	public ManagedMappingType getReferencedMappingType() {
		return (ManagedMappingType) getFetchContainer();
	}

	@Override
	public JavaTypeDescriptor getResultJavaTypeDescriptor() {
		return getReferencedMappingType().getJavaTypeDescriptor();
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public FetchableContainer getReferencedMappingContainer() {
		return fetchContainer;
	}

	@Override
	public List<Fetch> getFetches() {
		return fetches == null ? Collections.emptyList() : Collections.unmodifiableList( fetches );
	}

	@Override
	public Fetch findFetch(String fetchableName) {
		if ( fetches != null ) {
			for ( Fetch fetch : fetches ) {
				if ( fetch.getFetchedMapping().getFetchableName().equals( fetchableName ) ) {
					return fetch;
				}
			}
		}
		return null;
	}
}
