package info.thomazo.findgas.web.dto;

import lombok.Data;

@Data
public class Patch {

	private String station;
	private String stationName;

	private String location;
	private String address;
	private String cp;
	private String city;

	private String comment;
	private String name;

}
