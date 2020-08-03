package com.perforce.polarion.element.field;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FieldMapping {

	private static FieldMapping instance = new FieldMapping();

	private Map<WorkItemField, P4JobField> toJob = new HashMap<>();
	private Map<P4JobField, WorkItemField> fromJob = new HashMap<>();

	private FieldMapping() {
		// Default values (depending on UX remove these entries)
		add(WorkItemField.ID, new P4JobField("Job"));
		add(WorkItemField.DESCRIPTION, new P4JobField("Description"));
		add(WorkItemField.STATUS, new P4JobField("PolarionStatus"));
		add(WorkItemField.AUTHOR, new P4JobField("ReportedBy"));
		add(WorkItemField.TITLE, new P4JobField("PolarionTitle"));
		add(WorkItemField.PROJECT, new P4JobField("PolarionProject"));
		add(WorkItemField.LINK, new P4JobField("PolarionLink"));
	}

	public static FieldMapping getInstance() {
		return instance;
	}

	public P4JobField get(WorkItemField field) {
		if (toJob.containsKey(field)) {
			return toJob.get(field);
		} else {
			return null;
		}
	}

	public WorkItemField get(P4JobField field) {
		if (fromJob.containsKey(field)) {
			return fromJob.get(field);
		} else {
			return null;
		}
	}

	public void add(WorkItemField field, P4JobField jobField) {
		toJob.put(field, jobField);
		fromJob.put(jobField, field);
	}

	public Set<WorkItemField> listWorkItemFields() {
		return toJob.keySet();
	}

}
