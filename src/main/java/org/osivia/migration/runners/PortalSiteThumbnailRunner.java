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

import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;


/**
 * @author david
 */
public class PortalSiteThumbnailRunner extends AbstractRunner {

    private static final Log log = LogFactory.getLog(PortalSiteThumbnailRunner.class);

    /** PortalSite with picture query. */
    private static final String LIVE_PSP_QUERY = "SELECT ecm:uuid FROM PortalSite WHERE ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:currentLifeCycleState <> 'deleted' "
            + "and ttcn:picture/data is not null and ttc:vignette/data is null";

    private static final AutomationService service = Framework.getService(AutomationService.class);

    public PortalSiteThumbnailRunner(CoreSession session) {
        super(session);
    }

    @Override
    public int setInputs(int limit) {
        this.inputs = this.session.query(LIVE_PSP_QUERY, limit);

        int size = this.inputs.size();
        this.totalInputs += size;

        return size;
    }

    @Override
    public void run() throws ClientException {
        for (DocumentModel ps : this.inputs) {
            // Set ttcn:picture to ttc:vignette resizing it
            DocumentModel treatedPs = resizePictureToThumbnail(ps);
            if (treatedPs != null) {
                this.session.saveDocument(treatedPs);
                // Publish if necessary
                if (ps.hasFacet("isLocalPublishLive")) {
                    String liveVersionLabel = ps.getVersionLabel();

                    DocumentModel proxy = ToutaticeDocumentHelper.getProxy(this.session, treatedPs, null);
                    String proxyVersionLabel = proxy.getVersionLabel();

                    if (StringUtils.equals(liveVersionLabel, proxyVersionLabel)) {
                        // set onLine teated live
                        setOnLine(treatedPs);
                    } else {
                        // Live is different from proxy: treat (latest) version
                        log.debug(String.format("Treating last version of [%s]...", treatedPs.getPathAsString()));

                        DocumentModel latestVersion = ToutaticeDocumentHelper.getLatestDocumentVersion(ps, this.session);
                        latestVersion.putContextData(CoreSession.ALLOW_VERSION_WRITE, Boolean.TRUE);
                        latestVersion = resizePictureToThumbnail(latestVersion);
                        this.session.saveDocument(latestVersion);
                    }
                }
                // Persist unitary
                this.session.save();
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
