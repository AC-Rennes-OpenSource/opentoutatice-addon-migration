/**
 * 
 */
package org.osivia.migration.runners;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.trash.TrashService;
import org.nuxeo.runtime.api.Framework;
import org.osivia.migration.service.rest.BatchMode;


/**
 * @author david
 */
public class AgriUserWorkspacesRunner extends AbstractRunner {
    
    private static final Log log = LogFactory.getLog(AgriUserWorkspacesRunner.class);

    private static final String UW_QUERY = "select * from UserWorkspace where ecm:name like '%-agri' and ".concat(AbstractRunner.FIXED_CLAUSE);

    public AgriUserWorkspacesRunner(CoreSession session) {
        super(session);
    }

    @Override
    public int setInputs(int limit) {
        this.inputs = this.session.query(UW_QUERY, limit);
        this.inputs = removeDocsWithError(this.inputs);

        int size = this.inputs.size();
        this.totalInputs += size;

        return size;
    }

    @Override
    public void run() throws ClientException {
        TrashService trashService = Framework.getService(TrashService.class);
        
        for (DocumentModel ws : this.inputs) {
            try {
                if(!BatchMode.analyze.name().equals(this.getMode())) {
                    log.info(String.format("About to put in trash [%s]", ws.getPathAsString()));
                    trashService.trashDocuments(Arrays.asList(ws));
                    log.info(String.format("-> [%s] put in trash", ws.getPathAsString()));
                } else {
                    log.info(String.format("UserWorkspace [%s] will be put in trash", ws.getPathAsString()));
                    docsOnError.add(ws.getId());
                }
            } catch (Exception e) {
                // Store
                docsOnError.add(ws.getId());
                throw new ClientException(e);
            }
        }
    }

}
