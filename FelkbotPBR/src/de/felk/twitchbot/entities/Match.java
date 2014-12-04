package de.felk.twitchbot.entities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

import de.felk.twitchbot.db.DBHelper;

public class Match {

	private boolean wonBlue;
	private Date timeStart, timeEnd;
	private Team blue, red;
	private int id = -1;

	public Match(boolean wonBlue, Date timeStart, Date timeEnd, Team blue, Team red) {
		this.wonBlue = wonBlue;
		this.timeStart = timeStart;
		this.timeEnd = timeEnd;
		this.blue = blue;
		this.red = red;
	}

	public boolean isSaved() {
		return id >= 0;
	}

	public int getId() {
		if (!isSaved()) {
			throw new NotSavedException("Cannot retrieve ID of not-saved match!");
		}
		return id;
	}

	public void save(Connection conn) {

		// just save once to Database!
		if (isSaved()) {
			System.err.println("Match is already saved: " + toString());
			return;
		}

		try {
			if (!blue.isSaved()) {
				blue.save(conn, wonBlue, true, red.getSum() / (double) blue.getSum());
			}
			if (!red.isSaved()) {
				red.save(conn, !wonBlue, false, blue.getSum() / (double) red.getSum());
			}
			
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO matches (won_blue, time_start, time_end, team_blue, team_red) VALUES(?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, wonInt());
			stmt.setTimestamp(2, new java.sql.Timestamp(timeStart.getTime()));
			stmt.setTimestamp(3, new java.sql.Timestamp(timeEnd.getTime()));
			stmt.setInt(4, blue.getId());
			stmt.setInt(5, red.getId());
			stmt.executeUpdate();
			
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			} else {
				// no generated keys?!
				System.err.println("match was saved, but no keys were generated?");
			}
			
			DBHelper.updatePokemonStats(conn, new int[] { blue.getPkmn1().id, blue.getPkmn2().id, blue.getPkmn3().id, red.getPkmn1().id, red.getPkmn2().id, red.getPkmn3().id });
			DBHelper.updateUserStats(conn);
		} catch (SQLException e) {
			System.err.println("Could not save match: " + toString());
			e.printStackTrace();
		}

	}

	public String wonStr() {
		return wonBlue ? "blue" : "red";
	}

	public int wonInt() {
		return wonBlue ? 1 : 0;
	}

	@Override
	public String toString() {
		return "Match#" + id + "(blue:" + blue.toString() + ", red:" + red.toString() + ", start:" + timeStart + ", end:" + timeEnd + ", " + wonStr() + ")";
	}

}
