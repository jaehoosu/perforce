package com.perforce.polarion.element.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.IJob;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.polarion.element.field.FieldMapping;
import com.perforce.polarion.element.field.WorkItemAdapter;
import com.perforce.polarion.element.field.WorkItemField;
import com.perforce.polarion.repository.provider.ConnectionFactory;
import com.perforce.polarion.repository.provider.P4RepositoryConfiguration;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.platform.ITransactionService;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.persistence.model.IRevision;
import com.polarion.platform.security.AuthenticationFailedException;
import com.polarion.platform.security.ISecurityService;

public class WorkItemEvent {

	private static final Logger log = Logger.getLogger(WorkItemEvent.class);

	private final ITransactionService transaction;
	private final ITrackerService trackerService;
	private final FieldMapping mappings = FieldMapping.getInstance();

	/**
	 * Update a Polarion WorkItem given a Perforce Job
	 * 
	 * @param job
	 */
	public WorkItemEvent() {
		transaction = PlatformContext.getPlatform().lookupService(ITransactionService.class);
		trackerService = PlatformContext.getPlatform().lookupService(ITrackerService.class);

		ISecurityService security = PlatformContext.getPlatform().lookupService(ISecurityService.class);
		try {
			security.loginUserFromVault("perforce", "system");
		} catch (AuthenticationFailedException e) {
			log.error("Vault login error", e);
		}
	}

	public boolean createWorkItem(IJob job, String type) {
		// Get Perforce Job Field names
		String projField = mappings.get(WorkItemField.PROJECT).getField();
		String idField = mappings.get(WorkItemField.ID).getField();

		// Get Field values from Perforce Job
		Map<String, Object> map = job.getRawFields();
		String project = (String) map.get(projField);
		String id = (String) map.get(idField);

		log.info("Importing WorkItem(" + type + "): " + project + ":" + id);

		transaction.beginTx();
		boolean failed = true;
		try {
			ITrackerProject trackerProject = trackerService.getTrackerProject(project);
			IWorkItem workItem = trackerProject.createWorkItem(type);
			WorkItemAdapter adapter = new WorkItemAdapter(workItem);

			for (WorkItemField field : WorkItemField.getUpdatable()) {
				String jobField = mappings.get(field).getField();
				String value = (String) map.get(jobField);
				adapter.set(field, value);
			}

			// Add Linked Revisions
			linkedRevisions(workItem, project, id);

			workItem.save();
			failed = false;
		} catch (Exception e) {
			log.error("Error WorkItem Create.", e);
		} finally {
			try {
				transaction.endTx(failed);
			} catch (Exception e) {
				log.error("Error ending WorkItem Create transaction.", e);
			}
		}
		return !failed;
	}

	public void updateWorkItem(IJob job) {
		// Get Perforce Job Field names
		String projField = mappings.get(WorkItemField.PROJECT).getField();
		String idField = mappings.get(WorkItemField.ID).getField();

		// Get Field values from Perforce Job
		Map<String, Object> map = job.getRawFields();
		String project = (String) map.get(projField);
		String id = (String) map.get(idField);

		log.info("Updating WorkItem: " + project + ":" + id);

		transaction.beginTx();
		boolean failed = true;
		try {
			IWorkItem workItem = trackerService.findWorkItem(project, id);
			WorkItemAdapter adapter = new WorkItemAdapter(workItem);

			for (WorkItemField field : WorkItemField.getUpdatable()) {
				String jobField = mappings.get(field).getField();
				String value = (String) map.get(jobField);
				adapter.set(field, value);
			}

			// Update Linked Revisions
			linkedRevisions(workItem, project, id);

			workItem.save();
			failed = false;
		} finally {
			try {
				transaction.endTx(failed);
			} catch (Exception e) {
				log.error("Error ending WorkItem Update transaction.", e);
			}
		}
	}

	private void linkedRevisions(IWorkItem workItem, String projId, String id) {

		List<P4RepositoryConfiguration> configs = ConnectionFactory.getConnections(workItem.getContextId());

		// each Perforce connection
		for (P4RepositoryConfiguration config : configs) {
			IOptionsServer p4 = ConnectionFactory.getConnection(config);
			try {
				List<String> changes = new ArrayList<>();

				// List current fixes
				List<IFix> fixes = p4.getFixList(null, -1, id, false, 100);
				for (IFix fix : fixes) {
					int change = fix.getChangelistId();
					changes.add(String.valueOf(change));
				}

				// Add linked Revision
				String repoId = projId + ":" + config.getId();
				for (String change : changes) {
					log.info("Add linked revision: " + repoId + ":" + change);
					workItem.addLinkedRevision(repoId, change);
				}

				// Remove linked Revision
				@SuppressWarnings("unchecked")
				IPObjectList<IRevision> revisions = workItem.getLinkedRevisions();
				for (IRevision rev : revisions) {
					String repo = rev.getRepositoryName();
					if (repoId.equals(repo)) {
						String change = rev.getName();
						if (!changes.contains(change)) {
							log.info("Remove linked revision: " + repoId + ":" + change);
							workItem.removeLinkedRevision(repoId, change);
						}
					}
				}

			} catch (P4JavaException e) {
				log.warn("Unable access Fix for Job: " + id, e);
				return;
			} finally {
				try {
					p4.disconnect();
				} catch (P4JavaException e) {
					log.error("Unable to disconnect!", e);
				}
			}
		}
	}
}
