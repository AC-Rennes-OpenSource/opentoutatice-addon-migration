/**
 * 
 */

package org.osivia.migration.runners;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.query.sql.NXQL;


/**
 * @author david
 */
public class LocalLivesRunner extends AbstractRunner {

    private static final Log log = LogFactory.getLog(LocalLivesRunner.class);

    /** Publish spaces query. */
    private static final String PS_QUERY = "select * from Document where ecm:mixinType = 'TTCPublishSpace' and ecm:mixinType <> 'isLocalPublishLive' and "
            + "ecm:isProxy = 0 AND ecm:isVersion = 0";

    public LocalLivesRunner(CoreSession session) {
        super(session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int setInputs(int limit) {
        int size = 0;

        IterableQueryResult publishSpacesIds = null;
        try {
            publishSpacesIds = getPublishSpaces(this.session);
            this.inputs = getLocalLives(this.session, publishSpacesIds, limit);
            size = this.inputs.size();

            if (size == 0) {
                this.inputs = this.session.query(PS_QUERY, limit);
                size = this.inputs.size();
            }

            this.totalInputs += size;
        } finally {
            if (publishSpacesIds != null) {
                publishSpacesIds.close();
            }
        }

        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() throws ClientException {
        for (DocumentModel localLive : this.inputs) {
            if (!localLive.hasFacet("isLocalPublishLive")) {
                if (localLive.addFacet("isLocalPublishLive")) {
                    this.session.saveDocument(localLive);
                    this.treatedInputs += 1;

                    if (log.isDebugEnabled()) {
                        log.debug(localLive.getPathAsString() + " migrated");
                    }
                }
            }
        }

        // BDD persist
        this.session.save();
    }

    /**
     * @param session
     * @return the plush spaces of repository.
     */
    private IterableQueryResult getPublishSpaces(CoreSession session) {
        String request = "select ecm:uuid from Document where %s and ecm:mixinType = 'TTCPublishSpace'";
        String query = String.format(request, FIXED_CLAUSE);
        return session.queryAndFetch(query, NXQL.NXQL, new Object[0]);
    }

    /**
     * @param session
     * @param publishSpaces
     * @return local lives of given publish spaces.
     */
    private DocumentModelList getLocalLives(CoreSession session, IterableQueryResult publishSpacesIds, int limit) {
        this.inputs = new DocumentModelListImpl(0);

        if (publishSpacesIds != null) {
            Iterator<Map<String, Serializable>> iterator = publishSpacesIds.iterator();

            int totalRes = 0;
            while (iterator.hasNext() && totalRes < limit) {
                Map<String, Serializable> next = iterator.next();

                if (next != null) {
                    String request = String.format("select * from Document where %s and ecm:mixinType <> 'isLocalPublishLive' and ecm:ancestorId = '%s' ",
                            FIXED_CLAUSE, (String) next.get("ecm:uuid"));
                    DocumentModelList res = session.query(request, limit);

                    if (res.size() > 0) {
                        this.inputs.addAll(res);
                        totalRes += res.size();
                    }
                }
            }
        }

        return this.inputs;
    }

    /**
     * @param publishSpacesIds
     * @return the spaceIds set for query.
     */
    @Deprecated
    private String getSpaceIdsSet(IterableQueryResult publishSpacesIds) {
        StringBuffer set = new StringBuffer();

        if (publishSpacesIds != null) {
            Iterator<Map<String, Serializable>> iterator = publishSpacesIds.iterator();
            if (iterator.hasNext()) {
                set.append("(");

                while (iterator.hasNext()) {
                    Map<String, Serializable> row = iterator.next();
                    set.append("'").append(row.get("ecm:uuid")).append("'");

                    if (iterator.hasNext()) {
                        set.append(",");
                    }
                }

                set.append(")");
            }
        }


        return set.toString();
    }

}
