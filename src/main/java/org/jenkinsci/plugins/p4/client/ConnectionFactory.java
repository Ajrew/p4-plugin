package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.PropertyDefs;
import com.perforce.p4java.impl.mapbased.rpc.RpcPropertyDefs;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;
import hudson.util.FormValidation;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Connection Factory
 * <p>
 * Provides concurrent connections to the Perforce Server
 *
 * @author pallen
 */
public class ConnectionFactory {

	private static Logger logger = Logger.getLogger(ConnectionFactory.class
			.getName());

	private static IOptionsServer currentP4;

	/**
	 * Returns existing connection
	 *
	 * @return Server connection object
	 */
	public static IOptionsServer getConnection() {
		return currentP4;
	}

	/**
	 * Creates a server connection; provides a connection to the Perforce
	 * Server, initially client is undefined.
	 *
	 * @param config Connection configuration
	 * @return Server connection object
	 * @throws Exception push up stack
	 */
	public static IOptionsServer getConnection(ConnectionConfig config)
			throws Exception {

		IOptionsServer iserver = getRawConnection(config);

		// Add trust for SSL connections
		if (config.isSsl()) {
			String serverTrust = iserver.getTrust();
			if (!serverTrust.equalsIgnoreCase(config.getTrust())) {
				logger.warning("Trust mismatch! Server fingerprint: "
						+ serverTrust);
			} else {
				iserver.addTrust(config.getTrust());
			}
		}

		// Connect and update current P4 connection
		iserver.connect();
		currentP4 = iserver;
		return iserver;
	}

	public static FormValidation testConnection(ConnectionConfig config) {

		// Test for SSL connections
		try {
			IOptionsServer iserver = getRawConnection(config);
			if (config.isSsl()) {
				String serverTrust = iserver.getTrust();
				if (!serverTrust.equalsIgnoreCase(config.getTrust())) {
					return FormValidation
							.error("Trust mismatch! Server fingerprint: "
									+ serverTrust);
				} else {
					iserver.addTrust(config.getTrust());
				}
			}
		} catch (Exception e) {
			StringBuffer sb = new StringBuffer();
			sb.append("Unable to connect to: ");
			sb.append(config.getServerUri());
			sb.append("\n");
			sb.append(e.getMessage());
			return FormValidation.error(sb.toString());
		}

		return FormValidation.ok();
	}

	private static IOptionsServer getRawConnection(ConnectionConfig config)
			throws Exception {
		Properties props = System.getProperties();

		// Identify ourselves in server log files.
		Identifier id = new Identifier();
		props.put(PropertyDefs.PROG_NAME_KEY, id.getProduct());
		props.put(PropertyDefs.PROG_VERSION_KEY, id.getVersion());

		// Allow p4 admin commands.
		props.put(RpcPropertyDefs.RPC_RELAX_CMD_NAME_CHECKS_NICK, "true");

		// disable timeout for slow servers / large db lock times
		String timeout = String.valueOf(config.getTimeout());
		props.put(RpcPropertyDefs.RPC_SOCKET_SO_TIMEOUT_NICK, timeout);

		// Get a server connection
		String serverUri = config.getServerUri();
		IOptionsServer iserver;
		iserver = ServerFactory.getOptionsServer(serverUri, props);
		return iserver;
	}
}
