package de.felk.twitchbot.entities;

public class Pokemon {

	public int id, type1, type2, matches, wins, betsSum;
	public String name;
	public float quote;

	public Pokemon(int id, String name, int type1, int type2, int matches, int wins, int betsSum) {
		this.id = id;
		this.name = name;
		this.type1 = type1;
		this.type2 = type2;
		this.matches = matches;
		this.wins = wins;
		this.betsSum = betsSum;
		this.quote = (matches > 0) ? ((float) wins / matches) : 0f;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Pokemon)) {
			return false;
		}
		return ((Pokemon) o).id == this.id;
	}

	@Override
	public String toString() {
		return name+"#"+id;
	}

	public int compareName(String otherName) {
		otherName = otherName.toLowerCase();
		if (name.toLowerCase().startsWith(otherName)) {
			return 0;
		}
		return name.compareToIgnoreCase(otherName);
	}

}
