package info.thomazo.findgas.web.api;

import info.thomazo.findgas.web.config.ElasticConfig;
import info.thomazo.findgas.web.dto.Comment;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/api/comments")
public class CommentsCtrl {
	private static final DateTimeFormatter commentFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH'h'mm");

	@Autowired
	private Client esClient;

	@Autowired
	private ElasticConfig esConfig;


	@RequestMapping(method = GET)
	public List<Comment> list(@RequestParam String stationId) {
		SearchResponse res = esClient.prepareSearch(esConfig.getIndexName()).setTypes(esConfig.getCommentType())
				.setQuery(QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("station", stationId)))
				.setSize(10)
				.execute()
				.actionGet();

		List<Comment> comments = new ArrayList<>((int) res.getHits().totalHits());
		res.getHits().forEach(h -> comments.add(map(h)));

		return comments;
	}

	@RequestMapping(method = POST)
	public String add(@RequestParam String stationId, @RequestBody Comment comment) throws Exception {
		//insert comment
		esClient.prepareIndex(esConfig.getIndexName(), esConfig.getCommentType())
				.setSource(jsonBuilder().startObject()
						.field("station", stationId)
						.field("date", new Date())
						.field("comment", comment.getComment())
						.field("gas", comment.getGas())
						.field("name", comment.getName())
						.endObject())
				.get();

		//update station
		esClient.prepareUpdate(esConfig.getIndexName(), esConfig.getStationType(), stationId)
				.setDoc(jsonBuilder().startObject()
						.field("last_update", new Date())
						.field("last_name", comment.getName())
						.field("gas", comment.getGas())
						.field("comment", comment.getComment())
						.endObject())
				.get();

		return "\"ok\"";
	}



	private Comment map(SearchHit hit) {
		Map<String, Object> fields = hit.getSource();

		String dateStr = null;
		String date = getField(fields, "date");
		if (date != null) {
			dateStr = Instant.parse(date).atZone(ZoneId.systemDefault()).format(commentFormat);
		}

		return Comment.builder()
				.date(dateStr)
				.comment(getField(fields, "comment"))
				.name(getField(fields, "name"))
				.gas((List<String>) fields.get("gas"))
				.build();
	}

	private String getField(Map<String, Object> fields, String name) {
		Object value = fields.get(name);
		if (value instanceof String) return (String) value;
		return null;
	}
}
