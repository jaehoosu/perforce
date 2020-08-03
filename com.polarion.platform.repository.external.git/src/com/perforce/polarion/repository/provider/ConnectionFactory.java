package com.perforce.polarion.repository.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.perforce.p4java.PropertyDefs;
import com.perforce.p4java.client.IClient;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.mapbased.rpc.RpcPropertyDefs;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;
import com.perforce.p4java.server.callback.ICommandCallback;
import com.polarion.platform.core.IPlatform;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.repository.external.IExternalRepositoryProvider.IExternalRepository;
import com.polarion.platform.repository.external.IExternalRepositoryProviderRegistry;
import com.polarion.platform.service.repository.RepositoryException;
import com.polarion.subterra.base.data.identification.IContextId;

public class ConnectionFactory {

	private static final Logger log = Logger.getLogger(ConnectionFactory.class);

	public static IOptionsServer getConnection(P4RepositoryConfiguration config) {
		return getConnection(config, true);
	}

	public static IOptionsServer getConnection(P4RepositoryConfiguration config, boolean log) {
		IOptionsServer iserver = null;

		Properties props = System.getProperties();
		props.put(PropertyDefs.PROG_NAME_KEY, "P4-Polarion");
		props.put(PropertyDefs.PROG_VERSION_KEY, "Undefined");
		props.put(RpcPropertyDefs.RPC_SOCKET_SO_TIMEOUT_NICK, "0");

		// build the p4java URI
		String serverUri;
		String p4port = config.getP4Port();
		if (p4port.startsWith("ssl:")) {
			p4port = p4port.substring("ssl:".length());
			serverUri = "p4javassl://" + p4port;
		} else {
			serverUri = "p4java://" + p4port;
		}

		try {
			// open a Perforce connection
			iserver = ServerFactory.getOptionsServer(serverUri, props);
			iserver.connect();

			// set unicode if required
			String charset = config.getP4Charset();
			if (charset != null && charset.length() > 0) {
				iserver.setCharsetName(charset);
			}

			// set user name
			iserver.setUserName(config.getP4User());

			// authenticate with Perforce
			login(iserver, config);

			// Set active client workspace
			IClient iclient = iserver.getClient(config.getP4Client());
			iserver.setCurrentClient(iclient);

			// Register logging callback
			if (log) {
				ICommandCallback logging = new P4Logging(p4port);
				iserver.registerCallback(logging);
			}
		} catch (Exception e) {
			throw new RepositoryException(e);
		}

		return iserver;
	}

	public static List<P4RepositoryConfiguration> getConnections(IContextId context) {

		List<P4RepositoryConfiguration> configs = new ArrayList<>();

		// Given the context find all the Perforce connections
		IPlatform platform = PlatformContext.getPlatform();
		IExternalRepositoryProviderRegistry service;
		service = platform.lookupService(IExternalRepositoryProviderRegistry.class);
		List<IExternalRepository> repositories = service.getRepositories(context);

		for (IExternalRepository repository : repositories) {
			if (repository instanceof P4ExternalRepository) {
				P4ExternalRepository repo = (P4ExternalRepository) repository;
				configs.add(repo.getConfig());
				log.info(repo.getConfig().toString());
			}
		}

		return configs;
	}

	private static void login(IOptionsServer iserver, P4RepositoryConfiguration config) throws P4JavaException {

		String status = iserver.getLoginStatus();
		if (status.contains("not necessary")) {
			return;
		}

		String pass = config.getP4Password();
		if (pass != null && pass.length() > 0) {

			if (config.isP4Ticket()) {
				// login if ticket is provided
				iserver.setAuthTicket(pass);
			} else {
				// login if password is provided
				iserver.login(pass);
			}
		}
	}
}
