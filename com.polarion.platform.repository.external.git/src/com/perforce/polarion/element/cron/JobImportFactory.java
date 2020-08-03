package com.perforce.polarion.element.cron;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.perforce.p4java.core.IJob;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.option.server.CounterOptions;
import com.perforce.p4java.option.server.GetJobsOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.IServerInfo;
import com.perforce.polarion.element.event.WorkItemEvent;
import com.perforce.polarion.repository.provider.ConnectionFactory;
import com.perforce.polarion.repository.provider.P4RepositoryConfiguration;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.platform.context.IContext;
import com.polarion.platform.core.IPlatform;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.jobs.GenericJobException;
import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnit;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.jobs.spi.AbstractJobUnit;
import com.polarion.platform.jobs.spi.BasicJobDescriptor;
import com.polarion.platform.jobs.spi.JobParameterPrimitiveType;
import com.polarion.platform.jobs.spi.SimpleJobParameter;

public class JobImportFactory implements IJobUnitFactory {

	@Override
	public IJobUnit createJobUnit(String name) throws GenericJobException {
		return new JobImport(name, this);
	}

	@Override
	public IJobDescriptor getJobDescriptor(IJobUnit jobUnit) {
		BasicJobDescriptor desc = new BasicJobDescriptor("P4Job Import", jobUnit);
		JobParameterPrimitiveType stringType = new JobParameterPrimitiveType("String", String.class);
		JobParameterPrimitiveType boolType = new JobParameterPrimitiveType("Boolean", Boolean.class);

		desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "view",
				"Perforce JobView to filter Jobs for import.", stringType));

		desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "type",
				"Polarion WorkItem type for imported Jobs.", stringType));

		desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "delete",
				"Delete Perforce Job after import.", boolType));

		return desc;
	}

	@Override
	public String getName() {
		return IJobImport.JOB_NAME;
	}

	private final class JobImport extends AbstractJobUnit implements IJobImport {

		private final Logger log = Logger.getLogger(JobImport.class);

		private static final String IMPORT_COUNTER = "Polarion.Import.Time";

		private String view;
		private String type;
		private boolean delete;

		public JobImport(String name, IJobUnitFactory creator) {
			super(name, creator);
		}

		public void setView(String view) {
			this.view = view;
		}

		public void setType(String type) {
			this.type = type;
		}

		public void setDelete(boolean delete) {
			this.delete = delete;
		}

		/**
		 * The main job method
		 * 
		 * @param progress
		 */
		@Override
		protected IJobStatus runInternal(IProgressMonitor progress) {
			IPlatform platform = PlatformContext.getPlatform();
			IProjectService projectService = (IProjectService) platform.lookupService(IProjectService.class);
			IContext scope = getScope();

			progress.beginTask(getName(), 0);

			WorkItemEvent event = new WorkItemEvent();
			try {
				IProject project = projectService.getProjectForContextId(scope.getId());
				if (project == null) {
					return getStatusFailed("Scope '" + scope.getId() + "' is not project.", null);
				}

				// Given the context find all the Perforce connections
				List<P4RepositoryConfiguration> configs = ConnectionFactory.getConnections(scope.getId());
				for (P4RepositoryConfiguration config : configs) {
					List<IJob> jobs = queryJobs(config);
					for (IJob job : jobs) {
						// create new WorkItem (and eventually a new Perforce Job)
						boolean ok = event.createWorkItem(job, type);
						
						// delete imported Job if required
						if(ok && delete) {
							deleteJob(config, job);
						}
					}
				}

				return getStatusOK(null);
			} finally {
				progress.done();
			}
		}

		private List<IJob> queryJobs(P4RepositoryConfiguration config) {
			log.info("CRON: Importing " + config.getP4Port());

			IOptionsServer p4 = ConnectionFactory.getConnection(config);
			try {
				long scan = getLastScan(p4);
				long serverTime = getServerTime(p4);
				
				// Get jobs modified since last scan and not by Polarion
				String user = config.getP4User();
				String jobView = " " + "ReportedDate>" + scan + " ^ModifiedBy=" + user + " " + view;
				GetJobsOptions opts = new GetJobsOptions();
				opts.setJobView(jobView);
				List<IJob> jobs = p4.getJobs(null, opts);

				// Update the last scan time
				setLastScan(serverTime, p4);
				return jobs;
			} catch (P4JavaException e) {
				log.warn("Perforce Error.", e);
			} catch (ParseException e) {
				log.warn("Unable to parse date.", e);
			} finally {
				try {
					p4.disconnect();
				} catch (P4JavaException e) {
					log.error("Unable to disconnect!", e);
				}
			}

			// return empty list
			return new ArrayList<>();
		}

		private void deleteJob(P4RepositoryConfiguration config, IJob job) {
			log.info("CRON: Deleting " + job.getId());

			IOptionsServer p4 = ConnectionFactory.getConnection(config);
			try {
				p4.deleteJob(job.getId());
			} catch (P4JavaException e) {
				log.warn("Unable to delete Job.", e);
			} finally {
				try {
					p4.disconnect();
				} catch (P4JavaException e) {
					log.error("Unable to disconnect!", e);
				}
			}
		}

		private long getServerTime(IOptionsServer p4) throws P4JavaException, ParseException {
			IServerInfo info = p4.getServerInfo();
			String dateStr = info.getServerDate();
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z");
			Date date = formatter.parse(dateStr);
			long time = date.getTime() / 1000;
			log.info("CRON: Server time: " + time);
			return time;
		}

		private void setLastScan(long scan, IOptionsServer p4) throws P4JavaException {
			CounterOptions opts = new CounterOptions();
			p4.setCounter(IMPORT_COUNTER, String.valueOf(scan), opts);
		}

		private long getLastScan(IOptionsServer p4) throws P4JavaException {
			String value = p4.getCounter(IMPORT_COUNTER);
			long scan = 0;
			try {
				scan = Long.parseLong(value);
			} catch (NumberFormatException e) {
				log.warn("Unable to read counter " + IMPORT_COUNTER + ": " + value);
			}
			log.debug("CRON: Last scan: " + scan);
			return scan;
		}
	}
}
