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

	}

	public static Connection newConnection() {
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
	 * @param conn
	 *            the database connection
	 * @param pkmnIds
	 *            pokemon ids to update. If empty, all pokemons will be updated
	 */
	public static void updatePokemonStats(Connection conn, int... pkmnIds) {

		String sqlIn = "";
		if (pkmnIds.length > 0) {
			String inStr = Arrays.toString(pkmnIds);
			sqlIn = " WHERE `nat_id` IN (" + inStr.substring(1, inStr.length() - 1) + ") ";
		}

		String query = "UPDATE `pkmns` SET " + "`matches` = (SELECT COUNT(`id`) FROM `teams` WHERE `nat_id` IN (`pkmn1`, `pkmn2`, `pkmn3`)), " + "`wins` = (SELECT COUNT(*) FROM `teams`, `matches` WHERE	"
				+ "(`team_blue` = `teams`.`id` AND `won_blue` = 1 AND `nat_id` IN (`pkmn1`, `pkmn2`, `pkmn3`)) OR " + "(`team_red` = `teams`.`id` AND `won_blue` = 0 AND `nat_id` IN (`pkmn1`, `pkmn2`, `pkmn3`)) ), "
				+ "`bets_sum` = (SELECT SUM(`sum`) FROM `teams` WHERE `nat_id` IN (`pkmn1`, `pkmn2`, `pkmn3`)) " + sqlIn;
		try {

			conn.createStatement().executeUpdate(query);

		} catch (SQLException e) {
			System.err.println("Could not update pokemon stats: " + query);
			e.printStackTrace();
		}

	}

	public static void updateUserStats(Connection conn) {
		updateUserStats(conn, new int[]{});
	}
	
	public static void updateUserStats(Connection conn, int... userIds) {

		String sqlIn = "";
		if (userIds.length > 0) {
			String inStr = Arrays.toString(userIds);
			sqlIn = " WHERE id IN (" + inStr.substring(1, inStr.length() - 1) + ") ";
		}

		String query = "UPDATE users SET matches = (SELECT COUNT(id) FROM bets WHERE user_id = users.id), wins = (" + "SELECT COUNT(*) FROM teams, matches, bets WHERE ((teams.id = matches.team_blue AND matches.won_blue = 1) OR (teams.id = matches.team_red AND matches.won_blue = 0)) "
				+ "AND teams.id = bets.team_id AND bets.user_id = users.id) " + sqlIn;

		try {
			conn.createStatement().executeUpdate(query);
		} catch (SQLException e) {
			System.err.println("Could not update user stats: " + query);
			e.printStackTrace();
		}

	}

	public static void updateBalance(Connection conn, String username, int balance) {
		try {
			PreparedStatement statement = conn.prepareStatement("INSERT INTO " + PREFIX + "users (name, wins, matches, balance) VALUES (?, 0, 0, ?)" + " ON DUPLICATE KEY UPDATE balance = ?");
			statement.setString(1, username);
			statement.setInt(2, balance);
			statement.setInt(3, balance);
			statement.executeUpdate();

		} catch (SQLException e) {
			System.err.println("Could not update balance!");
			e.printStackTrace();
		}
	}

	public static void closeConnection(Connection conn) {
		try {
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
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
