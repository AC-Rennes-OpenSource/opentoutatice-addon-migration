/**
 * 
 */
package org.osivia.migration.transaction;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.UserTransaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.runtime.transaction.TransactionRuntimeException;

/**
 * @author ftorchet
 *
 */
public class LauncherTransactionHelper {
	
	private static final Log log = LogFactory.getLog(LauncherTransactionHelper.class);
	
	/**
     * Various binding names for the UserTransaction. They depend on the
     * application server used and how the configuration is done.
     */
    public static final String[] UT_NAMES = { "java:comp/UserTransaction", // standard
            "java:comp/env/UserTransaction", // manual binding outside appserver
            "UserTransaction" // jboss
    };

	/**
	 * Utility class.
	 */
	private LauncherTransactionHelper() {
	};
	
	/**
     * Looks up the User Transaction in JNDI.
     *
     * @return the User Transaction
     * @throws NamingException if not found
     */
    public static UserTransaction lookupUserTransaction()
            throws NamingException {
        InitialContext context = new InitialContext();
        int i = 0;
        for (String name : UT_NAMES) {
            try {
                final Object lookup = context.lookup(name);
                UserTransaction userTransaction = (UserTransaction) lookup;
                if (userTransaction != null) {
                    if (i != 0) {
                        // put successful name first for next time
                        UT_NAMES[i] = UT_NAMES[0];
                        UT_NAMES[0] = name;
                    }
                    return userTransaction;
                }
            } catch (NamingException e) {
                // try next one
            }
            i++;
        }
        throw new NamingException("UserTransaction not found in JNDI");
    }

	/**
	 * Start transaction.
	 */
	public static void checkNStartTransaction() {
		if (!TransactionHelper.isTransactionActive()) {
			TransactionHelper.startTransaction();
		} else {
			TransactionHelper.commitOrRollbackTransaction();
			TransactionHelper.startTransaction();
		}
	}

    /**
     * Start transaction.
     */
    public static boolean checkNStartTransactionWithStatus() {
        boolean tx;
        if (!TransactionHelper.isTransactionActive()) {
            tx = TransactionHelper.startTransaction();
        } else {
            TransactionHelper.commitOrRollbackTransaction();
            tx = TransactionHelper.startTransaction();
        }
        return tx;
    }

	/**
	 * Commits or rolls back the User Transaction depending on the transaction
	 * status.
	 */
	public static void commitOrRollbackTransaction() {
		UserTransaction ut;
		try {
			ut = lookupUserTransaction();
		} catch (NamingException e) {
			log.warn("No user transaction", e);
			return;
		}
		try {
			int status = ut.getStatus();
			if (status == Status.STATUS_ACTIVE) {
				if (log.isDebugEnabled()) {
					//log.debug("Commiting transaction");
				}
				ut.commit();
			} else if (status == Status.STATUS_MARKED_ROLLBACK) {
				if (log.isDebugEnabled()) {
					log.debug("Cannot commit transaction because it is marked rollback only");
				}
				ut.rollback();
			} else {
				if (log.isDebugEnabled()) {
					log.debug("Cannot commit transaction with unknown status: "
							+ status);
				}
			}
		} catch (Exception e) {
			String msg = "Unable to commit/rollback  " + ut;
			if (e instanceof RollbackException
					&& "Unable to commit: transaction marked for rollback"
							.equals(e.getMessage())) {
				// don't log as error, this happens if there's a
				// ConcurrentModificationException at transaction end inside VCS
				log.debug(msg, e);
			} else {
				log.error(msg, e);
			}
			throw new TransactionRuntimeException(msg, e);
		}
	}
	
	/**
     * Sets the current User Transaction as rollback only.
     *
     * @return {@code true} if the transaction was successfully marked rollback
     *         only, {@code false} otherwise
     */
    public static boolean setTransactionRollbackOnly() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Setting transaction as rollback only");
            }
            lookupUserTransaction().setRollbackOnly();
            return true;
        } catch (NamingException e) {
            // no transaction
        } catch (Exception e) {
            log.error("Could not mark transaction as rollback only", e);
        }
        return false;
    }

    /**
     * Rollback transaction.
     * 
     * @return {@code true} if the transaction was successfully rollback, {@code false} otherwise
     */
    public static boolean rollbackTransaction() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Rollback transaction");
            }
            lookupUserTransaction().rollback();
            return true;
        } catch (NamingException e) {
            // no transaction
        } catch (Exception e) {
            log.error("Could not rollback transaction", e);
        }
        return false;
    }

}
