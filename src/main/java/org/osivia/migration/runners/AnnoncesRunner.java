/**
 * 
 */
package org.osivia.migration.runners;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.osivia.migration.service.rest.BatchMode;

import fr.toutatice.ecm.platform.core.freemarker.ToutaticeFunctions;


/**
 * To truncate dc:description.
 * 
 * @author david
 *
 */
public class AnnoncesRunner extends AbstractRunner {

    private static final Log log = LogFactory.getLog(AnnoncesRunner.class);

    /** Annonce created since 4.6 MEP. */
    private static final String LIVE_ANNONCES_QUERY = "select * from Annonce where ecm:isProxy = 0 AND ecm:isVersion = 0 and dc:created > timestamp '2017-10-25 00:00:00' and ecm:currentLifeCycleState <> 'deleted' order by dc:title";

    private static final String DC_DESCRIPTION = "dc:description";
    private static final int MAX_DESC_SIZE = 300;
    private static final String TRUNCATED_TEXT_SUFFIX = " ...";

    private int offset = 0;

    /** Helper function. */
    private static final ToutaticeFunctions fn = new ToutaticeFunctions();

    public AnnoncesRunner(CoreSession session) {
        super(session);
    }

    @Override
    public int setInputs(int limit) {
        int size = 0;

        this.inputs = this.session.query(LIVE_ANNONCES_QUERY, null, limit, this.offset, true);

        size = this.inputs.size();

        // Pagination
        this.offset += limit;

        return size;
    }

    @Override
    public void run() throws ClientException {
        boolean execMode = StringUtils.equals(BatchMode.execute.name(), super.getMode());

        for (DocumentModel doc : this.inputs) {
            // Truncate description
            String desc = (String) doc.getPropertyValue(DC_DESCRIPTION);
            
            if (desc != null && desc.length() > 0) {
                if (StringUtils.endsWith(desc, TRUNCATED_TEXT_SUFFIX)) {
                    desc = StringUtils.substringBeforeLast(desc, TRUNCATED_TEXT_SUFFIX);
                }

                if (desc.length() > MAX_DESC_SIZE) {
                    desc = fn.truncateTextFromHTML(desc, MAX_DESC_SIZE);

                    if (execMode) {
                        doc.setPropertyValue(DC_DESCRIPTION, desc);
                        this.session.saveDocument(doc);
                    }

                    this.treatedInputs += 1;
                    this.totalInputs += 1;

                    if (log.isDebugEnabled()) {
                        String msg = execMode ? " fixed " : org.apache.commons.lang.StringUtils.EMPTY;
                        log.debug("[Live]: " + doc.getPathAsString() + msg);

                        msg = execMode ? "Description fixed as: \n" : "Description wil be fixed as: \n";
                        log.debug(msg + desc);
                    }

                    // Treat versions
                    List<DocumentModel> versions = this.session.getVersions(doc.getRef());
                    if (!versions.isEmpty()) {

                        if (log.isDebugEnabled()) {
                            String msg = execMode ? "[Treating its versions]: " : "[Versions of " + doc.getName() + " to treat]: ";
                            log.debug(msg);
                        }

                        for (DocumentModel version : versions) {
                            version.putContextData(CoreSession.ALLOW_VERSION_WRITE, Boolean.TRUE);

                            if (execMode) {
                                version.setPropertyValue(DC_DESCRIPTION, desc);
                                this.session.saveDocument(version);
                            }

                            this.treatedInputs += 1;
                            this.totalInputs += 1;

                            if (log.isDebugEnabled()) {
                                String vLabel = version.getVersionLabel() != null ? version.getVersionLabel() : " - no version -";
                                String msg = execMode ? " fixed " : org.apache.commons.lang.StringUtils.EMPTY;
                                log.debug("[Version]: " + vLabel + msg);
                            }
                        }
                    }
                }
            }
        }

        if (execMode) {
            this.session.save();
        }

    }

}
