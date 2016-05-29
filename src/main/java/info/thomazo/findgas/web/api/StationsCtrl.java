package info.thomazo.findgas.web.api;

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;
import info.thomazo.findgas.web.config.ElasticConfig;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.geogrid.GeoHashGrid;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.geojson.Point;
import org.ocpsoft.prettytime.PrettyTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/api/stations")
public class StationsCtrl {

	@Autowired
	private Client esClient;

	@Autowired
	private ElasticConfig esConfig;

	@RequestMapping(path = "/count", method = GET)
	public Map<String, Long> count() {
		Map<String, Long> res = new HashMap<>();

		String startDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		SearchResponse fromDay = searchFromLastUpdate(startDay);
		res.put("fromDay", fromDay.getHits().totalHits());

		SearchResponse lastHour = searchFromLastUpdate("now-1h");
		res.put("lastHour", lastHour.getHits().totalHits());

		return res;
	}

	@RequestMapping(method = GET)
	public List<GeoJsonObject> list(@RequestParam double n, @RequestParam double s, @RequestParam double w, @RequestParam double e, @RequestParam int z) {
		//limit bounds
		if (n < -90) n = -90; if (n > 90) n = 90;
		if (s < -90) s = -90; if (s > 90) s = 90;
		if (w < -180) w = -180; if (w > 180) s = 180;
		if (e < -180) e = -180; if (e > 180) e = 180;

		if (z < 10) {
			return listAggregate(n, s, w, e, z);
		}

		SearchResponse res = esClient.prepareSearch(esConfig.getIndexName()).setTypes(esConfig.getStationType())
				.setQuery(QueryBuilders.boolQuery().filter(QueryBuilders.geoBoundingBoxQuery("location").topLeft(n, w).bottomRight(s, e)))
				.setSize(100)
				.execute()
				.actionGet();

		if (res.getHits().getTotalHits() > 100) {
			return listAggregate(n, s, w, e, z);
		}

		List<GeoJsonObject> resStations = new ArrayList<>((int) res.getHits().totalHits());
		PrettyTime pt = new PrettyTime(Locale.FRANCE);

		res.getHits().forEach(h -> {
			Feature f = new Feature();
			f.setProperty("id", h.getId());
			setProperty(h, f, "name");
			setProperty(h, f, "address");
			setProperty(h, f, "cp");
			setProperty(h, f, "city");
			setProperty(h, f, "last_name");
			setProperty(h, f, "comment");
			setProperty(h, f, "gas");
			f.setGeometry(getPoint(h.getSource().get("location").toString()));

			Object lastUpdate = h.getSource().get("last_update");
			if (lastUpdate instanceof String) { //null is not an instance
				f.setProperty("last_update", lastUpdate);
				f.setProperty("last_ago", pt.format(Date.from(Instant.parse((String) lastUpdate))));
			}

			resStations.add(f);
		});

		return resStations;
	}

	@RequestMapping(value = "/{id}", method = GET)
	private Map<String, Object> get(@PathVariable String id) {
		GetResponse res = esClient.prepareGet(esConfig.getIndexName(), esConfig.getStationType(), id)
				.get();

		Map<String, Object> station = new HashMap<>();

		Map<String, Object> source = res.getSource();
		station.put("name", source.get("name"));
		station.put("location", source.get("location"));
		station.put("address", source.get("address"));
		station.put("cp", source.get("cp"));
		station.put("city", source.get("city"));

		return station;
	}


	private List<GeoJsonObject> listAggregate(double n, double s, double w, double e, int z) {
		int zoom = z - 4;
		if (zoom < 5) zoom = 5;

		SearchResponse res = esClient.prepareSearch(esConfig.getIndexName()).setTypes(esConfig.getStationType())
				.setQuery(QueryBuilders.boolQuery().filter(QueryBuilders.geoBoundingBoxQuery("location").topLeft(n, w).bottomRight(s, e)))
				.addAggregation(AggregationBuilders.geohashGrid("stations").field("location").precision(zoom))
				.setSize(100)
				.execute()
				.actionGet();


		GeoHashGrid stations = res.getAggregations().get("stations");
		List<GeoHashGrid.Bucket> buckets = stations.getBuckets();
		List<GeoJsonObject> resStations = new ArrayList<>(buckets.size());

		for (GeoHashGrid.Bucket bucket : buckets) {
			Feature f = new Feature();

			LatLong latLong = GeoHash.decodeHash(bucket.getKeyAsString());
			f.setGeometry(new Point(latLong.getLon(), latLong.getLat()));

			f.setProperty("nb", bucket.getDocCount());
			f.setProperty("radius", getRadiusFromGeohash(bucket.getKeyAsString()));

			resStations.add(f);
		}

		return resStations;
	}

	private double getRadiusFromGeohash(String geohash) {
		switch (geohash.length()) {
			case 1: return 2500 / 2;
			case 2: return 630 / 2;
			case 3: return 78 / 2;
			case 4: return 20 / 2;
			case 5: return 2.4 / 2;
			case 6: return 0.61 / 2;
			case 7: return 0.076 / 2;
			case 8: return 0.019 / 2;
		}
		return 0;
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

	private SearchResponse searchFromLastUpdate(String startDay) {
		return esClient.prepareSearch(esConfig.getIndexName()).setTypes(esConfig.getStationType())
				.setQuery(QueryBuilders.constantScoreQuery(QueryBuilders.rangeQuery("last_update").gte(startDay)))
				.setSize(0)
				.execute().actionGet();
	}
}
