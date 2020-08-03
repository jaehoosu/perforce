package com.perforce.polarion.repository.provider;

import javax.validation.constraints.NotNull;

import com.polarion.platform.repository.external.AbstractExternalRepositoryConfiguration;
import com.polarion.platform.repository.external.ExternalRepositoryCredentials;
import com.polarion.platform.repository.external.ExternalRepositoryCredentials.FieldType;

public class P4RepositoryConfiguration extends
		AbstractExternalRepositoryConfiguration {

	/**
	 * P4PORT defines the server and port from the Perforce server. E.g.
	 * perforce:1666 or ssl:perforce:1666 for secure connections.
	 */
	@NotNull
	private String p4Port;

	public String getP4Port() {
		return p4Port;
	}

	public void setP4Port(String p4Port) {
		this.p4Port = p4Port;
	}

	/**
	 * P4CLIENT defined the client workspace used to search changes and files
	 * with in Perforce.
	 */
	@NotNull
	private String p4Client;

	public String getP4Client() {
		return p4Client;
	}

	public void setP4Client(String p4Client) {
		this.p4Client = p4Client;
	}

	/**
	 * P4CHARSET defines the Charset to use with a Unicode enabled Perforce
	 * server; leave empty for non-unicode servers
	 */
	private String p4Charset;

	public String getP4Charset() {
		return p4Charset;
	}

	public void setP4Charset(String p4Charset) {
		this.p4Charset = p4Charset;
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("P4PORT:   " + p4Port + "\n");
		sb.append("P4USER:   " + p4User + "\n");
		sb.append("P4CLIENT: " + p4Client + "\n");
		return sb.toString();
	}

	/**
	 * P4USER defines the Perforce user account to authenticate the session.
	 */
	@NotNull
	@ExternalRepositoryCredentials(fieldType = FieldType.USERNAME, credentialId = "credentials")
	private String p4User;

	public String getP4User() {
		return p4User;
	}

	public void setP4User(String p4User) {
		this.p4User = p4User;
	}

	/**
	 * P4PASSWD the clear-text password to authenticate the Perforce user. (use
	 * p4ticket for secure environments)
	 */
	@ExternalRepositoryCredentials(fieldType = FieldType.PASSWORD, credentialId = "credentials")
	private String p4Password;

	public String getP4Password() {
		return p4Password;
	}

	public void setP4Password(String p4Password) {
		this.p4Password = p4Password;
	}

	/**
	 * If 'true' the password field stores the P4TICKET session ticket to
	 * authenticate the Perforce user. Recommended that a long lived ticket is
	 * used for 'non human' users.
	 */
	private boolean p4Ticket;

	public boolean isP4Ticket() {
		return p4Ticket;
	}

	public void setP4Ticket(boolean p4Ticket) {
		this.p4Ticket = p4Ticket;
	}

	/**
	 * browserBaseURL, the Swarm URL for browsing the Perforce code. E.g.
	 * https://swarm.workshop.perforce.com
	 */
	private String browserBaseURL;

	public String getBrowserBaseURL() {
		return browserBaseURL;
	}

	public void setBrowserBaseURL(String browserBaseURL) {
		this.browserBaseURL = browserBaseURL;
	}

	/**
	 * External ID for the Perforce provider.
	 */
	@Override
	public String getProviderId() {
		return P4ExternalRepositoryProvider.ID;
	}

}
