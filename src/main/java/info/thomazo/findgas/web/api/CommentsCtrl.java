package info.thomazo.findgas.web.api;

import info.thomazo.findgas.web.config.ElasticConfig;
import info.thomazo.findgas.web.config.IpMDCFilter;
import info.thomazo.findgas.web.dto.Comment;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/api/comments")
public class CommentsCtrl {
	private static final Logger logger = LoggerFactory.getLogger(CommentsCtrl.class);

	private static final DateTimeFormatter commentFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH'h'mm");

	private static final PolicyFactory stripHtml = new HtmlPolicyBuilder().toFactory();

	@Autowired
	private Client esClient;

	@Autowired
	private ElasticConfig esConfig;

	@Autowired
	private IpMDCFilter ipMDCFilter;

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
	public String add(@RequestParam String stationId, @RequestBody Comment comment, ServletRequest request) throws Exception {
		sanitizeComment(comment);
		if (comment.getComment() != null && comment.getComment().length() > 130) {
			comment.setComment(comment.getComment().substring(0, 130));
		}

		//insert comment
		IndexResponse idxRes = esClient.prepareIndex(esConfig.getIndexName(), esConfig.getCommentType())
				.setSource(jsonBuilder().startObject()
						.field("station", stationId)
						.field("date", new Date())
						.field("comment", comment.getComment())
						.field("gas", comment.getGas())
						.field("name", comment.getName())
						.field("ip", ipMDCFilter.getIp(request))
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

		logger.info("[ADDED_COMMENT] [id:{}] [stationId:{}] [gas={}] [name={}] [comment={}]",
				idxRes.getId(), stationId, String.join(",", comment.getGas()), comment.getName(), comment.getComment());

		return "\"ok\"";
	}

	private void sanitizeComment(Comment comment) {
		comment.setDate(sanitize(comment.getDate()));
		comment.setComment(sanitize(comment.getComment()));
		comment.setName(sanitize(comment.getName()));

		List<String> gasList = comment.getGas();
		if (gasList != null && !gasList.isEmpty()) {
			comment.setGas(gasList.stream()
					.map(this::sanitize)
					.filter(g -> g != null)
					.collect(Collectors.toList())
			);
		}
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

	private String sanitize(String input) {
		if (input == null) return null;
		String res = stripHtml.sanitize(input);
		if (res.isEmpty()) return null;
		return res;
	}
}
