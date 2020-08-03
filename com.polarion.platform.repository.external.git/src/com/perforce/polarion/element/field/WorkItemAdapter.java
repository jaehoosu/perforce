package com.perforce.polarion.element.field;

import java.util.Iterator;

import org.apache.log4j.Logger;

import com.perforce.p4java.Log;
import com.perforce.p4java.admin.IProperty;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.option.server.GetPropertyOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.persistence.model.IRevision;

public class WorkItemAdapter {

	private static final Logger log = Logger.getLogger(WorkItemAdapter.class);

	private final IWorkItem workItem;

	private String polarionUrl = "unset";

	public WorkItemAdapter(IWorkItem workItem) {
		this.workItem = workItem;
	}

	public void setPolarionUrl(IOptionsServer p4) {
		try {
			GetPropertyOptions propOpts = new GetPropertyOptions();
			propOpts.setName("Polarion.Server.Url");
			for (IProperty prop : p4.getProperty(propOpts)) {
				String value = prop.getValue();
				if (value != null && !value.isEmpty()) {
					polarionUrl = value;
				}
			}
		} catch (P4JavaException e) {
			log.warn("P4 counter 'Polarion.Server.Url' not found.");
		}
	}

	public String get(WorkItemField field) {
		switch (field) {

		case ID:
			return workItem.getId();

		case PROJECT:
			return workItem.getProjectId();

		case DESCRIPTION:
			String description = "";
			Text text = workItem.getDescription();
			if (text != null) {
				description = text.getContent();
			}
			return description;

		case TITLE:
			return workItem.getTitle();

		case STATUS:
			IStatusOpt status = workItem.getStatus();
			return status.getId();

		case AUTHOR:
			IUser user = workItem.getAuthor();
			return user.getId();

		case ASSIGNEE:
			@SuppressWarnings("unchecked")
			IPObjectList<IUser> users = workItem.getAssignees();
			return assigneesToString(users);

		case LINKED_REVISIONS:
			@SuppressWarnings("unchecked")
			IPObjectList<IRevision> revisions = workItem.getLinkedRevisions();
			return linkedRevisionsToString(revisions);

		case LINK:
			// http://<servername>/polarion/#/project/<projectname>/workitem?id=<workitem-id>
			String project = workItem.getProjectId();
			String id = workItem.getId();
			String url = polarionUrl + "/polarion/#/project/" + project + "/workitem?id=" + id;
			log.info("URL: " + url);
			return url;

		default:
			Log.info("Unsupported Polarion field: " + this.toString());
			return null;
		}
	}

	public void set(WorkItemField field, String value) {
		switch (field) {

		case ID:
			// set at creation, immutable

		case PROJECT:
			// set at creation, immutable

		case DESCRIPTION:
			Text text = new Text(Text.TYPE_PLAIN, value);
			workItem.setDescription(text);

		case TITLE:
			workItem.setTitle(value);

		case STATUS:
			String status = WorkItemField.STATUS.getName();
			workItem.setEnumerationValue(status, value);

		case AUTHOR:
			// set at creation, immutable

		default:
			Log.warn("Unsupported Polarion field: " + this.toString());
		}
	}

	private String assigneesToString(IPObjectList<IUser> users) {
		StringBuffer sb = new StringBuffer();
		Iterator<IUser> itr = users.iterator();
		while (itr.hasNext()) {
			IUser user = itr.next();
			sb.append(user.getId());
			if (itr.hasNext()) {
				sb.append(" ");
			}
		}
		return sb.toString();
	}

	private String linkedRevisionsToString(IPObjectList<IRevision> revisions) {
		StringBuffer sb = new StringBuffer();
		Iterator<IRevision> itr = revisions.iterator();
		while (itr.hasNext()) {
			IRevision rev = itr.next();
			sb.append(rev.getRevision());
			if (itr.hasNext()) {
				sb.append(" ");
			}
		}
		return sb.toString();
	}
}
