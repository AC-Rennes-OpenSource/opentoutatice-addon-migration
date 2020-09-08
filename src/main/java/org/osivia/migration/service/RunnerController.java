/**
 * 
 */
package org.osivia.migration.service;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationServiceHelper;
import org.nuxeo.ecm.platform.ec.placeful.PlacefulServiceImpl;
import org.nuxeo.ecm.platform.ec.placeful.interfaces.PlacefulService;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.osivia.migration.persistence.ejb.OttcMigrations;
import org.osivia.migration.runners.AbstractRunner;
import org.osivia.migration.transaction.LauncherTransactionHelper;

/**
 * @author david
 *
 */
public class RunnerController {

    private final static Log log = LogFactory.getLog(RunnerController.class);

    /**
     * Utility class.
     */
    private RunnerController() {
        super();
    }

    /**
     * @param runnerDescriptor
     * @return true if the runner has to be execute.
     */
    public static boolean needToMigrate(final RunnerDescriptor runnerDescriptor) {
        boolean needs = false;

        // Check migration yet done: check DB table ottcMigrations
        LauncherTransactionHelper.checkNStartTransaction();

        try {
            PlacefulService pfService = NotificationServiceHelper.getPlacefulServiceBean();

            OttcMigrations ottcMigrations;
            try {
                ottcMigrations = (OttcMigrations) pfService.getAnnotation(runnerDescriptor.getRunnerId(), OttcMigrations.NAME);

                // Check status
                needs = runnerDescriptor.isForce() || !AbstractRunner.MIGRATION_DONE.equalsIgnoreCase(ottcMigrations.getStatus());

            } catch (NoResultException nre) {
                // Not yet done: table creation
                ottcMigrations = new OttcMigrations(runnerDescriptor.getRunnerId(), runnerDescriptor.getMigrationVersion());
                pfService.setAnnotation(ottcMigrations);

                needs = true;
            }

        } catch (Exception e) {
            LauncherTransactionHelper.setTransactionRollbackOnly();
            logStackTrace(log, e);
            needs = false;
        } finally {
            LauncherTransactionHelper.commitOrRollbackTransaction();
        }

        return needs;
    }

    /**
     * Checks currentVersion >= migrationVersion.
     * 
     * @param runnerDescriptor
     * @return boolean
     */
    // private static boolean checkVersions(final RunnerDescriptor runnerDescriptor) {
    // // Needs
    // boolean needs = runnerDescriptor.isForce();
    //
    // if (!needs) {
    // // Check Ottc versions
    // String currentVersion = Framework.getProperty(AbstractRunner.OTTC_VERSION_PROPERTY);
    //
    // // Current version must be defined
    // if (StringUtils.isBlank(currentVersion)) {
    // log.error("No property 'ottc.version' is defined in nuxeo.conf: migration ".concat(runnerDescriptor.getRunnerId().concat(" will not be done")));
    // needs = false;
    // } else {
    // String migrationVersion = runnerDescriptor.getMigrationVersion();
    // needs = currentVersion.compareToIgnoreCase(migrationVersion) >= 0;
    // }
    // }
    //
    // return needs;
    // }


    /**
     * Store that migration (with runnerId) is done.
     * 
     * @param em
     * @param runnerId
     */
    protected static void storeMigrationStatus(RunnerDescriptor runnerDescriptor) {
        // LauncherTransactionHelper.checkNStartTransaction();
        //
        // try {
        // PlacefulService pfService = NotificationServiceHelper.getPlacefulServiceBean();
        //
        // OttcMigrations ottcMigrations;
        // try {
        // ottcMigrations = (OttcMigrations) pfService.getAnnotation(runnerDescriptor.getRunnerId(), OttcMigrations.NAME);
        // // Store done status
        // ottcMigrations.setStatus(AbstractRunner.MIGRATION_DONE);
        // TransactionHelper.commitOrRollbackTransaction();
        //
        // } catch (Exception e) {
        // LauncherTransactionHelper.setTransactionRollbackOnly();
        // logStackTrace(log, e);
        // }
        // } finally {
        // LauncherTransactionHelper.commitOrRollbackTransaction();
        // }

        // ====================

        LauncherTransactionHelper.checkNStartTransaction();

        boolean isTx = TransactionHelper.isTransactionActive();
        if (!isTx) {
            isTx = TransactionHelper.startTransaction();
        }
        try {

            PlacefulService pfService = NotificationServiceHelper.getPlacefulServiceBean();

            EntityManager entityManager = ((PlacefulServiceImpl) pfService).getOrCreatePersistenceProvider().acquireEntityManagerWithActiveTransaction();
            OttcMigrations notUpdatedEntity = entityManager.getReference(OttcMigrations.class, runnerDescriptor.getRunnerId());
            entityManager.remove(notUpdatedEntity);
            if (isTx) {
                LauncherTransactionHelper.commitOrRollbackTransaction();
            }

            if (!TransactionHelper.isTransactionActive()) {
                LauncherTransactionHelper.checkNStartTransaction();
                if (!TransactionHelper.isTransactionActive()) {
                    isTx = TransactionHelper.startTransaction();
                }
            }

            OttcMigrations ottcMigrations = new OttcMigrations(runnerDescriptor.getRunnerId(), runnerDescriptor.getMigrationVersion());
            ottcMigrations.setStatus(AbstractRunner.MIGRATION_DONE);
            pfService.setAnnotation(ottcMigrations);

            if (isTx) {
                LauncherTransactionHelper.commitOrRollbackTransaction();
            }

        } catch (Exception e) {
            if (isTx) {
                LauncherTransactionHelper.setTransactionRollbackOnly();
            }
            log.error(e);
        }

    }

    /**
     * Logs stack trace in server.log.
     * 
     * @param log
     * @param e
     */
    private static void logStackTrace(Log log, Throwable t) {

        StringWriter stringWritter = new StringWriter();
        PrintWriter printWritter = new PrintWriter(stringWritter, true);
        t.printStackTrace(printWritter);
        printWritter.flush();
        stringWritter.flush();

        log.error("[Error]: " + stringWritter.toString());

    }

}
