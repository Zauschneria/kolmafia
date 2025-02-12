/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package net.sourceforge.kolmafia.scripts.svn;

import net.sourceforge.kolmafia.RequestLogger;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/*
 * This class is an implementation of ISVNEventHandler intended for  processing
 * events generated by do*() methods of an SVNUpdateClient object. An  instance
 * of this handler will be provided to  an  SVNUpdateClient. When calling,  for
 * example, SVNWCClient.doUpdate(..) on some path, that method will generate an
 * event for each 'update'/'add'/'delete'/.. action it will perform upon  every
 * path being updated. And this event is passed to
 *
 * ISVNEventHandler.handleEvent(SVNEvent event,  double progress)
 *
 * to notify the handler.  The  event  contains detailed  information about the
 * path, action performed upon the path and some other.
 */
public class UpdateEventHandler implements ISVNEventHandler {
  /*
   * progress is currently reserved for future purposes and now is always
   * ISVNEventHandler.UNKNOWN
   */
  @Override
  public void handleEvent(SVNEvent event, double progress) {
    /*
     * Gets the current action. An action is represented by SVNEventAction.
     * In case of an update an action can be determined via comparing
     * SVNEvent.getAction() and SVNEventAction.UPDATE_-like constants.
     */
    SVNEventAction action = event.getAction();
    String pathChangeType = " ";
    if (action == SVNEventAction.UPDATE_ADD) {
      /*
       * the item was added
       */
      SVNManager.queueFileEvent(new SVNFileEvent(event.getFile(), event));
      pathChangeType = "A";
    } else if (action == SVNEventAction.UPDATE_DELETE) {
      /*
       * the item was deleted
       */
      SVNManager.queueFileEvent(new SVNFileEvent(event.getFile(), event));
      pathChangeType = "D";
    } else if (action == SVNEventAction.UPDATE_UPDATE) {
      /*
       * Find out in details what state the item is (after having been
       * updated).
       *
       * Gets the status of file/directory item contents. It is
       * SVNStatusType who contains information on the state of an item.
       */
      SVNStatusType contentsStatus = event.getContentsStatus();
      if (contentsStatus == SVNStatusType.CHANGED) {
        /*
         * the item was modified in the repository (got the changes from
         * the repository
         */
        SVNManager.queueFileEvent(new SVNFileEvent(event.getFile(), event));
        pathChangeType = "U";
      } else if (contentsStatus == SVNStatusType.CONFLICTED) {
        /*
         * The file item is in a state of Conflict. That is, changes
         * received from the repository during an update, overlap with
         * local changes the user has in his working copy.
         */
        RequestLogger.printLine(
            "<font color=\"red\">There are unresolved conflicts for "
                + event.getFile().getName()
                + "</font>");
        RequestLogger.printLine("Resolve them manually and perform another SVN update.");
        SVNManager.queueFileEvent(new SVNFileEvent(event.getFile(), event));
        pathChangeType = "C";
      } else if (contentsStatus == SVNStatusType.MERGED) {
        /*
         * The file item was merGed (those changes that came from the
         * repository did not overlap local changes and were merged into
         * the file).
         */
        SVNManager.queueFileEvent(new SVNFileEvent(event.getFile(), event));
        pathChangeType = "G";
      }
    } else if (action == SVNEventAction.UPDATE_EXTERNAL) {
      /* for externals definitions */
      RequestLogger.printLine(
          "Fetching external item into '" + event.getFile().getAbsolutePath() + "'");
      RequestLogger.printLine("External at revision " + event.getRevision());
      return;
    } else if (action == SVNEventAction.UPDATE_COMPLETED) {
      /*
       * Updating the working copy is completed. Prints out the revision.
       */
      RequestLogger.printLine("At revision " + event.getRevision());
      return;
    } else if (action == SVNEventAction.ADD) {
      SVNManager.queueFileEvent(new SVNFileEvent(event.getFile(), event));
      RequestLogger.printLine("A     " + event.getURL());
      return;
    } else if (action == SVNEventAction.DELETE) {
      SVNManager.queueFileEvent(new SVNFileEvent(event.getFile(), event));
      RequestLogger.printLine("D     " + event.getURL());
      return;
    } else if (action == SVNEventAction.LOCKED) {
      RequestLogger.printLine("L     " + event.getURL());
      return;
    } else if (action == SVNEventAction.LOCK_FAILED) {
      RequestLogger.printLine("failed to lock    " + event.getURL());
      return;
    }

    /*
     * Now getting the status of properties of an item. SVNStatusType also
     * contains information on the properties state.
     */
    SVNStatusType propertiesStatus = event.getPropertiesStatus();
    /*
     * At first consider properties are normal (unchanged).
     */
    String propertiesChangeType = " ";
    if (propertiesStatus == SVNStatusType.CHANGED) {
      /*
       * Properties were updated.
       */
      propertiesChangeType = "U";
    } else if (propertiesStatus == SVNStatusType.CONFLICTED) {
      /*
       * Properties are in conflict with the repository.
       */
      propertiesChangeType = "C";
    } else if (propertiesStatus == SVNStatusType.MERGED) {
      /*
       * Properties that came from the repository were merged with the
       * local ones.
       */
      propertiesChangeType = "G";
    }

    /*
     * Gets the status of the lock.
     */
    String lockLabel = " ";
    SVNStatusType lockType = event.getLockStatus();

    if (lockType == SVNStatusType.LOCK_UNLOCKED) {
      /*
       * The lock is broken by someone.
       */
      lockLabel = "B";
    }

    String printMe = event.getURL() != null ? event.getURL().toString() : event.getFile().getPath();
    RequestLogger.printLine(
        pathChangeType + propertiesChangeType + lockLabel + "       " + printMe);
  }

  /*
   * Should be implemented to check if the current operation is cancelled. If
   * it is, this method should throw an SVNCancelException.
   */
  @Override
  public void checkCancelled() throws SVNCancelException {}
}
