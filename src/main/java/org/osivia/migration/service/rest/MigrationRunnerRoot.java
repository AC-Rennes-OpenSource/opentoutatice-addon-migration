/**
 * 
 */
package org.osivia.migration.service.rest;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.webengine.jaxrs.session.SessionFactory;
import org.nuxeo.runtime.api.Framework;
import org.osivia.migration.service.RunnerDescriptor;
import org.osivia.migration.service.RunnerLauncherService;


/**
 * @author david
 *
 */
@Path("migration")
public class MigrationRunnerRoot {

    public static Log log = LogFactory.getLog(MigrationRunnerRoot.class);

    @Context
    protected HttpServletRequest request;

    protected CoreSession coreSession;

    private static RunnerLauncherService rs;

    protected static RunnerLauncherService getRunnerLauncherService() {
        if (rs == null) {
            rs = Framework.getService(RunnerLauncherService.class);
        }
        return rs;
    }

    @GET
    public String get(@QueryParam("name") String runnerName, @QueryParam("batchSize") int batchSize, @QueryParam("mode") String mode,
            @QueryParam("params") String params) {
        // CoreSession
        this.coreSession = SessionFactory.getSession(request);

        Principal principal = coreSession.getPrincipal();
        if (!(principal instanceof NuxeoPrincipal)) {
            return "<h4>Unauthorized: you must be administrator of application (\"SuperAdmin\").</h4>";
        }
        NuxeoPrincipal nuxeoPrincipal = (NuxeoPrincipal) principal;
        if (!nuxeoPrincipal.isAdministrator()) {
            return "<h4>Unauthorized: you must be administrator of application (\"SuperAdmin\").</h4>";
        }

        // Check of batchSize
        if (batchSize > 0 && batchSize < 100) {
            return "<h4>Parameter batchSize must be greater or equals to 100.</h4>";
        }

        // Get runner
        RunnerDescriptor runner = getRunnerLauncherService().getRunner(runnerName);
        if (runner == null) {
            return "<h2>Migration module: " + runnerName + " does not exist!</h2>";
        }

        // Migration runner
        int bS = batchSize > 0 ? batchSize : -1;
        getRunnerLauncherService().execute(this.coreSession, runner, bS, mode, params);

        return "Migration bacth " + runnerName + " launched.<br/>" + "You can consult {nuxeo.dir.log}/migration.log if activated.";
    }
}
