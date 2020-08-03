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

public class JobScannerFactory implements IJobUnitFactory {

	@Override
	public IJobUnit createJobUnit(String name) throws GenericJobException {
		return new JobScanner(name, this);
	}

	@Override
	public IJobDescriptor getJobDescriptor(IJobUnit jobUnit) {
		BasicJobDescriptor desc = new BasicJobDescriptor("P4Job Scanner", jobUnit);
		return desc;
	}

	@Override
	public String getName() {
		return IJobScanner.JOB_NAME;
	}

	private final class JobScanner extends AbstractJobUnit implements IJobScanner {

		private final Logger log = Logger.getLogger(JobScanner.class);

		private static final String SCAN_COUNTER = "Polarion.Job.Time";

		public JobScanner(String name, IJobUnitFactory creator) {
			super(name, creator);
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
						event.updateWorkItem(job);
					}
				}

				return getStatusOK(null);
			} finally {
				progress.done();
			}
		}

		private List<IJob> queryJobs(P4RepositoryConfiguration config) {
			log.info("CRON: Scanning " + config.getP4Port());

			IOptionsServer p4 = ConnectionFactory.getConnection(config);
			try {
				long scan = getLastScan(p4);
				long serverTime = getServerTime(p4);

				// Get jobs modified since last scan and not by Polarion
				String user = config.getP4User();
				String jobView = " " + "ModifiedDate>" + scan + " ^ModifiedBy=" + user;
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
			p4.setCounter(SCAN_COUNTER, String.valueOf(scan), opts);
		}

		private long getLastScan(IOptionsServer p4) throws P4JavaException {
			String value = p4.getCounter(SCAN_COUNTER);
			long scan = 0;
			try {
				scan = Long.parseLong(value);
			} catch (NumberFormatException e) {
				log.warn("Unable to read counter " + SCAN_COUNTER + ": " + value);
			}
			log.debug("CRON: Last scan: " + scan);
			return scan;
		}
	}
}
