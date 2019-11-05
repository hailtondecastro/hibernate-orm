package org.hibernate.orm.test.metamodel.mapping.components.relations.hhhyyyyy;

import java.util.Set;

import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

@Embeddable
public class MasterCompComp {
	@OneToMany(cascade={}, orphanRemoval=true, fetch=FetchType.LAZY) 
	//@OrderBy("DETL_ID")
	@JoinColumns({
		@JoinColumn(name="DUMMY_COL_MasterCompComp_01")
	})
	private Set<DetailEnt> detailEntCol;

	public Set<DetailEnt> getDetailEntCol() {
		return detailEntCol;
	}

	public void setDetailEntCol(Set<DetailEnt> detailEntCol) {
		this.detailEntCol = detailEntCol;
	}
}
