package org.hibernate.orm.test.metamodel.mapping.components.relations.hhhyyyyy;

import javax.persistence.AssociationOverride;
import javax.persistence.AssociationOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name="MASTER")
public class MasterEnt {
	@Id()
	@Column(name="MAST_ID")
	private Integer mtId;
	@Column(name="MAST_VCHAR_A")
	private String vcharA;
	@Embedded
	@AssociationOverrides({
		@AssociationOverride(
				name="detailEntCol", 
				joinColumns={
						@JoinColumn(name="DETL_MAST_ID_COMP")
				}),
		@AssociationOverride(
				name="masterCompComp.detailEntCol", 
				joinColumns={
						@JoinColumn(name="DETL_MAST_ID_COMP_COMP")
				}),
	})
	private MasterComp masterComp;
	
	public Integer getMtId() {
		return mtId;
	}
	
	public void setMtId(Integer mtId) {
		this.mtId = mtId;
	}
	
	public String getVcharA() {
		return vcharA;
	}
	
	public void setVcharA(String vcharA) {
		this.vcharA = vcharA;
	}
	
	public MasterComp getMasterComp() {
		return masterComp;
	}
	
	public void setMasterComp(MasterComp masterComp) {
		this.masterComp = masterComp;
	}	
}
