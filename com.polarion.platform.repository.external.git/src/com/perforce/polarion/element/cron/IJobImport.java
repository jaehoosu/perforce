package com.perforce.polarion.element.cron;

import com.polarion.platform.jobs.IJobUnit;

public interface IJobImport extends IJobUnit {
	
	static final String JOB_NAME = "jobimport.job";
	
	public void setView(String view);
	public void setType(String type);
	public void setDelete(boolean delete);

}
