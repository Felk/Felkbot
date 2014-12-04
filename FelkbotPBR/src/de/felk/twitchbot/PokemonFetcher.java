package de.felk.twitchbot;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import de.felk.twitchbot.entities.Pokemon;

public class PokemonFetcher {

	private List<Pokemon> pokemonList = new ArrayList<>();

	public PokemonFetcher(Connection conn) {

		loadList(conn);

	}
	
	public void reload(Connection conn) {
		loadList(conn);
	}
	
	private void loadList(Connection conn) {
		try {
			ResultSet rs = conn.createStatement().executeQuery("SELECT nat_id, name, type1, type2, matches, wins, bets_sum FROM pkmns ORDER BY name ASC");
			pokemonList.clear();
			while (rs.next()) {
				pokemonList.add(new Pokemon(rs.getInt("nat_id"), rs.getString("name"), rs.getInt("type1"), rs.getInt("type2"), rs.getInt("matches"), rs.getInt("wins"), rs.getInt("bets_sum")));
			}
		} catch (SQLException e) {
			System.err.println("Could not fetch Pokemon data.");
			e.printStackTrace();
		}
	}

	public Pokemon getPokemonByName(String name) {
		int min = 0, max = pokemonList.size() - 1, cmp, pos;

		// list is alphabetically sorted, do a binary search.
		while (min <= max) {
			pos = (min + max) / 2;
			// custom comparison function returns already 0 if the name only starts with the given string
			cmp = pokemonList.get(pos).compareName(name);
			if (cmp < 0) {
				min = pos + 1;
			} else if (cmp > 0) {
				max = pos - 1;
			} else {
				// go back to first fitting one
				while (pos > 0 && pokemonList.get(pos - 1).compareName(name) == 0) {
					pos--;
				}
				return pokemonList.get(pos);
			}
		}

		return null;
	}
	
	@Override
	public String toString() {
		return pokemonList.toString();
	}
	
}
