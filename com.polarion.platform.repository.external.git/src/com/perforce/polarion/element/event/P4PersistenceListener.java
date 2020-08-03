package com.perforce.polarion.element.event;

import java.util.List;

import org.apache.log4j.Logger;

import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.persistence.events.IPersistenceEvent;
import com.polarion.platform.persistence.events.IPersistenceListener;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.notifications.ChangeInfo;

public class P4PersistenceListener implements IPersistenceListener {

	private static final Logger log = Logger.getLogger(P4PersistenceListener.class);

	public P4PersistenceListener(IDataService dataService) {
		super();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void handleEvent(IPersistenceEvent event) {

		String revision = event.getRevision();
		String previousRevision = event.getPreviousRevision();

		// CREATED
		for (IPObject pobject : (List<IPObject>) event.getCreated()) {
			ChangeInfo change = ChangeInfo.create(pobject, revision, previousRevision, false, true);
			handleCreation(change);
			change.forget();
		}

		// MODIFIED
		for (IPObject pobject : (List<IPObject>) event.getModified()) {
			ChangeInfo change = ChangeInfo.create(pobject, revision, previousRevision, true, true);
			handleCreation(change);
			change.forget();
		}

		// DELETED
		for (IPObject pobject : (List<IPObject>) event.getDeleted()) {
			ChangeInfo change = ChangeInfo.create(pobject, revision, previousRevision, true, false);
			handleRemoval(change);
			change.forget();
		}
	}

	private void handleCreation(ChangeInfo change) {
		log.info("Creation of " + change.current.getUri());

		// exit early if not a WorkItem
		if (!(change.current instanceof IWorkItem)) {
			return;
		}

		IWorkItem workItem = (IWorkItem) change.current;
		JobEvent jobEvent = new JobEvent(workItem);
		jobEvent.updateJob();
	}

	private void handleRemoval(ChangeInfo change) {
		log.info("Removal of " + change.current.getUri());

		// exit early if not a WorkItem
		if (!(change.current instanceof IWorkItem)) {
			return;
		}

		IWorkItem workItem = (IWorkItem) change.current;
		JobEvent jobEvent = new JobEvent(workItem);
		jobEvent.deleteJob();
	}

}
