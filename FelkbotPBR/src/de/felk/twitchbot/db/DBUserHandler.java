package de.felk.twitchbot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import de.felk.twitchbot.entities.User;

/**
 * This class is used to retrieve user data with prepared statements (=> good performance).
 */
public class DBUserHandler {

	private PreparedStatement stmt_getUserId, stmt_getUser, stmt_newUser, stmt_updateBalance, stmt_updateBalanceBet;

	public DBUserHandler(Connection conn) {
		try {
			stmt_getUserId = conn.prepareStatement("SELECT id FROM users WHERE name = ? LIMIT 1");
			stmt_getUser = conn.prepareStatement("SELECT id, name, time, balance, matches, wins FROM users WHERE id = ? LIMIT 1");
			stmt_newUser = conn.prepareStatement("INSERT INTO users (name, balance, matches, wins) VALUES (?, 0, 0, 0)", Statement.RETURN_GENERATED_KEYS);
			stmt_updateBalance = conn.prepareStatement("UPDATE users SET balance = ? WHERE id = ? LIMIT 1");
			stmt_updateBalanceBet = conn.prepareStatement("UPDATE bets SET calculated = 0, balance = ? WHERE user_id = ? ORDER BY id DESC LIMIT 1");
		} catch (SQLException e) {
			System.err.println("Could not prepare statements for user handler.");
			e.printStackTrace();
		}
	}

	public void updateBalance(int userId, int balance) {
		try {
			stmt_updateBalance.setInt(1, balance);
			stmt_updateBalance.setInt(2, userId);
			stmt_updateBalance.executeUpdate();
			stmt_updateBalanceBet.setInt(1, balance);
			stmt_updateBalanceBet.setInt(2, userId);
			stmt_updateBalanceBet.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * returns the ID of a user. If the user doesn't exist, it will be created.
	 * 
	 * @param name
	 *            Name of user
	 * @return the user's id. May be freshly created.
	 * @throws SQLException
	 */
	public int getUserId(String name, boolean createIfNotExists) {
		try {
			stmt_getUserId.setString(1, name);
			ResultSet rs = stmt_getUserId.executeQuery();
			if (rs.next()) {
				// Found user. use his ID
				return rs.getInt(1);
			} else if (createIfNotExists) {
				// Found no user. Insert new one and retrieve ID
				stmt_newUser.setString(1, name);
				stmt_newUser.executeUpdate();
				ResultSet keyRs = stmt_newUser.getGeneratedKeys();
				if (keyRs.next()) {
					return keyRs.getInt(1);
				} else {
					// no generated keys?!
					System.err.println("Inserted new User '" + name + "', but no key generated?! Therefore bets with user_id -1 were propably added.");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return -1;
	}

	public int getUserId(String name) {
		return getUserId(name, true);
	}

	public User getUser(String name, boolean createIfNotExists) {
		return getUser(getUserId(name));
	}

	public User getUser(String name) {
		return getUser(name, true);
	}

	public User getUser(int id) {
		try {
			stmt_getUser.setInt(1, id);
			ResultSet rs = stmt_getUser.executeQuery();
			if (rs.next()) {
				return new User(rs.getInt("id"), rs.getString("name"), rs.getLong("time"), rs.getInt("balance"), rs.getInt("matches"), rs.getInt("wins"));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		// user not found!
		return null;
	}

}
