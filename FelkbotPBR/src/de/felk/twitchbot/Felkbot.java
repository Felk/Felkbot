package de.felk.twitchbot;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.felk.twitchbot.db.DBHelper;
import de.felk.twitchbot.db.DBUserHandler;
import de.felk.twitchbot.entities.Bet;
import de.felk.twitchbot.entities.Match;
import de.felk.twitchbot.entities.Pokemon;
import de.felk.twitchbot.entities.Team;
import de.felk.twitchbot.reaction.ReactionConditioned;
import de.felk.twitchbot.reaction.ReactionConditionedRegex;
import de.felk.twitchbot.reaction.ReactionResult;

public class Felkbot extends Twitchbot {

	public static void main(String[] args) {

		Felkbot.simulateLogMode = false;

		if (args.length < 2) {
			System.err.println("Need arguments: USER OAUTH");
			System.exit(-1);
		}

		if (args.length >= 3) {
			if (args[2].equalsIgnoreCase("simulate")) {
				Felkbot.simulateLogMode = true;
			}
		}

		System.setErr(System.out);

		Connection conn = DBHelper.newConnection();
		PokemonFetcher fetcher = new PokemonFetcher(conn);
		DBHelper.closeConnection(conn);

		new Felkbot(args[0], args[1], fetcher);
	}

	private enum Phase {
		BETTING, BATTLE;
	}

	private Phase phase = Phase.BETTING;

	private PokemonFetcher pokemonFetcher;

	private HashMap<String, Integer> balances;
	private LinkedHashMap<String, Bet> betsBlue, betsRed;
	private List<Pokemon> pokemons = new ArrayList<>();
	private Date timeStart;
	private String lastMsgSender = "", lastMsgChannel = "";
	private ChatLogger chatLogger;

	private String[] messages = new String[] { "Here's the visualized overview: ", "Here you go: ", "Visualized overview: ", "Overview for the match: ", "Here's the tactical overview: ", "The tactical overview: ", "The match overview: ", "Your overview for this match: ",
			"Here you go, match visualization: ", "Helpful overview for this match: ", "Pre-calculated overview for this match: ", "Here's the precalculated overview: " };
	private final String tppChannel = "#twitchplayspokemon";

	public static boolean simulateLogMode = true;

	public Felkbot(String name, String oauth, PokemonFetcher fetcher) {
		super(name, oauth);
		this.pokemonFetcher = fetcher;
		this.betsBlue = new LinkedHashMap<String, Bet>();
		this.betsRed = new LinkedHashMap<String, Bet>();
		this.balances = new HashMap<String, Integer>();
		this.chatLogger = new ChatLogger();

		if (simulateLogMode) {
			Connection conn = DBHelper.newConnection();
			try {
				ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM `log` WHERE name LIKE 'tpp%' OR text LIKE '!bet %' ORDER BY time, id");
				while (rs.next()) {
					// System.out.println(rs.getString("text"));
					String user = rs.getString("name");
					String text = rs.getString("text");
					Date date = rs.getTimestamp("time");
					// System.out.println("Simulating " + user + "@" + date + ": " + text);
					if (user.equalsIgnoreCase("tppinfobot")) {
						System.out.println("Simulating " + user + "@" + date + ": " + text);
					}
					onMessageTime(tppChannel, user, "", "", text, date);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			DBHelper.updatePokemonStats(conn);
			DBHelper.closeConnection(conn);
		}
	}

	private int parseBalance(String str) {
		try {
			return NumberFormat.getNumberInstance(Locale.US).parse(str).intValue();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		throw new NumberFormatException("Could not parse to int: " + str);
	}

	@Override
	protected void init() {
		addOp("felkcraft");
		setOutputEnabled(false);
		if (!simulateLogMode) {
			addDefaultChannel(tppChannel);
			addDefaultChannel(getOwnChannel());
		}
		initReactions();
	}

	private void initReactions() {
		// INIT BEHAVIOURS, COMMANDS AND STUFF

		String tppInfobot = "tppinfobot";
		String tppBankbot = "tppbankbot";

		// quit-command
		addReaction(new ReactionConditioned(ANY, getOps(), false) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time) {
				if (message.startsWith("!quit")) {
					quit("Quit by " + sender);
				}
				return null;
			}
		});

		// remote-command
		addReaction(new ReactionConditionedRegex(ANY, getOps(), false, Pattern.compile("^!remote ([^ ]*?) (.*?)$", Pattern.CASE_INSENSITIVE)) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time, Matcher matcher) {
				return new ReactionResult(matcher.group(1), matcher.group(2));
			}
		});

