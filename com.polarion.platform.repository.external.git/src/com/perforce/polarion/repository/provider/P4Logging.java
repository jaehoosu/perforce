package com.perforce.polarion.repository.provider;

import org.apache.log4j.Logger;

import com.perforce.p4java.server.callback.ICommandCallback;

public class P4Logging implements ICommandCallback {

	private static final Logger log = Logger.getLogger(P4Logging.class);

	private final String serverId;

	public P4Logging(String serverId) {
		this.serverId = serverId;
	}

	@Override
	public void completedServerCommand(int key, long millisecsTaken) {
	}

	@Override
	public void issuingServerCommand(int key, String commandString) {
		String tag = "P4[" + serverId + "] ";
		log.info(tag + commandString + "\n");
	}

	@Override
	public void receivedServerErrorLine(int key, String errorLine) {
	}

	@Override
	public void receivedServerInfoLine(int key, String infoLine) {
	}

	@Override
	public void receivedServerMessage(int key, int genericCode, int severityCode, String message) {
	}

}
