package de.felk.twitchbot.db;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;

public class DBHelper {

	private static final String PREFIX = "";
	private static File propertiesFile = new File("dbconn.properties");
	private static String host, database, username, password;
	private static Connection conn;
	private static boolean closePausedConnections = true;
	private static boolean autoCommit = false;

	static {
		// "Hack" to make the Database driver work properly.
		// The internet told be to do so.
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			System.err.println("Could not load mysql jdbc driver");
			e.printStackTrace();
			System.exit(-1);
		}

		// read the connection properties
		Properties connectionProperties = new Properties();
		if (!propertiesFile.exists() || propertiesFile.isDirectory()) {
			System.err.println("Properties file does not exist: " + propertiesFile);
			System.exit(-1);
		}
		try {
			BufferedInputStream stream = new BufferedInputStream(new FileInputStream(propertiesFile));
			connectionProperties.load(stream);
			stream.close();
		} catch (IOException e) {
			System.err.println("Could not read properties file: " + propertiesFile);
			e.printStackTrace();
			System.exit(-1);
		}

		host = connectionProperties.getProperty("host");
		database = connectionProperties.getProperty("database");
		username = connectionProperties.getProperty("username");
		password = connectionProperties.getProperty("password");

		// Try to establish a connection right away, so 'conn' is not null.
		// If it is null at this very first try, abort the program. This is unacceptable!
		// prevents NPEs later on
		Connection conn = getConnection();
		if (conn == null) {
			System.err.println("Database connection test failed! Aborting...");
			System.exit(-1);
		}
		pauseConnection();

	}

	public static void commit() {
		try {
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Signals the DBHelper that the Connection is not needed for some time. The DBHelper then might close it and establish a new connection when needed.
	 */
	public static void pauseConnection() {
		commit();
		if (closePausedConnections) {
			closeConnection();
		}
	}

	/**
	 * Returns an active Connection to the database. This can be a previous connection that was put on hold, or a newly established one.
	 * 
	 * @return active SQL Connection object
	 * @throws SQLException
	 */
	public static Connection getConnection() {
		try {
			if (conn == null || conn.isClosed()) {
				conn = newConnection();
				conn.setAutoCommit(autoCommit);
			}
		} catch (SQLException e) {
			System.err.println("Could not establish a database connection!");
			e.printStackTrace();
		}
		return conn;
	}

	private static Connection newConnection() {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection("jdbc:mysql://" + host + "/" + database + "?user=" + username + "&password=" + password);
		} catch (SQLException e) {
			System.err.println("Could not establish database connection! Check MySQL-server and dbconn.properties");
			e.printStackTrace();
		}
		return conn;
	}

	/**
	 * Recalculates and recounts the database fields `matches`, `wins` and `bets_sum`
	 * 
	 * @param pkmnIds
	 *            pokemon ids to update. If empty, all pokemons will be updated
	 */
	public static void updatePokemonStats(int... pkmnIds) {

		String sqlIn = "";
		if (pkmnIds.length > 0) {
			String inStr = Arrays.toString(pkmnIds);
			sqlIn = " WHERE `nat_id` IN (" + inStr.substring(1, inStr.length() - 1) + ") ";
		}

		String query = "UPDATE `pkmns` SET " + "`matches` = (SELECT COUNT(`id`) FROM `teams` WHERE `nat_id` IN (`pkmn1`, `pkmn2`, `pkmn3`)), " + "`wins` = (SELECT COUNT(*) FROM `teams`, `matches` WHERE	"
				+ "(`team_blue` = `teams`.`id` AND `won_blue` = 1 AND `nat_id` IN (`pkmn1`, `pkmn2`, `pkmn3`)) OR " + "(`team_red` = `teams`.`id` AND `won_blue` = 0 AND `nat_id` IN (`pkmn1`, `pkmn2`, `pkmn3`)) ), "
				+ "`bets_sum` = (SELECT SUM(`sum`) FROM `teams` WHERE `nat_id` IN (`pkmn1`, `pkmn2`, `pkmn3`)) " + sqlIn;
		try {

			getConnection().createStatement().executeUpdate(query);

		} catch (SQLException e) {
			System.err.println("Could not update pokemon stats: " + query);
			e.printStackTrace();
		}

	}

	public static void updateUserStats() {
		updateUserStats(new int[] {});
	}

	public static void updateUserStats(int... userIds) {

		String sqlIn = "";
		if (userIds.length > 0) {
			String inStr = Arrays.toString(userIds);
			sqlIn = " WHERE id IN (" + inStr.substring(1, inStr.length() - 1) + ") ";
		}

		String query = "UPDATE users SET matches = (SELECT COUNT(id) FROM bets WHERE user_id = users.id), wins = (" + "SELECT COUNT(*) FROM teams, matches, bets WHERE ((teams.id = matches.team_blue AND matches.won_blue = 1) OR (teams.id = matches.team_red AND matches.won_blue = 0)) "
				+ "AND teams.id = bets.team_id AND bets.user_id = users.id) " + sqlIn;

		try {
			getConnection().createStatement().executeUpdate(query);
		} catch (SQLException e) {
			System.err.println("Could not update user stats: " + query);
			e.printStackTrace();
		}

	}

	public static void updateBalance(String username, int balance) {
		try {
			PreparedStatement statement = getConnection().prepareStatement("INSERT INTO " + PREFIX + "users (name, wins, matches, balance) VALUES (?, 0, 0, ?)" + " ON DUPLICATE KEY UPDATE balance = ?");
			statement.setString(1, username);
			statement.setInt(2, balance);
			statement.setInt(3, balance);
			statement.executeUpdate();

		} catch (SQLException e) {
			System.err.println("Could not update balance!");
			e.printStackTrace();
		}
	}

	public static void closeConnection() {
		try {
			commit();
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets whether the DBHelper should close Connections that are put on hold. Otherwise it would hold the connection, risking timeouts and stuff. Setting this option to true is useful when you are
	 * running the bot in simulation mode, causing lots of disconnects and reconnects in a short peroid of time otherwise.
	 * 
	 * @param closePausedConnections
	 */
	public static void setClosePausedConnections(boolean closePausedConnections) {
		DBHelper.closePausedConnections = closePausedConnections;
	}

	public static boolean isConnectionActive() {
		try {
			return !conn.isClosed();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static void setAutoCommit(boolean autoCommit) {
		if (isConnectionActive()) {
			try {
				conn.setAutoCommit(autoCommit);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		DBHelper.autoCommit = autoCommit;
	}
	/*
	 * public static ArrayList<Pokemon> getPokemon(Connection conn, String... names) { ArrayList<Pokemon> pkmns = new ArrayList<Pokemon>();
	 * 
	 * String query = "SELECT national_id, name, type1, type2, matches, wins, bets_sum FROM " + PREFIX + "pkmn WHERE name LIKE ? LIMIT 1"; try { PreparedStatement statement =
	 * conn.prepareStatement(query); for (String name : names) { statement.setString(1, name + '%'); ResultSet result = statement.executeQuery(); if (result.next()) { Pokemon pkmn = new
	 * Pokemon(result.getInt("national_id"), result.getString("name"), result.getInt("type1"), result.getInt("type2"), result.getInt("matches"), result.getInt("wins"), result.getInt("bets_sum")); //
	 * prevent duplicates if (!pkmns.contains(pkmn)) { pkmns.add(pkmn); } } else { Bot.out("No Match for Pokemon: " + name); } } } catch (SQLException e) {
	 * System.err.println("Could not prepare statement: " + query); e.printStackTrace(); }
	 * 
	 * try { conn.close(); } catch (SQLException e) { System.err.println("Could not close connection"); e.printStackTrace(); }
	 * 
	 * return pkmns; }
	 */

}
