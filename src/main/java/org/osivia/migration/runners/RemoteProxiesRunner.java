/**
 * 
 */
package org.osivia.migration.runners;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * @author david
 *
 */
public class RemoteProxiesRunner extends AbstractRunner {

    private static Log log = LogFactory.getLog(RemoteProxiesRunner.class);

    /**
     * Remote proxies in publish spaces.
     * (clause is '%.remote.proxy.%' to take incoherent remote proxies -i.e with timestamp so copied?)
     */
    private static final String REMOTE_ACRENNES_PROXIES_QUERIES = "select * from Document where ecm:isProxy = 1 and ecm:name like '%.remote.proxy%' and ecm:mixinType <> 'isRemoteProxy' and ecm:isVersion = 0 and ecm:currentLifeCycleState != 'deleted'";
    /**
     * Remote proxies (no local proxies).
     * (clause is '%.proxy.%' to take incoherent local proxies -i.e with timestamp so copied?)
     */
    private static final String NO_LOCAL_PROXIES_QUERIES = "select * from Document where ecm:isProxy = 1 and ecm:name not like '%.proxy%' and ecm:mixinType <> 'isRemoteProxy' and ecm:isVersion = 0 and ecm:currentLifeCycleState != 'deleted'";

    public RemoteProxiesRunner(CoreSession session) {
        super(session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int setInputs(int limit) {
        int size = 0;
        // Remote proxies in Publish Spaces
        this.inputs = this.session.query(REMOTE_ACRENNES_PROXIES_QUERIES, limit);
        if (this.inputs.size() > 0) {
            size = this.inputs.size();
        } else {
            // Remote proxies
            this.inputs = this.session.query(NO_LOCAL_PROXIES_QUERIES, limit);
            size = this.inputs.size();
        }

        this.totalInputs += size;
        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() throws ClientException {
        for (DocumentModel remoteProxy : this.inputs) {
            if (!remoteProxy.hasFacet("isRemoteProxy")) {
                remoteProxy.addFacet("isRemoteProxy");

                this.session.saveDocument(remoteProxy);
                this.treatedInputs += 1;

                if (log.isDebugEnabled()) {
                    log.debug(remoteProxy.getPathAsString() + " migrated");
                }
            }
        }
        this.session.save();
    }

}
