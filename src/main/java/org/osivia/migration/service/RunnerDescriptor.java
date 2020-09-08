/**
 * 
 */
package org.osivia.migration.service;

import java.io.Serializable;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * @author david
 *
 */
@XObject("runner")
public class RunnerDescriptor implements Serializable {

	private static final long serialVersionUID = 3312394453618746728L;

	/** Class of runner. */
	@XNode("@clazz")
	String clazz;

	/**
	 * @return the class of runner.
	 */
	public String getClazz() {
		return this.clazz;
	}
	
	/** Runner enabled. */
	@XNode("@enabled")
	private boolean enabled = true;
	
    /**
     * @return the enabled
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /** Runner forced. */
    @XNode("@force")
    private boolean force = false;

    /**
     * @return the force
     */
    public boolean isForce() {
        return this.force;
    }

    /** id of migration process. */
	@XNode("@runnerId")
	String runnerId;

	/**
	 * @return the id of migration process.
	 */
	public String getRunnerId() {
		return this.runnerId;
	}

	/** ottc versions before this version must be migrated */
	@XNode("@migrationVersion")
	String migrationVersion;

	/**
	 * @return the version
	 */
	public String getMigrationVersion() {
		return this.migrationVersion;
	}

}
