package de.felk.twitchbot.entities;

import java.util.Date;

public class Bet {

	public int amount;
	public Date time;

	public Bet(int amount, Date time) {
		this.amount = amount;
		this.time = time;
	}

	@Override
	public String toString() {
		return "Bet(" + amount + " @ " + time + ")";
	}

}
