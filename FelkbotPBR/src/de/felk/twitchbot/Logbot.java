package de.felk.twitchbot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;

import de.felk.twitchbot.db.DBHelper;
import de.felk.twitchbot.reaction.ReactionConditioned;
import de.felk.twitchbot.reaction.ReactionResult;

public class Logbot extends Twitchbot {

	public static void main(String[] args) {
		if (args.length < 2) {
			System.err.println("Need arguments: USER OAUTH");
			System.exit(-1);
		}

		System.setErr(System.out);

		new Logbot(args[0], args[1]);
	}

	private final String tppChannel = "#twitchplayspokemon";
	private final int MAX = 100;
	private int next = 0;
	private ChatMessage[] messages = new ChatMessage[MAX];

	public Logbot(String name, String oauth) {
		super(name, oauth);
		emptyList();
	}

	private void emptyList() {
		next = 0;
	}

	private void dump() {

		Connection conn = DBHelper.newConnection();
		try {
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
		} finally {
			DBHelper.closeConnection(conn);
		}

		emptyList();
	}

	private void addMessage(String user, String text) {
		messages[next] = new ChatMessage(user, text, new Date());
		next++;
		//System.out.printf("#%-5d %-24s %s\n", next, user, text);
		if (next >= messages.length) {
			dump();
			out("Dumped " + MAX + " message to MySQL!");
		}
	}

	@Override
	protected void init() {

		addDefaultChannel(tppChannel);
		addOp("felkcraft");

		addReaction(new ReactionConditioned(tppChannel, ANY, false) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderMod, String message, Date time) {
				addMessage(sender, message);
				return null;
			}
		});

		// quit-command
		addReaction(new ReactionConditioned(ANY, getOps(), false) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time) {
				if (message.startsWith("!quit")) {
					quit("Quit by " + sender);
				}
				return null;
			}
		});

	}

}
