package org.jenkinsci.plugins.p4.tasks;

import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.impl.generic.core.Label;
import hudson.AbortException;
import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.changes.P4ChangeEntry;
import org.jenkinsci.plugins.p4.changes.P4Revision;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.review.ReviewProp;
import org.jenkinsci.plugins.p4.workspace.Expand;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CheckoutTask extends AbstractTask implements FileCallable<Boolean>, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(CheckoutTask.class.getName());

	private final Populate populate;

	private CheckoutStatus status;
	private int head;
	private P4Revision buildChange;
	private int review;

	/**
	 * Constructor
	 *
	 * @param populate Populate options
	 */
	public CheckoutTask(Populate populate) {
		this.populate = populate;
	}

	public void initialise() throws AbortException {
		ClientHelper p4 = getConnection();
		try {
			// fetch and calculate change to sync to or review to unshelve.
			status = getStatus(getWorkspace());
			head = p4.getClientHead();
			review = getReview(getWorkspace());
			buildChange = getBuildChange(getWorkspace());

			// try to get change-number if automatic label
			if (buildChange.isLabel()) {
				String label = buildChange.toString();
				if (p4.isLabel(label)) {
					Label labelSpec = p4.getLabel(label);
					String revSpec = labelSpec.getRevisionSpec();
					if (revSpec != null && !revSpec.isEmpty() && revSpec.startsWith("@")) {
						try {
							int change = Integer.parseInt(revSpec.substring(1));
							// if change is bigger than head, use head
							if (change > head) {
								buildChange = new P4Revision(head);
							} else {
								buildChange = new P4Revision(change);
							}
						} catch (NumberFormatException e) {
							// leave buildChange as is
						}
					}
				}
				if (p4.isCounter(label)) {
					try {
						String counter = p4.getCounter(label);
						// if a change number, add change...
						int change = Integer.parseInt(counter);
						// if change is bigger than head, use head
						if (change > head) {
							buildChange = new P4Revision(head);
						} else {
							buildChange = new P4Revision(change);
						}
					} catch (NumberFormatException n) {
						// no change number in counter
					}
				}
			} else {
				// if change is bigger than head, use head
				if (buildChange.getChange() > head) {
					buildChange = new P4Revision(head);
				}
			}
		} catch (Exception e) {
			String err = "P4: Unable to initialise CheckoutTask: " + e;
			logger.severe(err);
			p4.log(err);
			throw new AbortException(err);
		} finally {
			p4.disconnect();
		}
	}

	/**
	 * Invoke sync on build node (master or remote node).
	 *
	 * @return true if updated, false if no change.
	 */
	public Boolean invoke(File workspace, VirtualChannel channel) throws IOException {
		return (Boolean) tryTask();
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		// Tidy the workspace before sync/build
		p4.tidyWorkspace(populate);

		// Sync workspace to label, head or specified change
		p4.syncFiles(buildChange, populate);

		// Unshelve review if specified
		if (status == CheckoutStatus.SHELVED) {
			p4.unshelveFiles(review);
		}
		return true;
	}

	/**
	 * Get the build status for the parameter map.
	 *
	 * @param workspace
	 */
	private CheckoutStatus getStatus(Workspace workspace) {
		CheckoutStatus status = CheckoutStatus.HEAD;
		String value = workspace.getExpand().get(ReviewProp.STATUS.toString());
		if (value != null && !value.isEmpty()) {
			status = CheckoutStatus.parse(value);
		}
		return status;
	}

	/**
	 * Get the sync point from the parameter map. Returns the head if no change
	 * found in the map.
	 *
	 * @param workspace
	 */
	private P4Revision getBuildChange(Workspace workspace) {
		// Use head as the default
		P4Revision build = new P4Revision(this.head);

		// Get Environment parameters from expand
		Expand expand = workspace.getExpand();

		// if a pinned change/label is specified the update
		String populateLabel = populate.getPin();
		if (populateLabel != null && !populateLabel.isEmpty()) {
			// Expand label with environment vars if one was defined
			String expandedPopulateLabel = expand.format(populateLabel, false);
			if (!expandedPopulateLabel.isEmpty()) {
				try {
					// if build is a change-number passed as a label
					int change = Integer.parseInt(expandedPopulateLabel);
					build = new P4Revision(change);
					logger.info("getBuildChange:pinned:change:" + change);
				} catch (NumberFormatException e) {
					build = new P4Revision(expandedPopulateLabel);
					logger.info("getBuildChange:pinned:label:" + expandedPopulateLabel);
				}
			}
		}

		// if change is specified then update
		String cngStr = expand.get(ReviewProp.CHANGE.toString());
		if (cngStr != null && !cngStr.isEmpty()) {
			try {
				int change = Integer.parseInt(cngStr);
				build = new P4Revision(change);
				logger.info("getBuildChange:ReviewProp:CHANGE:" + change);
			} catch (NumberFormatException e) {
			}
		}

		// if label is specified then update
		String lblStr = expand.get(ReviewProp.LABEL.toString());
		if (lblStr != null && !lblStr.isEmpty()) {
			try {
				// if build is a change-number passed as a label
				int change = Integer.parseInt(lblStr);
				build = new P4Revision(change);
				logger.info("getBuildChange:ReviewProp:LABEL:" + change);
			} catch (NumberFormatException e) {
				build = new P4Revision(lblStr);
				logger.info("getBuildChange:ReviewProp:LABEL:" + lblStr);
			}
		}

		logger.info("getBuildChange:return:" + build);
		return build;
	}

	/**
	 * Get the unshelve point from the parameter map.
	 *
	 * @param workspace
	 */
	private int getReview(Workspace workspace) {
		int review = 0;
		Expand expand = workspace.getExpand();
		String value = expand.get(ReviewProp.REVIEW.toString());
		if (value != null && !value.isEmpty()) {
			try {
				review = Integer.parseInt(value);
			} catch (NumberFormatException e) {
			}
		}
		return review;
	}

	public List<P4Revision> getChanges(P4Revision last) {

		List<P4Revision> changes = new ArrayList<P4Revision>();

		// Add changes to this build.
		ClientHelper p4 = getConnection();
		try {
			changes = p4.listChanges(last, buildChange);
		} catch (Exception e) {
			String err = "Unable to get changes: " + e;
			logger.severe(err);
			p4.log(err);
			e.printStackTrace();
		} finally {
			p4.disconnect();
		}

		// Include shelf if a review
		if (status == CheckoutStatus.SHELVED) {
			changes.add(new P4Revision(review));
		}

		return changes;
	}

	public List<P4ChangeEntry> getChangesFull(P4Revision last) {

		List<P4ChangeEntry> changesFull = new ArrayList<P4ChangeEntry>();

		// Add changes to this build.
		ClientHelper p4 = getConnection();
		try {
			if (status == CheckoutStatus.SHELVED) {
				P4ChangeEntry cl = new P4ChangeEntry();
				IChangelistSummary pending = p4.getChange(review);
				cl.setChange(p4, pending);
				changesFull.add(cl);
			}

			// add all changes to list
			List<P4Revision> changes = p4.listChanges(last, buildChange);
			for (P4Revision change : changes) {
				P4ChangeEntry cl = new P4ChangeEntry();
				IChangelistSummary summary = p4.getChangeSummary(change.getChange());
				cl.setChange(p4, summary);
				changesFull.add(cl);
			}
		} catch (Exception e) {
			String err = "Unable to get full changes: " + e;
			logger.severe(err);
			p4.log(err);
			e.printStackTrace();
		} finally {
			p4.disconnect();
		}

		return changesFull;
	}

	public P4ChangeEntry getCurrentChange() {
		P4ChangeEntry cl = new P4ChangeEntry();
		P4Revision current = getBuildChange();

		ClientHelper p4 = getConnection();
		try {
			cl = current.getChangeEntry(p4);
		} catch (Exception e) {
			String err = "Unable to get current change: " + e;
			logger.severe(err);
			p4.log(err);
			e.printStackTrace();
		} finally {
			p4.disconnect();
		}

		return cl;
	}

	public CheckoutStatus getStatus() {
		return status;
	}

	// Returns the number of the build change not the review change
	public P4Revision getSyncChange() {
		return buildChange;
	}

	public P4Revision getBuildChange() {
		if (status == CheckoutStatus.SHELVED) {
			return new P4Revision(review);
		}
		return buildChange;
	}

	public void setBuildChange(P4Revision parentChange) {
		buildChange = parentChange;
	}

	public void setIncrementalChanges(List<P4Revision> changes) {
		if (changes != null && !changes.isEmpty()) {
			P4Revision lowest = changes.get(changes.size() - 1);
			buildChange = lowest;
		}
	}

	public int getReview() {
		return review;
	}

	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}
}
