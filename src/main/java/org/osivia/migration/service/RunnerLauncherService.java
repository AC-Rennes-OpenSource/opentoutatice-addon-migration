/**
 * 
 */
package org.osivia.migration.service;

import org.nuxeo.ecm.core.api.CoreSession;


/**
 * @author david
 *
 */
public interface RunnerLauncherService {

    /** Atomical size transaction. */
    int ATOMIC_SIZE_TRANSACTION = 100;
    /** Limit request size. */
    int INPUTS_SIZE = ATOMIC_SIZE_TRANSACTION;

    RunnerDescriptor getRunner(String id);

    void execute(CoreSession session, RunnerDescriptor runnerDescriptor, int batchSize, String mode, String params);

}
