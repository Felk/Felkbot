package de.felk.twitchbot;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;

public class ChatMessage {

	private String user, text;
	private Date time;
	
	public ChatMessage(String user, String text, Date time) {
		this.user = user;
		this.text = text;
		this.time = time;
	}
	
	public Time getSqlTime() {
		return new Time(time.getTime());
	}
	
	public Timestamp getTimestamp() {
		return new Timestamp(time.getTime());
	}
	
	public String getUser() {
		return user;
	}
	
	public String getText() {
		return text;
	}
	
}