		// new match
		addReaction(new ReactionConditionedRegex(tppChannel, tppInfobot, false, Pattern.compile("a new match is about to begin", Pattern.CASE_INSENSITIVE)) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time, Matcher regexMatcher) {
				// This bot message cannot be trusted anymore to detect betting phase start!
				// Instead, start new round when match finishes and delete bets older than 4 minutes
				// newMatch();
				if (phase == Phase.BATTLE) {
					newMatch();
				}
				return null;
			}
		});

		// match starts
		String regexPoke = "((?:mr. )?[a-z0-9'-]*?(?: jr.)?)";
		Pattern pattern = Pattern.compile("the battle between %, %, % and %, %, % has just begun".replaceAll("%", regexPoke), Pattern.CASE_INSENSITIVE);
		addReaction(new ReactionConditionedRegex(tppChannel, tppInfobot, false, pattern) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time, Matcher matcher) {
				if (phase != Phase.BETTING) {
					System.err.println("Wrong phase: did not start battle");
					return null;
				}
				List<Pokemon> pokemons = new ArrayList<>();
				for (int i = 1; i <= matcher.groupCount(); i++) {
					pokemons.add(pokemonFetcher.getPokemonByName(fixPkmnNames(matcher.group(i))));
				}
				startMatch(pokemons, time);
				String link = messages[(int) (Math.random() * messages.length)] + "www.fe1k.de/tpp/visualize";
				for (Pokemon pkmn : pokemons) {
					link += "-" + pkmn.id;
				}
				return new ReactionResult(channel, link);
			}
		});

		// match finished
		addReaction(new ReactionConditionedRegex(tppChannel, tppInfobot, false, Pattern.compile("Team (Blue|Red) won the match", Pattern.CASE_INSENSITIVE)) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time, Matcher regexMatcher) {
				if (phase != Phase.BATTLE) {
					System.err.println("Wrong phase: did not end match");
				} else {
					endMatch(regexMatcher.group(1).equalsIgnoreCase("blue"), time);
				}
				newMatch();
				return null;
			}
		});

		// match ends with draw
		// this match is considered trash (draws usually dont happen)
		addReaction(new ReactionConditionedRegex(tppChannel, tppInfobot, false, Pattern.compile("Match resulted in a draw", Pattern.CASE_INSENSITIVE)) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderMod, String message, Date time, Matcher regexMatcher) {
				if (phase != Phase.BATTLE) {
					System.err.println("Draw was announced, but not in battle phase. Clearing anyway...");
				}
				newMatch();
				return null;
			}
		});

		// add balances
		addReaction(new ReactionConditionedRegex(tppChannel, tppBankbot, false, Pattern.compile("@([^ ]*?) your balance is ([0-9,]{1,9})", Pattern.CASE_INSENSITIVE)) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time, Matcher regexMatcher) {
				addBalance(regexMatcher.group(1), parseBalance(regexMatcher.group(2)));
				return null;
			}
		});

		// parse bettings
		addReaction(new ReactionConditionedRegex(tppChannel, ANY, false, Pattern.compile("^!bet 0*([0-9]{1,7}) (blue|red)($|\\s)", Pattern.CASE_INSENSITIVE)) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time, Matcher regexMatcher) {
				if (phase != Phase.BETTING) {
					return null;
				}
				addBet(sender, Integer.parseInt(regexMatcher.group(1)), regexMatcher.group(2).equalsIgnoreCase("blue"), time);
				return null;
			}
		});

		// repeat messages that contain felkbot in own channel
		addReaction(new ReactionConditioned(ANY, ANY, false) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time) {
				if (!sender.equalsIgnoreCase(CONSOLE) && message.toLowerCase().contains(getName().toLowerCase())) {
					lastMsgChannel = channel;
					lastMsgSender = sender;
					return new ReactionResult(CONSOLE, sender + channel + ": " + message);
				}
				return null;
			}
		});

		// answer to last message adressed to this bot
		addReaction(new ReactionConditionedRegex(ANY, getOps(), false, Pattern.compile("^!respond (.*)$", Pattern.CASE_INSENSITIVE)) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time, Matcher regexMatcher) {
				System.out.println(lastMsgChannel + "." + lastMsgSender);
				return new ReactionResult(lastMsgChannel, '@' + lastMsgSender + ' ' + regexMatcher.group(1));
			}
		});

		// enable or disable messages
		addReaction(new ReactionConditioned(ANY, getOps(), false) {
			public ReactionResult executeAccepted(String channel, String sender, boolean isSenderOp, String message, Date time) {
				if (message.startsWith("!enableOutput")) {
					setOutputEnabled(true);
				} else if (message.startsWith("!disableOutput")) {
					setOutputEnabled(false);
				}
				return null;
			}
		});

		if (!simulateLogMode) {
			// log every message
			addReaction(new ReactionConditioned(tppChannel, ANY, false) {
				public ReactionResult executeAccepted(String channel, String sender, boolean isSenderMod, String message, Date time) {
					chatLogger.log(sender, message, time);
					return null;
				}
			});
		}

	}

	private void addBalance(String username, int balance) {
		balances.put(username, balance);
		// remove invalid bets
		Bet bet = betsBlue.get(username);
		if (bet != null && bet.amount > balance) {
			betsBlue.remove(username);
		}
		bet = betsRed.get(username);
		if (bet != null && bet.amount > balance) {
			betsRed.remove(username);
		}
	}

	private void removeTooOldBets() {
		// throw out all bets older than 4 minutes,
		// because the betting phase only lasts that long and
		// this method should only get called when still in that phase

		for (int i = 0; i < 2; i++) {
			Iterator<Entry<String, Bet>> iter = (i == 0 ? betsBlue : betsRed).entrySet().iterator();
			while (iter.hasNext()) {
				long limit = new Date().getTime() - 4 * 60 * 1000; // milliseconds!
				Bet bet = iter.next().getValue();
				if (limit < bet.time.getTime()) {
					// list is LinkedHashMap (keeping insertion order), so all following entries are within the limit
					break;
				}
				iter.remove();
			}
		}
	}

	private void addBet(String username, int betAmount, boolean onBlue, Date time) {
		removeTooOldBets();

		// check if bet seems valid.
		// remove only bets, which are 100% invalid!
		// dataloss (maybe even based on inaccurate assumptions) are worse than false bets

		// if user's current balance is known, check if bet is valid
		Integer balance = balances.get(username);
		if (balance != null && balance < betAmount) {
			return; // not enough money
		}

		if ((onBlue && betsRed.containsKey(username)) || (!onBlue && betsBlue.containsKey(username))) {
			return; // already bet on opposite side
		}

		Bet prev = (onBlue ? betsBlue : betsRed).get(username);
		if (prev != null && prev.amount > betAmount) {
			return; // already bet more
		}

		Bet bet = new Bet(betAmount, time);

		(onBlue ? betsBlue : betsRed).put(username, bet);
	}

	private void startMatch(List<Pokemon> pokemons, Date date) {
		phase = Phase.BATTLE;
		this.pokemons = pokemons;
		timeStart = date;
	}

	private void newMatch() {
		phase = Phase.BETTING;
		cleanup();
		out("--- NEW MATCH ---");
	}

	private void endMatch(boolean wonBlue, Date date) {

		removeTooOldBets();

		if (pokemons.size() != 6) {
			out("Could not end match, because pokemonNames list was not 6 long: " + Arrays.toString(pokemons.toArray()));
			return;
		}

		Connection conn = DBHelper.newConnection();
		DBUserHandler userHandler = new DBUserHandler(conn);

		for (Entry<String, Integer> entry : balances.entrySet()) {
			userHandler.updateBalance(userHandler.getUserId(entry.getKey()), entry.getValue());
		}

		Team blue = new Team(pokemons.get(0), pokemons.get(1), pokemons.get(2), betsBlue);
		Team red = new Team(pokemons.get(3), pokemons.get(4), pokemons.get(5), betsRed);
		Match match = new Match(wonBlue, timeStart, date, blue, red);
		match.save(conn);
		out(match.wonStr() + " won");

		DBHelper.closeConnection(conn);

		// Start new match here, because the "a new match bla bla" message cannot be trusted!
		// Just constantly throw out bets older than 4 minutes
		cleanup();

	}

	private void cleanup() {
		betsBlue.clear();
		betsRed.clear();
		balances.clear();
		pokemons.clear();
	}

	private String fixPkmnNames(String names) {
		names = names.replace("nidoranm", "nidoran-m");
		return names.replace("nidoranf", "nidoran-f");
	}

}
