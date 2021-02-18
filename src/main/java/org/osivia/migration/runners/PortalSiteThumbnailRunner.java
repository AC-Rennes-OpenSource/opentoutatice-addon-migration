/**
 * 
 */
package org.osivia.migration.runners;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;
import org.osivia.migration.service.rest.BatchMode;

import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;


/**
 * @author david
 */
public class PortalSiteThumbnailRunner extends AbstractRunner {

    private static final Log log = LogFactory.getLog(PortalSiteThumbnailRunner.class);

    /** PortalSite with picture query. */
    private static final String LIVE_PSP_QUERY = "SELECT * FROM PortalSite WHERE ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:currentLifeCycleState <> 'deleted' "
            + "and ttcn:picture/data is not null and ttc:vignette/data is null";

    private static final String PROXY_PSP_RECOVERY_QUERY = "SELECT * FROM PortalSite WHERE ecm:isProxy = 1 AND ecm:currentLifeCycleState <> 'deleted' "
            + "and ttcn:picture/data is not null and ttc:vignette/data is null";

    private static final AutomationService service = Framework.getService(AutomationService.class);

    public PortalSiteThumbnailRunner(CoreSession session) {
        super(session);
    }

    @Override
    public int setInputs(int limit) {
        String query = BatchMode.recovery.name().equals(this.getMode()) ? PROXY_PSP_RECOVERY_QUERY : LIVE_PSP_QUERY;
        this.inputs = this.session.query(query, limit);
        this.inputs = removeDocsWithError(this.inputs);

        int size = this.inputs.size();
        this.totalInputs += size;

        return size;
    }

    @Override
    public void run() throws ClientException {
        if (!BatchMode.recovery.name().equals(this.getMode())) {
            for (DocumentModel ps : this.inputs) {
                try {
                    log.info(String.format("Treating PS: [%s]", ps.getPathAsString()));
                    // Set ttcn:picture to ttc:vignette resizing it
                    DocumentModel treatedPs = resizePictureToThumbnail(ps);
                    if (treatedPs != null) {
                        treatedPs = this.session.saveDocument(treatedPs);

                        DocumentModel proxy = ToutaticeDocumentHelper.getProxy(this.session, treatedPs, null);
                        if (proxy != null) {
                            log.debug(String.format("Published: treating last version of [%s]...", treatedPs.getPathAsString()));

                            DocumentModel version = this.session.getSourceDocument(proxy.getRef());
                            if (version != null && version.isVersion()) {
                                version.putContextData(CoreSession.ALLOW_VERSION_WRITE, Boolean.TRUE);
                                version = resizePictureToThumbnail(version);
                                this.session.saveDocument(version);
                            } else {
                                log.warn(String.format("PortalSite [%s] is in incoherent state: published but has no latest version",
                                        treatedPs.getPathAsString()));
                            }
                        }
                        // Persist unitary
                        this.session.save();
                    }
                } catch (Exception e) {
                    // Store
                    docsOnError.add(ps.getId());
                    throw new ClientException(e);
                }
            }
        } else {
            log.info("======[Recovery mode]=====");
            for (DocumentModel ps : this.inputs) {
                try {
                    log.info(String.format("Treating PS: [%s]", ps.getPathAsString()));
                    DocumentModel workingCopy = this.session.getWorkingCopy(ps.getRef());
                    workingCopy = resizePictureToThumbnail(workingCopy);
                    this.session.saveDocument(workingCopy);
                    
                    DocumentModel version = this.session.getSourceDocument(ps.getRef());
                    if (version != null && version.isVersion()) {
                        version.putContextData(CoreSession.ALLOW_VERSION_WRITE, Boolean.TRUE);
                        version = resizePictureToThumbnail(version);
                        this.session.saveDocument(version);
                    } else {
                        log.warn(String.format("PortalSite [%s] is in incoherent state: published but has no version", ps.getPathAsString()));
                    }
                    // Persist unitary
                    this.session.save();
                } catch (Exception e) {
                    // Store
                    docsOnError.add(ps.getId());
                    throw new ClientException(e);
                }
            }
        }
    }

    protected DocumentModel resizePictureToThumbnail(DocumentModel ps) {
        DocumentModel res = null;

        OperationContext ctx = new OperationContext(this.session);

        ctx.setInput(ps);
        Map<String, Object> params = new HashMap<>();
        params.put("img_heidth", 100);
        params.put("img_width", 100);
        params.put("xpath_img_in", "ttcn:picture");
        params.put("xpath_img_out", "ttc:vignette");
        params.put("enlarge", true);

        try {
            res = (DocumentModel) service.run(ctx, "ImageResize.Operation", params);
        } catch (Exception e) {
            throw new ClientException(e);
        }

        return res;
    }

    protected void setOnLine(DocumentModel treatedPs) {
        OperationContext ctx = new OperationContext(this.session);
        ctx.setInput(treatedPs);

        Map<String, Object> params = new HashMap<>();

        try {
            service.run(ctx, "Document.SetOnLineOperation", params);
        } catch (Exception e) {
            throw new ClientException(e);
        }

    }

}
