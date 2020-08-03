package com.perforce.polarion.repository.provider;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.polarion.core.util.ObjectUtils;
import com.polarion.platform.internal.service.repository.LocationChangeMetaData;
import com.polarion.platform.repository.external.IExternalRepositoryConfiguration;
import com.polarion.platform.repository.external.IExternalRepositoryProvider.IExternalRepository;
import com.polarion.platform.repository.external.IExternalRepositoryProvider.IExternalRepositoryCallback;
import com.polarion.platform.service.repository.ILocationChangeMetaData;
import com.polarion.platform.service.repository.IRevisionMetaData;
import com.polarion.platform.service.repository.RepositoryException;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.location.ILocation;
import com.polarion.subterra.base.location.Location;

public class P4ExternalRepository implements IExternalRepository {

	private final IContextId contextId;
	private P4RepositoryConfiguration config;
	private final IExternalRepositoryCallback callback;

	/**
	 * @param contextId
	 * @param configuration
	 * @param callback
	 */
	public P4ExternalRepository(IContextId contextId, P4RepositoryConfiguration configuration,
			IExternalRepositoryCallback callback) {
		this.contextId = contextId;
		config = configuration;
		this.callback = callback;
	}

	public P4RepositoryConfiguration getConfig() {
		return config;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.polarion.platform.repository.external.IExternalRepositoryProvider
	 * .IExternalRepository#getContextId()
	 */
	@Override
	public IContextId getContextId() {
		return contextId;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.polarion.platform.repository.external.IExternalRepositoryProvider
	 * .IExternalRepository#getRevisionViewURL(java.lang.String)
	 */
	@Override
	public String getRevisionViewURL(String revision) {
		String base = config.getBrowserBaseURL();
		if (base != null) {
			base = cleanURL(base);
			String url = base + "/changes/" + revision;
			return url;
		}
		return null;
	}

	@Override
	public String getViewLocationDiffURL(ILocationChangeMetaData locationChangeMetaData) {
		String base = config.getBrowserBaseURL();
		if (base != null) {
			base = cleanURL(base);
			ILocation toRev = locationChangeMetaData.getChangeLocationTo();
			String path = toRev.getLocationPath();
			String change = toRev.getRevision();
			String hex = Integer.toHexString(path.hashCode());
			String url = base + "/changes/" + change + "#" + hex;
			return url;
		}
		return null;
	}

	@Override
	public String getViewLocationURL(ILocationChangeMetaData locationChangeMetaData) {
		String base = config.getBrowserBaseURL();
		if (base != null) {
			base = cleanURL(base);
			ILocation toRev = locationChangeMetaData.getChangeLocationTo();
			String path = toRev.getLocationPath();
			String url = base + "/files/" + path;
			return url;
		}
		return null;
	}

	private String cleanURL(String url) {
		if (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.polarion.platform.repository.external.IExternalRepositoryProvider
	 * .IExternalRepository#poll(com.polarion.platform.repository.external.
	 * IExternalRepositoryProvider.IExternalRepository.PollMode)
	 */
	@Override
	public void poll(PollMode mode) {
		if (mode == null) {
			throw new IllegalArgumentException("mode is null");
		}

		String lastRevision = callback.getRememberedState(this);
		int lastChange = 1;

		try {
			// build fileSpec in client syntax for query
			String clientPath = "//" + config.getP4Client() + "/...";
			if (lastRevision != null && !mode.equals(PollMode.FROM_FIRST)) {
				String range = "@" + lastRevision + ",#head";
				clientPath += range;
				lastChange = Integer.parseInt(lastRevision);
			}
			List<IFileSpec> fileSpec;
			fileSpec = FileSpecBuilder.makeFileSpecList(clientPath);

			// connect to Perforce (without logging) and fetch change based on revision ID
			IOptionsServer p4 = ConnectionFactory.getConnection(config, false);
			GetChangelistsOptions opts = new GetChangelistsOptions();
			List<IChangelistSummary> changes;
			changes = p4.getChangelists(fileSpec, opts);
			p4.disconnect();

			// push new each change to callback
			if (changes.size() > 1) {
				for (IChangelistSummary c : changes) {
					if (c.getId() > lastChange) {
						lastChange = c.getId();
					}
					callback.revisionAdded(this, "" + c.getId());
				}
				lastRevision = "" + lastChange;
			}
		} catch (Exception e) {
			throw new RepositoryException(e);
		} finally {
			callback.setRememberedState(this, lastRevision);
		}
	}

	@Override
	public boolean allowsAutoPolling() {
		return false;
	}

	@Override
	public void startAutoPolling() {
		// empty
	}

	@Override
	public void stopAutoPolling() {
		// empty
	}

	/**
	 * Returns the Perforce Changelist meta-data
	 */
	@Override
	public IRevisionMetaData getRevisionMetaData(String revision) {

		try {
			// connect to Perforce and fetch change based on revision ID
			int changeId = Integer.parseInt(revision);
			IOptionsServer p4 = ConnectionFactory.getConnection(config);
			final IChangelist change = p4.getChangelist(changeId);
			p4.disconnect();

			return new IRevisionMetaData() {

				@Override
				public String getAuthor() {
					return change.getUsername();
				}

				@Override
				public Date getDate() {
					return change.getDate();
				}

				@Override
				public String getDescription() {
					return change.getDescription();
				}

				@Override
				public String getName() {
					return change.getClientId();
				}
			};
		} catch (Exception e) {
			throw new RepositoryException(e);
		}
	}

	/**
	 * Returns Perforce configuration
	 */
	@Override
	public IExternalRepositoryConfiguration getConfiguration() {
		return config;
	}

	/**
	 * Reconfigures Perforce configuration
	 */
	@Override
	public void reconfigure(IExternalRepositoryConfiguration newConfiguration) {
		config = (P4RepositoryConfiguration) newConfiguration;
	}

	/**
	 * Builds a list of file revisions in a changelist
	 */
	@Override
	public List<ILocationChangeMetaData> getChangedLocations(String change) {

		List<ILocationChangeMetaData> list = null;

		IOptionsServer p4 = ConnectionFactory.getConnection(config);

		try {
			// connect to Perforce and fetch change based on revision ID
			int changeId = Integer.parseInt(change);
			IChangelist changelist = p4.getChangelist(changeId);

			// build list of revisions
			list = new ArrayList<ILocationChangeMetaData>();
			for (IFileSpec r : changelist.getFiles(true)) {
				LocationChangeMetaData data = new LocationChangeMetaData();

				// Set location of revision
				String toPath = r.getDepotPathString();

				// Set action of revision
				switch (r.getAction()) {
				case ADD:
				case IMPORT:
					data.setCreated(true);
					data.setToLoc(getLocation(toPath, change));
					break;

				case EDIT:
					data.setModified(true);
					data.setToLoc(getLocation(toPath, change));
					break;

				case DELETE:
					data.setRemoved(true);
					data.setToLoc(getLocation(toPath, change));
					break;

				case BRANCH:
				case INTEGRATE:
					data.setCopied(true);
					data.setToLoc(getLocation(toPath, change));
					data.setFromLoc(getLocation(r.getFromFile(), change));
					break;

				case MOVE_ADD:
					data.setMoved(true);
					data.setToLoc(getLocation(toPath, change));
					break;

				case MOVE_DELETE:
					data.setRemoved(true);
					data.setToLoc(getLocation(toPath, change));
					break;

				default:
					break;
				}

				// add to list
				list.add(data);
			}
		} catch (Exception e) {
			throw new RepositoryException(e);
		} finally {
			try {
				p4.disconnect();
			} catch (P4JavaException e) {
				throw new RepositoryException(e);
			}
		}
		return list;
	}

	private ILocation getLocation(String path, String revision) {
		if (path != null && !path.startsWith("/")) {
			path = "/" + path;
		}
		if (ObjectUtils.emptyString(revision)) {
			return Location.getLocationWithRepository(config.getId(), path);
		}
		return Location.getLocationWithRepositoryAndRevision(config.getId(), path, revision);
	}
}
