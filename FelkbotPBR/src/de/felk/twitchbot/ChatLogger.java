package de.felk.twitchbot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;

import de.felk.twitchbot.db.DBHelper;

public class ChatLogger {

	private static final int MAX = 100;
	private int next = 0;
	private ChatMessage[] messages = new ChatMessage[MAX];
	
	private void reset() {
		next = 0;
	}
	
	private void dump() {

		//Connection conn = DBHelper.newConnection();
		try (Connection conn = DBHelper.newConnection()) {
			conn.setAutoCommit(false);
			PreparedStatement ps = conn.prepareStatement("INSERT INTO log (name, text, `time`) VALUES (?, ?, ?)");
			for (int i = 0; i < next; i++) {
				ps.setString(1, messages[i].getUser());
				ps.setString(2, messages[i].getText());
				ps.setTimestamp(3, messages[i].getTimestamp());
				ps.addBatch();
			}
			ps.executeBatch();
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		reset();
	}
	
	public void log(String user, String text, Date time) {
		messages[next] = new ChatMessage(user, text, time);
		next++;
		//System.out.printf("#%-5d %-24s %s\n", next, user, text);
		if (next >= messages.length) {
			dump();
			System.out.println("Logger dumped " + MAX + " message to Database!");
		}
	}
}
