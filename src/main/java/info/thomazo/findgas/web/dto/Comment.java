package info.thomazo.findgas.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

	/** dd/MM/yyyy hh:mm */
	private String date;

	private String comment;

	/** Optional */
	private String name;

	/** empty or null if no gas at the station */
	private List<String> gas;
}
