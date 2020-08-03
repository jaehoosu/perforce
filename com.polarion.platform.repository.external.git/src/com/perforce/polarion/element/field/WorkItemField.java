package com.perforce.polarion.element.field;

import java.util.ArrayList;
import java.util.List;

import com.perforce.p4java.Log;

public enum WorkItemField {

	ID("id", Updateable.ONCE),
	PROJECT("project", Updateable.ONCE),
	DESCRIPTION("description", Updateable.UPDATE),
	TITLE("title", Updateable.UPDATE),
	STATUS("status", Updateable.UPDATE),
	AUTHOR("author", Updateable.ONCE),
	ASSIGNEE("assignee", Updateable.ONCE),
	LINKED_REVISIONS("linkedRevisions", Updateable.ONCE),
	LINK("link", Updateable.UPDATE);
	

	private final String name;
	private final Updateable update;

	WorkItemField(String name, Updateable update) {
		this.name = name;
		this.update = update;
	}

	public static WorkItemField parse(String type) {
		if (type != null) {
			for (WorkItemField t : WorkItemField.values()) {
				if (type.equalsIgnoreCase(t.getName())) {
					return t;
				}
			}
		}

		Log.info("No definition for Polarion field: " + type);
		return null;
	}

	public String getName() {
		return name;
	}

	public static List<WorkItemField> getUpdatable() {
		List<WorkItemField> list = new ArrayList<>();
		
		for( WorkItemField item : WorkItemField.values()) {
			if(item.update == Updateable.UPDATE) {
				list.add(item);
			}
		}
		return list;
	}
	

}
