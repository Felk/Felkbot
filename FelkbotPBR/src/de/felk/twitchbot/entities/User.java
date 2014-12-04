package de.felk.twitchbot.entities;

public class User {

	private int id, balance, matches, wins;
	private long time;
	private String name;

	public User(int id, String name, long time, int balance, int matches, int wins) {
		this.id = id;
		this.name = name;
		this.time = time;
		this.balance = balance;
		this.matches = matches;
		this.wins = wins;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public long getTime() {
		return time;
	}

	public int getBalance() {
		return balance;
	}

	public int getMatches() {
		return matches;
	}

	public int getWins() {
		return wins;
	}
	
	@Override
	public int hashCode() {
		return id;
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof User)) return false;
		return id == ((User)o).getId();
	}

}
