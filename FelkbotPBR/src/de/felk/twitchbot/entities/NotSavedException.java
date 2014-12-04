package de.felk.twitchbot.entities;

public class NotSavedException extends RuntimeException {

	public NotSavedException(String string) {
		super(string);
	}

	private static final long serialVersionUID = 1015727161106912498L;

}
