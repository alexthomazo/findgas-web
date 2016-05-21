package info.thomazo.findgas.web.api;

import info.thomazo.findgas.web.config.ElasticConfig;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.geojson.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/api/stations")
public class StationsCtrl {

	@Autowired
	private Client esClient;

	@Autowired
	private ElasticConfig esConfig;

	@RequestMapping(method = GET)
	public List<GeoJsonObject> list(@RequestParam double n, @RequestParam double s, @RequestParam double w, @RequestParam double e, @RequestParam int z) {
		if (z < 12) {
			//aggregate search
		} else {
			//display all
		}

		SearchResponse res = esClient.prepareSearch(esConfig.getIndexName()).setTypes(esConfig.getIndexType())
				.setQuery(QueryBuilders.boolQuery().filter(QueryBuilders.geoBoundingBoxQuery("location").topLeft(n, w).bottomRight(s, e)))
				.setSize(1000)
				.execute()
				.actionGet();

		List<GeoJsonObject> resStations = new ArrayList<>((int) res.getHits().totalHits());

		res.getHits().forEach(h -> {
			Feature f = new Feature();
			setProperty(h, f, "address");
			setProperty(h, f, "cp");
			setProperty(h, f, "city");
			f.setGeometry(getPoint(h.getSource().get("location").toString()));

			resStations.add(f);
		});

		return resStations;
	}

	private GeoJsonObject getPoint(String location) {
		int idx = location.indexOf(',');
		if (idx == -1) return null;

		double lat = Double.parseDouble(location.substring(0, idx));
		double lng = Double.parseDouble(location.substring(idx + 2));

		return new Point(lng, lat);
	}

	private void setProperty(SearchHit hit, Feature feature, String fieldName) {
		Object value = hit.getSource().get(fieldName);
		if (value != null) {
			feature.setProperty(fieldName, value);
		}
	}
}
