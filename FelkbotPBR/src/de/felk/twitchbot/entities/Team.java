package de.felk.twitchbot.entities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map.Entry;

import de.felk.twitchbot.db.DBUserHandler;

public class Team {

	private Pokemon pkmn1, pkmn2, pkmn3;
	HashMap<String, Integer> bets = new HashMap<String, Integer>();
	private int num, sum, id = -1;

	public Team(Pokemon pkmn1, Pokemon pkmn2, Pokemon pkmn3, HashMap<String, Integer> bets) {
		this.pkmn1 = pkmn1;
		this.pkmn2 = pkmn2;
		this.pkmn3 = pkmn3;
		this.bets = bets;
		this.num = bets.size();
		this.sum = 0;
		for (int n : bets.values()) {
			sum += n;
		}
	}

	public int getSum() {
		return sum;
	}

	public Pokemon getPkmn1() {
		return pkmn1;
	}

	public Pokemon getPkmn2() {
		return pkmn2;
	}

	public Pokemon getPkmn3() {
		return pkmn3;
	}

	public boolean isSaved() {
		return id >= 0;
	}

	public int getId() {
		if (!isSaved())
			throw new NotSavedException("Cannot retrieve ID of not-saved team!");
		return id;
	}

	public void save(Connection conn, boolean won, boolean blue, double odds) {

		// just save once to Database!
		if (isSaved()) {
			System.err.println("Team is already saved: " + toString());
			return;
		}

		// Insert match
		try {
			Statement stmt = conn.createStatement();
			stmt.executeUpdate("INSERT INTO `teams` (num, sum, pkmn1, pkmn2, pkmn3) VALUES (" + num + "," + sum + "," + pkmn1.id + "," + pkmn2.id + "," + pkmn3.id + ")", Statement.RETURN_GENERATED_KEYS);
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			} else {
				// no generated keys?!
				System.err.println("team was saved, but no keys were generated?");
			}
		} catch (SQLException e) {
			System.err.println("Could not save team: " + toString());
			e.printStackTrace();
		}

		// Insert bets
		try {
			DBUserHandler userFetcher = new DBUserHandler(conn);
			PreparedStatement stmt_addBet = conn.prepareStatement("INSERT INTO bets (user_id, team_id, bet, blue, balance, calculated) " + "VALUES (?, ?, ?, " + (blue ? 1 : 0) + ", ?, 1)");
			PreparedStatement stmt_updateBalance = conn.prepareStatement("UPDATE users SET balance = ? WHERE id = ? LIMIT 1");

			for (Entry<String, Integer> entry : bets.entrySet()) {

				String username = entry.getKey();
				int bet = entry.getValue();

				// retrieve user
				User user = userFetcher.getUser(username);

				int newBalance = user.getBalance();
				if (won) {
					newBalance += bet * odds;
				} else {
					newBalance -= bet;
				}

				// insert bet
				stmt_addBet.setInt(1, user.getId());
				stmt_addBet.setInt(2, getId());
				stmt_addBet.setInt(3, bet);
				stmt_addBet.setDouble(4, newBalance);
				stmt_addBet.executeUpdate();

				// update user's balance
				stmt_updateBalance.setInt(1, newBalance);
				stmt_updateBalance.setInt(2, user.getId());
				stmt_updateBalance.executeUpdate();

			}

		} catch (SQLException e1) {
			System.err.println("Error inserting bets for match: " + toString());
			e1.printStackTrace();
		}

	}

	@Override
	public String toString() {
		return "Team#" + id + "(" + pkmn1.name + ", " + pkmn2.name + ", " + pkmn3.name + ", " + num + ", " + sum + "$)";
	}

}
