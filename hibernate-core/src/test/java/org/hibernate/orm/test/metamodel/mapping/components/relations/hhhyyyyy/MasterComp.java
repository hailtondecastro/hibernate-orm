package org.hibernate.orm.test.metamodel.mapping.components.relations.hhhyyyyy;

import java.util.Set;

import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Version;

@Embeddable
public class MasterComp {
	@OneToMany(cascade={}, fetch=FetchType.LAZY) 
	//@OrderBy("DETL_ID")
	@JoinColumns({
		@JoinColumn(name="DUMMY_COL_MasterComp_01")
	})
	private Set<DetailEnt> detailEntCol;
	@Embedded
	private MasterCompComp masterCompComp;
	public Set<DetailEnt> getDetailEntCol() {
		return detailEntCol;
	}
	public void setDetailEntCol(Set<DetailEnt> detailEntCol) {
		this.detailEntCol = detailEntCol;
	}
	public MasterCompComp getMasterCompComp() {
		return masterCompComp;
	}
	public void setMasterCompComp(MasterCompComp masterCompComp) {
		this.masterCompComp = masterCompComp;
	}
	
	
}
