package org.hibernate.orm.test.metamodel.mapping.components.relations.hhhyyyyy;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name="DETAIL")
public class DetailEnt {
	@Id
	@Column(name="DETL_ID")
	private Integer dtId;
	@Column(name="DETL_VCHAR_A")
	private String vcharA;
	
	public Integer getDtId() {
		return dtId;
	}
	
	public void setDtId(Integer dtId) {
		this.dtId = dtId;
	}
	
	public String getVcharA() {
		return vcharA;
	}
	
	public void setVcharA(String vcharA) {
		this.vcharA = vcharA;
	}
}
