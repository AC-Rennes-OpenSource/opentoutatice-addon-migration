/**
 * 
 */

package org.osivia.migration.service;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.osivia.migration.runners.AbstractRunner;
import org.osivia.migration.service.rest.BatchMode;
import org.osivia.migration.transaction.LauncherTransactionHelper;

/**
 * @author david
 */
public class RunnerLauncherServiceImpl extends DefaultComponent implements RunnerLauncherService {

    private static final Log log = LogFactory.getLog(RunnerLauncherServiceImpl.class);

    /** Extension point. */
    private static final String RUNNERS_PT_EXT = "runners";
    /** Map of runners. */
    private static final Map<String, RunnerDescriptor> runnersDescriptors = new HashMap<String, RunnerDescriptor>();

    /**
     * {@inheritDoc}
     */
    @Override
    public int getApplicationStartedOrder() {
        return 9999;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) throws Exception {
        if (RUNNERS_PT_EXT.equals(extensionPoint)) {
            RunnerDescriptor runnerDescriptor = (RunnerDescriptor) contribution;
            runnersDescriptors.put(runnerDescriptor.getRunnerId(), runnerDescriptor);
        }
    }

    /**
     * @param runnerDescriptor
     */
    @Override
    public void execute(CoreSession session, RunnerDescriptor runnerDescriptor, int batchSize, String mode, String params) {
        if (runnerDescriptor.isEnabled()) {
            try {
                boolean toMigrate = RunnerController.needToMigrate(runnerDescriptor);
                if (toMigrate) {

                    // Runner
                    String runnerClass = runnerDescriptor.getClazz();
                    Class<?> runnerC = Class.forName(runnerClass);

                    Class<?>[] constructorParams = {CoreSession.class};
                    Constructor<?> constructor = runnerC.getDeclaredConstructor(constructorParams);
                    AbstractRunner runner = (AbstractRunner) constructor.newInstance(session);

                    // Mode
                    runner.setMode(mode);

                    // Possible parameter
                    if (params != null) {
                        runner.setParams(params);
                    }

                    // Log timer
                    final long begin = System.currentTimeMillis();
                    // Batch parameters
                    if (batchSize > 0) {
                        // Run migration
                        runBatch(runnerDescriptor, batchSize, runner);
                    } else if (batchSize <= 0) {
                        // Run migration
                        runBatch(runnerDescriptor, -1, runner);
                    }

                    if (log.isDebugEnabled()) {
                        final long end = System.currentTimeMillis();
                        log.debug("[===== End of "
                                + runnerDescriptor.getRunnerId()
                                + " migration : ".concat(String.valueOf(runner.getTreatedInputs())).concat(" on ")
                                        .concat(String.valueOf(runner.getTotalInputs())).concat(" in ").concat(String.valueOf((end - begin) / 1000))
                                        .concat(" s =====]"));

                        if (AbstractRunner.getDocsOnError().size() > 0) {
                            log.debug("[===== Documents on error =====]: ");
                            for (String uid : AbstractRunner.getDocsOnError()) {
                                log.debug(uid);
                            }
                        }
                    }
                    // Reset docs on error
                    if (batchSize <= 0) {
                        AbstractRunner.resetDocsOnerror();
                    }

                    // Persist migration
                    if(!BatchMode.analyze.name().equals(runner.getMode())) {
                        RunnerController.storeMigrationStatus(runnerDescriptor);
                    }

                    if (log.isDebugEnabled()) {
                        String status = toMigrate ? "Ended" : "Yet done";
                        log.debug("[" + runnerDescriptor.getRunnerId() + "] " + status);
                    }
                } else {
                    log.info(String.format("Migration Runner [%s] yet done: nothing to do (you can set force to true in migration-config.xml file to re-run it)", 
                            runnerDescriptor.getRunnerId()));
                }
            } catch (Exception e) {
                log.error("[" + runnerDescriptor.getRunnerId() + "] ERROR: ", e);

                if (log.isDebugEnabled()) {
                    log.debug("[" + runnerDescriptor.getRunnerId() + "] ABORTED");
                }
            }

        } else {
            if(log.isInfoEnabled()) {
                log.info(String.format("Migration Runner [%s] is not enabled: no treatment will be done", runnerDescriptor.getRunnerId()));
            }
        }
    }

    /**
     * @param runnerDescriptor
     * @param batchSize
     * @param runner
     * @return
     */
    private boolean runBatch(RunnerDescriptor runnerDescriptor, int batchSize, AbstractRunner runner) {
        boolean stopBatchs = false;

        // Inputs
        int totalInputs = getInputs(runner, RunnerLauncherService.INPUTS_SIZE);

        if (totalInputs > 0) {

            if (log.isDebugEnabled()) {
                if (log.isDebugEnabled()) {
                    String msg = !StringUtils.equals(BatchMode.execute.name(), runner.getMode()) ? BatchMode.analyze.name() : StringUtils.EMPTY;
                    log.debug("[===== Beginning of " + runnerDescriptor.getRunnerId() + " migration " + msg + " =====]");
                }
            }

            if (batchSize > 0) {

                // Technical batch (for request time)
                while (totalInputs <= batchSize) {
                    // Excecute
                    runner.runSilentlyInTx(true);

                    // Next iteration
                    int inputs = getInputs(runner, RunnerLauncherService.INPUTS_SIZE);
                    if (inputs > 0) {
                        totalInputs += inputs;
                    } else {
                        // To stop
                        totalInputs += batchSize + 1;
                        stopBatchs = true;
                    }
                }

            } else if (batchSize == -1) {
                int inputs = totalInputs;

                while (inputs > 0) {
                    // Excecute
                    runner.runSilentlyInTx(true);

                    // Next iteration
                    inputs = getInputs(runner, RunnerLauncherService.INPUTS_SIZE);
                }

                if (inputs <= 0) {
                    // To stop
                    stopBatchs = true;
                }
            }
        } else {
            stopBatchs = true;
        }

        return stopBatchs;
    }

    /**
     * @param runner
     * @return
     */
    private int getInputs(AbstractRunner runner, int size) {
        LauncherTransactionHelper.checkNStartTransaction();
        int docsToTreat = 0;
        try {
            docsToTreat = runner.setInputs(size);
        } catch (Exception e) {
            LauncherTransactionHelper.setTransactionRollbackOnly();
            log.error(e);
        } finally {
            LauncherTransactionHelper.commitOrRollbackTransaction();
        }
        return docsToTreat;
    }

    @Override
    public RunnerDescriptor getRunner(String id) {
        return runnersDescriptors.get(id);
    }

}
