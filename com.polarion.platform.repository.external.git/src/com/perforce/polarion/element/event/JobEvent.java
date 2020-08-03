package com.perforce.polarion.element.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.IJob;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.option.server.FixJobsOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.polarion.element.field.FieldMapping;
import com.perforce.polarion.element.field.P4JobField;
import com.perforce.polarion.element.field.WorkItemAdapter;
import com.perforce.polarion.element.field.WorkItemField;
import com.perforce.polarion.repository.provider.ConnectionFactory;
import com.perforce.polarion.repository.provider.P4RepositoryConfiguration;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.persistence.model.IRevision;
import com.polarion.subterra.base.data.identification.IContextId;

public class JobEvent {

	private static final Logger log = Logger.getLogger(JobEvent.class);

	private final IWorkItem workItem;
	private final IContextId context;
	private final String id;

	private List<P4RepositoryConfiguration> configs = new ArrayList<>();

	/**
	 * Update a Perforce Job given a Polarion WorkItem
	 * 
	 * @param workItem
	 */
	public JobEvent(IWorkItem workItem) {
		this.workItem = workItem;
		this.context = workItem.getContextId();
		this.id = workItem.getId();

		// Given the context find all the Perforce connections
		configs = ConnectionFactory.getConnections(context);
	}

	public void deleteJob() {
		// each Perforce connection
		for (P4RepositoryConfiguration config : configs) {
			IOptionsServer p4 = ConnectionFactory.getConnection(config);
			try {
				p4.deleteJob(id);
			} catch (P4JavaException e) {
				log.warn("Unable to delete Job: " + id + e.getMessage());
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

	public void updateJob() {
		// each Perforce connection
		for (P4RepositoryConfiguration config : configs) {
			IOptionsServer p4 = ConnectionFactory.getConnection(config);
			try {
				// fetch each Job field
				Map<String, Object> fields = toJobSpec(p4);

				// Update fields
				IJob job = p4.getJob(id);
				Map<String, Object> current = job.getRawFields();
				current.putAll(fields);
				job.setRawFields(current);
				job.update();

				// Update fixes
				fixes();
			} catch (P4JavaException e) {
				log.warn("Unable to update Job: " + id, e);
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

	private Map<String, Object> toJobSpec(IOptionsServer p4) {
		// create WorkItem adapter and set URL
		WorkItemAdapter adapter = new WorkItemAdapter(workItem);
		adapter.setPolarionUrl(p4);

		// load all defined fields
		Map<WorkItemField, String> changes = new HashMap<>();
		for (WorkItemField item : WorkItemField.values()) {
			String value = adapter.get(item);
			changes.put(item, value);
		}

		// Map for each field
		Map<String, Object> fields = new HashMap<>();

		for (Map.Entry<WorkItemField, String> entry : changes.entrySet()) {
			WorkItemField field = entry.getKey();
			String value = entry.getValue();

			FieldMapping mappings = FieldMapping.getInstance();
			P4JobField jobField = mappings.get(field);

			if (jobField != null) {
				fields.put(jobField.getField(), value);
				log.info("Spec value: " + jobField.getField() + "=" + value);
			} else {
				log.info("No mapping for Polarion field: " + field.getName());
			}
		}
		return fields;
	}

	private void fixes() {
		@SuppressWarnings("unchecked")
		IPObjectList<IRevision> revisions = workItem.getLinkedRevisions();

		// each Perforce connection
		for (P4RepositoryConfiguration config : configs) {
			IOptionsServer p4 = ConnectionFactory.getConnection(config);
			try {
				List<Integer> remainder = new ArrayList<>();

				// List current fixes
				List<IFix> fixes = p4.getFixList(null, -1, id, false, 100);
				for (IFix fix : fixes) {
					int change = fix.getChangelistId();
					remainder.add(change);
				}

				// Add fix records
				String repoId = config.getId();
				for (int change : toFixRecord(repoId, revisions)) {
					log.info("fix: " + id + " " + change);
					FixJobsOptions opts = new FixJobsOptions();
					opts.setStatus("same");
					p4.fixJobs(Arrays.asList(id), change, opts);

					// remove from list
					if (remainder.contains(change)) {
						remainder.remove(remainder.indexOf(change));
					}
				}

				// Remove fix records
				for (int change : remainder) {
					log.info("unfix: " + id + " " + change);
					FixJobsOptions opts = new FixJobsOptions();
					opts.setDelete(true);
					opts.setStatus("same");
					p4.fixJobs(Arrays.asList(id), change, opts);
				}

			} catch (P4JavaException e) {
				log.warn("Unable to add Fix for Job: " + id, e);
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

	private List<Integer> toFixRecord(String repoId, IPObjectList<IRevision> revisions) {

		log.debug("... Fixes for Repo: " + repoId);

		// List of changes associated with the repo
		List<Integer> fixes = new ArrayList<>();

		Iterator<IRevision> itr = revisions.iterator();
		while (itr.hasNext()) {
			IRevision rev = itr.next();

			String repoStr = rev.getRepositoryName();
			log.debug("... " + repoId + "===" + repoStr);
			if (repoStr != null && repoStr.endsWith(repoId)) {
				String changeStr = rev.getName();
				log.debug("... add fix: " + repoId + ":" + changeStr);

				int change = Integer.parseInt(changeStr);
				fixes.add(change);
			}
		}
		return fixes;
	}

}
