/**
 * 
 */
package org.osivia.migration.runners;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.osivia.migration.service.rest.BatchMode;
import org.osivia.migration.transaction.LauncherTransactionHelper;

import fr.toutatice.ecm.platform.core.helper.ToutaticeSilentProcessRunnerHelper;

/**
 * @author david
 *
 */
public abstract class AbstractRunner extends ToutaticeSilentProcessRunnerHelper {

    private static final Log log = LogFactory.getLog(AbstractRunner.class);

    /** Done migration status. */
    public static final String MIGRATION_DONE = "done";

    /** Default filter */
    public static final String FIXED_CLAUSE = " ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:currentLifeCycleState != 'deleted'";

    /** Empty id filter */
    public static final String EMPTY_ID_FILTER = "ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:currentLifeCycleState != 'deleted'";

    /** Silent services. */
    public static final List<Class<?>> FILTERED_SERVICES_LIST = new ArrayList<Class<?>>() {

        private static final long serialVersionUID = 1L;

        {
            add(EventService.class);
            add(VersioningService.class);
        }
    };

    /** Batch mode: analyze or execute. Default value: analyze */
    protected String mode = BatchMode.analyze.name();

    /** Documents to treat. */
    protected DocumentModelList inputs;
    /** Rows to treat. */
    protected IterableQueryResult results;
    /** Param. */
    protected String params;

    protected int totalInputs;
    protected int treatedInputs;

    /** Documents on error. */
    protected static List<String> docsOnError = new ArrayList<>();


    public int getTotalInputs() {
        return totalInputs;
    }


    public int getTreatedInputs() {
        return treatedInputs;
    }

    /**
     * Setter for documents to treat.
     */
    public abstract int setInputs(int limit);

    public AbstractRunner(CoreSession session) {
        super(session);
    }

    /**
     * @return the mode
     */
    public String getMode() {
        return mode;
    }

    /**
     * @param mode the mode to set
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * Setter for param.
     */
    public void setParams(String params) {
        this.params = params;
    }

    /** Getter for docs on error. */
    public static List<String> getDocsOnError() {
        return docsOnError;
    }

    /** Reset docs on errors. */
    public static void resetDocsOnerror() {
        docsOnError.clear();
    }

    /**
     * Remove documents with errors from inputs.
     */
    protected DocumentModelList removeDocsWithError(DocumentModelList inputs) {
        // Remove docs on error
        Iterator<DocumentModel> iterator = inputs.iterator();
        while (iterator.hasNext()) {
            String id = iterator.next().getId();
            if (docsOnError.contains(id)) {
                iterator.remove();
            }
        }

        return inputs;
    }

    /**
     * Executes runner in one transaction.
     */
    public void runSilentlyInTx(boolean unrestricted) {
        LauncherTransactionHelper.checkNStartTransaction();
        try {
            this.silentRun(unrestricted, AbstractRunner.FILTERED_SERVICES_LIST);
        } catch (Exception e) {
            LauncherTransactionHelper.setTransactionRollbackOnly();
            log.error(e);
        } finally {
            LauncherTransactionHelper.commitOrRollbackTransaction();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract void run() throws ClientException;

}
