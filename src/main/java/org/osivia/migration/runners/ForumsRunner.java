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
import org.nuxeo.ecm.core.api.DocumentModelIterator;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventListenerDescriptor;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.runtime.api.Framework;

import fr.toutatice.ecm.platform.collab.tools.constants.CollabToolsConstants;
import fr.toutatice.ecm.platform.collab.tools.forum.ThreadUpdateListener;


/**
 * @author david
 *
 */
public class ForumsRunner extends AbstractRunner {

    /** Logger. */
    private static final Log log = LogFactory.getLog(ForumsRunner.class);

    /** Forums query. */
    private static final String FORUMS_QUERY = "select ecm:uuid from Forum where ecm:isVersion = 0";

    /** Event service. */
    private static EventService eventService;

    /** CommentManager. */
    protected static CommentManager commentManager;

    public ForumsRunner(CoreSession session) {
        super(session);
    }

    @Override
    public int setInputs(int limit) {
        this.results = this.session.queryAndFetch(FORUMS_QUERY, NXQL.NXQL, new Object[0]);
        return new Long(this.results.size()).intValue();
    }

    /**
     * Getter for EventService.
     */
    protected EventService getEventService() {
        if (eventService == null) {
            eventService = Framework.getService(EventService.class);
        }
        return eventService;
    }

    /**
     * 
     */
    @Override
    public void run() throws ClientException {
        try {

            if (this.results != null) {
                // Iterate
                Iterator<Map<String, Serializable>> iterator = this.results.iterator();
                while (iterator.hasNext()) {
                    try {

                        // Get Threads
                        String forumId = (String) iterator.next().get("ecm:uuid");

                        if (log.isDebugEnabled()) {
                            log.debug("[Forum: " + forumId + "] treating");
                        }

                        DocumentModelIterator threadIterator = this.session.getChildrenIterator(new IdRef(forumId), "Thread");

                        // Treatment of Threads
                        if (threadIterator != null) {
                            while (threadIterator.hasNext()) {
                                DocumentModel thread = threadIterator.next();

                                if (CollabToolsConstants.THREAD_TYPE.equals(thread.getType())) {

                                    if (log.isDebugEnabled()) {
                                        log.debug("[Thread: " + thread.getTitle() + "] treating");
                                    }

                                    // Use of existing treatment in ThreadUpdateListener
                                    EventListenerDescriptor threadUpdater = getEventService().getEventListener("ottcThreadUpdateListener");
                                    EventListener threadUpdateListener = threadUpdater.asEventListener();

                                    // Update of thread
                                    ((ThreadUpdateListener) threadUpdateListener).updateAnswersOfThread(thread, this.session);

                                    if (log.isDebugEnabled()) {
                                        log.debug("[Thread: " + thread.getTitle() + "] treated");
                                    }

                                }

                            }
                        }

                        if (log.isDebugEnabled()) {
                            log.debug("[Forum: " + forumId + "] treated");
                        }

                    } catch (Exception e) {
                        throw new ClientException(e);
                    }
                }
            }

        } finally {
            if (this.results != null) {
                this.results.close();
            }
        }

    }

}
