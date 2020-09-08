/**
 * 
 */
package org.osivia.migration.persistence.ejb;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.nuxeo.ecm.platform.ec.placeful.Annotation;

/**
 * @author david
 *
 */
@Entity
public class OttcMigrations extends Annotation {
	
	private static final long serialVersionUID = 7813150494742367379L;
	
	/** Id of migration. */
	private String mId;
	
	/** Entity name. */
	public final static String NAME = "OttcMigrations";

	/** Version of migration module. */
	@Column(name = "MIGRATION_VERSION", nullable = false)
	private String migrationVersion;
	
	/** Status of migration. */
	@Column(name = "MIGRATION_STATUS", nullable = true)
	private String status;
	
	/**
	 * Default constructor;
	 */
	public OttcMigrations(){};
	
	/**
	 * Constructor
	 * @param id
	 * @param status
	 */
	public OttcMigrations(String id, String migrationVersion){
		this.mId = id;
		this.migrationVersion = migrationVersion;
	}
	
	/**
	 * @return the id
	 */
	@Id
	public String getId() {
		return this.mId;
	}
	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.mId = id;
	}

	/**
	 * @return the migrationVersion
	 */
	public String getMigrationVersion() {
		return migrationVersion;
	}

	/**
	 * @param migrationVersion the migrationVersion to set
	 */
	public void setMigrationVersion(String migrationVersion) {
		this.migrationVersion = migrationVersion;
	}
	
    /**
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    
    /**
     * @param status the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
			return "MigrationsBean (".concat(this.id == null ? "none" : this.id).concat("|")
					.concat(this.migrationVersion == null ? "none" : this.migrationVersion).concat(")");
    }

}
